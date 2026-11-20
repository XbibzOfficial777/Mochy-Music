const fs = require('node:fs/promises');
const path = require('node:path');
const { Engine } = require('../src/engine.cjs');
const { fetchMedia } = require('../src/media.cjs');

(async () => {
    const engine = new Engine(path.resolve('resources', `yt-dlp${process.platform === 'win32' ? '.exe' : ''}`));
    const results = await engine.search('Rick Astley Never Gonna Give You Up');
    if (!results.length) throw new Error('YouTube returned no search results.');
    const media = await engine.resolve(results[0].videoId);
    const response = await fetchMedia(media.url, { headers: { ...media.headers, Range: 'bytes=0-65535' }, signal: AbortSignal.timeout(30000) });
    if (!response.ok) throw new Error(`Audio HTTP ${response.status}`);
    const reader = response.body.getReader();
    const { value } = await reader.read();
    await reader.cancel();
    if (!value?.length) throw new Error('Audio response is empty.');
    const lyricsResponse = await fetch('https://lrclib.net/api/search?track_name=Never%20Gonna%20Give%20You%20Up&artist_name=Rick%20Astley', { signal: AbortSignal.timeout(30000), headers: { 'User-Agent': 'MochiBibz/0.1.0 (https://github.com/XbibzOfficial777/Mochy-Music)' } });
    if (!lyricsResponse.ok) throw new Error(`LRCLIB HTTP ${lyricsResponse.status}`);
    const lyrics = await lyricsResponse.json();
    if (!lyrics.some(item => item.plainLyrics || item.syncedLyrics)) throw new Error('LRCLIB returned no lyrics.');
    const report = { checkedAt: new Date().toISOString(), videoId: results[0].videoId, audioBytesRead: value.length, lyricsResults: lyrics.length };
    await fs.writeFile('online-report.json', JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
