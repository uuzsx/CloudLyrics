const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const code = fs.readFileSync('src/main/resources/cloudlyrics/reader.js', 'utf8');

function harness() {
  let now = 1000, timerId = 0;
  const timers = new Map(), requests = [];
  const context = vm.createContext({
    Date: { now: () => now },
    setTimeout: (fn, delay) => { const id = ++timerId; timers.set(id, { fn, at: now + delay }); return id; },
    clearTimeout: id => timers.delete(id),
    request: args => new Promise((resolve, reject) => requests.push({ args, resolve, reject }))
  });
  vm.runInContext(`
    var window = globalThis;
    var frame = {current:12.345,playId:'101_1'};
    var state = {playing:{onlineResourceId:'101',resourceTrackId:'101',playId:'101_1',resourceName:'Test',resourceArtists:[{name:'Artist'}],playingState:2},
      setting:{showLyric:false},'page:vinylPage':{pageState:'closed'}};
    var app = {getStore:()=>state};
    var formatter = function(data) { return {currentUsedLyric:"none",lyricLines:[],...data.formatted}; };
    var req = function(id) { if(id==='359') { req.c[id]={exports:{a:formatter}}; return req.c[id].exports; } throw Error('Unexpected module'); };
    req.c={11:{exports:{a:app}},128:{exports:{b:()=>frame}},15:{exports:{wg:request}}};
    req.m={
      128:function(){return 'subscribePlayStatus "playprogress"';},
      15:function(){},
      359:function(){return 'headArtistInfos rawYrc currentUsedLyric:"none"';}
    };
    req.m[15].toString=()=> 'n.d(t,"wg",(function(){return xo}));xo=Object(r.a)({url:"/api/song/lyric/v1",format:o.q})';
    window.webpackJsonp={push:entry=>entry[1].cloudlyrics_reader_v1({}, {}, req)};
  `, context);
  const read = () => JSON.parse(JSON.stringify(vm.runInContext(code, context)));
  const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
  const resolve = async (index = requests.length - 1, lines = [{ time:10, lyric:'Synthetic line' }], extra = {}) => {
    requests[index].resolve({ code:200, formatted:{ lyricLines:lines, ...extra } }); await flush();
  };
  const select = id => {
    context.state.playing.onlineResourceId = String(id);
    context.state.playing.playId = String(id) + '_1';
    context.frame.playId = context.state.playing.playId;
  };
  const advance = async ms => {
    now += ms;
    for (const [id, t] of timers) if (t.at <= now) { timers.delete(id); t.fn(); }
    await flush();
  };
  return { context, requests, read, flush, resolve, select, advance };
}

let checks = 0;
async function test(name, run) {
  try { await run(harness()); checks++; }
  catch (error) { error.message = name + ': ' + error.message; throw error; }
}

(async () => {
  await test('cold lyric load with both lyric windows closed and no UI lyric store', async h => {
    assert.equal(h.read().status, 'loading'); await h.flush();
    assert.equal(h.requests.length, 1);
    assert.deepEqual(JSON.parse(JSON.stringify(h.requests[0].args)), {id:'101',lv:-1,tv:-1,rv:-1,yv:-1});
    assert.ok(h.context.req.c[359], 'formatter must be lazy-loaded');
    await h.resolve(); assert.equal(h.read().lyrics[0].text, 'Synthetic line');
    assert.equal(h.context.state.setting.showLyric, false);
    assert.equal(h.context.state['page:vinylPage'].pageState, 'closed');
    assert.equal(h.context.state['async:lyric'], undefined);
  });
  await test('accurate native playback position and metadata', async h => {
    const f = h.read(); assert.equal(f.positionMs,12345); assert.equal(f.title,'Test'); assert.equal(f.artist,'Artist');
  });
  await test('paused playback', async h => {
    h.context.state.playing.playingState=1; assert.equal(h.read().playing,false);
  });
  await test('polls share one pending request', async h => {
    for(let i=0;i<20;i++) h.read(); await h.flush(); assert.equal(h.requests.length,1);
  });
  await test('UI lyrics can be cleared or stale without affecting mod lyrics', async h => {
    h.context.state['async:lyric']={lyricLines:[{time:0,lyric:'Wrong UI song'}]};
    h.read(); await h.flush(); await h.resolve();
    h.context.state['async:lyric']={lyricLines:[]};
    assert.equal(h.read().lyrics[0].text,'Synthetic line');
  });
  await test('track switch never exposes old lyrics while loading', async h => {
    h.read(); await h.flush(); await h.resolve(); h.select(202);
    const f=h.read(); assert.equal(f.status,'loading'); assert.deepEqual(f.lyrics,[]);
    await h.flush(); await h.resolve(1,[{time:2,lyric:'Second song'}]);
    assert.equal(h.read().lyrics[0].text,'Second song');
  });
  await test('out of order responses remain attached to their requested song', async h => {
    h.read(); await h.flush(); h.select(202); h.read(); await h.flush();
    await h.resolve(1,[{time:0,lyric:'Second song'}]); await h.resolve(0,[{time:0,lyric:'First song'}]);
    assert.equal(h.read().lyrics[0].text,'Second song');
    h.select(101); assert.equal(h.read().lyrics[0].text,'First song');
    await h.flush(); assert.equal(h.requests.length,2);
  });
  await test('native progress for another playback is suppressed', async h => {
    h.read(); await h.flush(); await h.resolve(); h.context.frame.playId='old';
    assert.equal(h.read().status,'loading'); assert.equal(h.read().positionMs,0); assert.deepEqual(h.read().lyrics,[]);
  });
  await test('invalid native progress is suppressed', async h => {
    h.context.frame.current=NaN; assert.equal(h.read().status,'loading'); assert.equal(h.read().positionMs,0);
  });
  await test('transient errors back off for 30 seconds then retry', async h => {
    h.read(); await h.flush(); h.requests[0].reject(Error('offline')); await h.flush();
    assert.equal(h.read().status,'unavailable'); await h.advance(29999); h.read(); await h.flush();
    assert.equal(h.requests.length,1); await h.advance(1); assert.equal(h.read().status,'loading');
    await h.flush(); assert.equal(h.requests.length,2); await h.resolve(); assert.equal(h.read().status,'ready');
  });
  await test('unsuccessful API code is not cached as a lyricless track', async h => {
    h.read(); await h.flush(); h.requests[0].resolve({code:503}); await h.flush(); assert.equal(h.read().status,'unavailable');
  });
  await test('hung requests time out and late responses cannot overwrite retries', async h => {
    h.read(); await h.flush(); await h.advance(12000); assert.equal(h.read().status,'unavailable');
    await h.advance(30000); h.read(); await h.flush(); await h.resolve(1,[{time:1,lyric:'Retry result'}]);
    await h.resolve(0,[{time:1,lyric:'Late result'}]); assert.equal(h.read().lyrics[0].text,'Retry result');
    assert.equal(h.context.__cloudlyricsReaderV2.active,0);
  });
  await test('at most two active requests during rapid track switches', async h => {
    h.read(); await h.flush(); h.select(202); h.read(); await h.flush(); h.select(303); h.read(); await h.flush();
    assert.equal(h.requests.length,2); await h.resolve(0); h.read(); await h.flush();
    assert.equal(h.requests.length,3); assert.equal(h.requests[2].args.id,'303');
  });
  await test('instrumentals and missing synced lyrics stay silent', async h => {
    h.read(); await h.flush(); await h.resolve(0,[]); assert.equal(h.read().status,'no_synced_lyrics');
    assert.deepEqual(h.read().lyrics,[]); await h.advance(60000); h.read(); await h.flush(); assert.equal(h.requests.length,1);
  });
  await test('untimed pure text is not emitted at the start of playback', async h => {
    h.read(); await h.flush(); await h.resolve(0,[{time:0,lyric:'Untimed text'}],{displayType:'pure'});
    assert.equal(h.read().status,'no_synced_lyrics');
  });
  await test('formatter timestamps are converted once without adding UI offsets', async h => {
    h.context.state['async:lyric']={offset:7}; h.read(); await h.flush();
    await h.resolve(0,[{time:1.234,lyric:'Line'}],{offset:5}); assert.equal(h.read().lyrics[0].timeMs,1234);
  });
  await test('invalid and negative cue timestamps are discarded', async h => {
    h.read(); await h.flush(); await h.resolve(0,[{time:-1},{time:NaN},{time:Infinity},{time:'2'},{time:0,lyric:'Valid'}]);
    assert.deepEqual(h.read().lyrics,[{timeMs:0,text:'Valid'}]);
  });
  await test('bounded cue count and line size', async h => {
    h.read(); await h.flush(); await h.resolve(0,Array.from({length:3100},(_,i)=>({time:i,lyric:'x'.repeat(1100)})));
    assert.equal(h.read().lyrics.length,3000); assert.equal(h.read().lyrics[0].text.length,1000);
  });
  await test('song cache is bounded and evicted songs reload', async h => {
    for(let i=1;i<=20;i++) { h.select(i); h.read(); await h.flush(); await h.resolve(); }
    h.read(); assert.equal(h.context.__cloudlyricsReaderV2.cache.size,16);
    h.select(1); assert.equal(h.read().status,'loading'); await h.flush(); assert.equal(h.requests.length,21);
  });
  await test('no current track performs no lyric request', async h => {
    h.context.state.playing={}; assert.equal(h.read().status,'idle'); await h.flush(); assert.equal(h.requests.length,0);
  });
  await test('non-song resource ids perform no lyric request', async h => {
    h.select('local-file'); assert.equal(h.read().status,'no_synced_lyrics'); await h.flush(); assert.equal(h.requests.length,0);
  });
  await test('unsupported client API fails explicitly', async h => {
    delete h.context.req.m[15]; assert.throws(h.read,/Unsupported Cloud Music version/);
  });
  console.log('PASS: '+checks+' Cloud Music adapter checks');
})().catch(error => { console.error(error); process.exitCode=1; });
