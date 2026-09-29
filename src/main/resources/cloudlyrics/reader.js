// Reads playback and loads lyrics through the player's own API; never opens its lyrics UI.
(() => {
  let bridge = window.__cloudlyricsReaderV2;
  if (!bridge) {
    if (!window.webpackJsonp) throw new Error('Unsupported Cloud Music web runtime');
    let require = window.__cloudlyricsRequireV1;
    if (!require) window.webpackJsonp.push([['cloudlyrics_reader_v1'], {
      cloudlyrics_reader_v1: (module, exports, r) => { require = window.__cloudlyricsRequireV1 = r; }
    }, [['cloudlyrics_reader_v1']]]);
    if (!require) throw new Error('Cloud Music module bridge unavailable');
    let app, getProgress, fetchLyrics, formatLyrics;
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
          if (typeof candidate === 'function' && /^\(\)=>[\w$]+$/.test(candidate.toString())) {
            const frame = candidate();
            if (frame && typeof frame.current === 'number' && 'playId' in frame) getProgress = candidate;
          }
        }
      }
      // Resolve the lyric API by its route and export binding, not version-specific module IDs.
      if (source && source.includes('/api/song/lyric/v1')) {
        const binding = source.match(/(?:^|[,;])([\w$]+)=Object\([\w$.]+\)\(\{url:["']\/api\/song\/lyric\/v1["']/);
        if (binding) {
          for (const match of source.matchAll(/\.d\([\w$]+,"([^"]+)",\(function\(\)\{return ([\w$]+)\}\)\)/g)) {
            if (match[2] === binding[1] && typeof exports[match[1]] === 'function') fetchLyrics = exports[match[1]];
          }
        }
      }
    }
    // This pure formatting module can be lazy-loaded while every lyric window is closed.
    for (const [id, factory] of Object.entries(require.m || {})) {
      const source = factory.toString();
      if (!source.includes('headArtistInfos') || !source.includes('rawYrc') ||
          !source.includes('currentUsedLyric:"none"') || source.includes('namespace:')) continue;
      const exports = require(id);
      for (const value of Object.values(exports || {})) {
        if (typeof value === 'function' && value.length === 1 &&
            value.toString().includes('currentUsedLyric:"none"') && value.toString().includes('lyricLines')) {
          formatLyrics = value;
        }
      }
      if (formatLyrics) break;
    }
    if (!app || !getProgress || !fetchLyrics || !formatLyrics)
      throw new Error('Unsupported Cloud Music version: playback or lyric API not found');
    bridge = window.__cloudlyricsReaderV2 = { app, getProgress, fetchLyrics, formatLyrics, cache: new Map(), active: 0 };
  }

  const p = bridge.app.getStore().playing || {};
  const songId = String(p.onlineResourceId || p.resourceTrackId || p.curPlaying?.resourceId || '');
  const frame = bridge.getProgress();
  const frameMatches = frame && Number.isFinite(frame.current) && String(frame.playId) === String(p.playId);
  const now = Date.now();
  let entry = bridge.cache.get(songId);
  if (songId && /^\d+$/.test(songId) && (!entry || (entry.status === 'unavailable' && now >= entry.retryAt)) && bridge.active < 2) {
    entry = { status: 'loading', lyrics: [], retryAt: 0 };
    bridge.cache.delete(songId);
    bridge.cache.set(songId, entry);
    bridge.active++;
    const requestedEntry = entry;
    let settled = false;
    const finish = (status, lyrics) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      bridge.active--;
      requestedEntry.status = status;
      requestedEntry.lyrics = lyrics;
      requestedEntry.retryAt = Date.now() + 30000;
    };
    const timer = setTimeout(() => finish('unavailable', []), 12000);
    Promise.resolve().then(() => bridge.fetchLyrics({ id: songId, lv: -1, tv: -1, rv: -1, yv: -1 }))
      .then(data => {
        if (settled) return;
        if (!data || Number(data.code) !== 200) throw new Error('Lyric request unavailable');
        const parsed = bridge.formatLyrics(data);
        const lyrics = parsed.displayType === 'pure' ? [] : (parsed.lyricLines || []).slice(0, 3000)
          .filter(line => Number.isFinite(line.time) && line.time >= 0)
          .map(line => ({ timeMs: Math.round(line.time * 1000), text: String(line.lyric || '').slice(0, 1000) }));
        finish(lyrics.length ? 'ready' : 'no_synced_lyrics', lyrics);
      }).catch(() => finish('unavailable', []));
  }
  // Responses belong to their requested song, even if they finish after a track change.
  // Never consult async:lyric: the player clears that UI store when lyric windows close.
  for (const [key, item] of bridge.cache) {
    if (bridge.cache.size <= 16) break;
    if (key !== songId && item.status !== 'loading') bridge.cache.delete(key);
  }
  let status = !songId ? 'idle' : !/^\d+$/.test(songId) ? 'no_synced_lyrics' : entry?.status || 'loading';
  if (songId && !frameMatches) status = 'loading';
  return {
    songId,
    playbackId: String(p.playId || songId),
    title: String(p.resourceName || ''),
    artist: (p.resourceArtists || []).map(a => a.name || '').join(' / '),
    playing: p.playingState === 2,
    positionMs: frameMatches ? Math.round(frame.current * 1000) : 0,
    status,
    // The player formatter applies returned lyric offsets exactly once.
    lyrics: status === 'ready' ? entry.lyrics : []
  };
})()
