// Read-only adapter for the official Cloud Music 3.x player. No account data is read.
(() => {
  let bridge = window.__cloudlyricsReaderV1;
  if (!bridge) {
    if (!window.webpackJsonp) throw new Error('Unsupported Cloud Music web runtime');
    let require = window.__cloudlyricsRequireV1;
    if (!require) window.webpackJsonp.push([['cloudlyrics_reader_v1'], {
      cloudlyrics_reader_v1: (module, exports, r) => { require = window.__cloudlyricsRequireV1 = r; }
    }, [['cloudlyrics_reader_v1']]]);
    if (!require) throw new Error('Cloud Music module bridge unavailable');
    let app, getProgress;
    for (const [id, mod] of Object.entries(require.c || {})) {
      const exports = mod.exports;
      if (!exports) continue;
      for (const key of Object.keys(exports)) {
        const value = exports[key];
        if (value && typeof value.getStore === 'function') app = value;
      }
      const source = require.m[id] && require.m[id].toString();
      if (source && source.includes('subscribePlayStatus') && source.includes('"playprogress"')) {
        for (const key of Object.keys(exports)) {
          const candidate = exports[key];
          // The native event module exposes a side-effect-free closure returning its latest frame.
          if (typeof candidate === 'function' && /^\(\)=>[\w$]+$/.test(candidate.toString())) {
            const frame = candidate();
            if (frame && typeof frame.current === 'number' && 'playId' in frame) getProgress = candidate;
          }
        }
      }
    }
    if (!app || !getProgress) throw new Error('Unsupported Cloud Music version: playback interface not found');
    bridge = window.__cloudlyricsReaderV1 = { app, getProgress, songId: null, lyricsRef: null, waiting: false };
  }
  const store = bridge.app.getStore();
  const p = store.playing || {};
  const l = store['async:lyric'] || {};
  const songId = String(p.resourceTrackId || p.curPlaying?.resourceId || '');
  const lines = l.lyricLines || [];
  if (bridge.songId !== songId) {
    bridge.waiting = bridge.songId !== null && bridge.lyricsRef === lines;
    bridge.songId = songId;
  }
  if (bridge.lyricsRef !== lines) bridge.waiting = false;
  bridge.lyricsRef = lines;
  const frame = bridge.getProgress();
  const frameMatches = frame && String(frame.playId) === String(p.playId);
  let status = 'ready';
  if (!songId) status = 'idle';
  else if (!frameMatches || l.isLoading || bridge.waiting) status = 'loading';
  else if (l.isLyricFetchFailed) status = 'unavailable';
  else if (!lines.some(line => typeof line.time === 'number' && line.time >= 0)) status = 'no_synced_lyrics';
  return {
    songId,
    playbackId: String(p.playId || songId),
    title: String(p.resourceName || ''),
    artist: (p.resourceArtists || []).map(a => a.name || '').join(' / '),
    playing: p.playingState === 2,
    positionMs: frameMatches ? Math.round(frame.current * 1000) : 0,
    status,
    // Cloud Music has already applied its own lyric offset to these timestamps.
    lyrics: status === 'ready' ? lines.slice(0,3000)
      .filter(line => typeof line.time === 'number' && line.time >= 0)
      .map(line => ({timeMs: Math.round(line.time * 1000), text: String(line.lyric || '').slice(0,1000)})) : []
  };
})()
