const { execFile } = require('node:child_process');
const { youtubeId, safeMediaUrl } = require('./core.cjs');
class Engine {
    constructor(binary) { this.binary = binary; }
    run(args) {
        return new Promise((resolve, reject) => {
            execFile(this.binary, ['--ignore-config', '--no-warnings', '--socket-timeout', '20', '--js-runtimes', `node:${process.execPath}`, ...args],
                { timeout: 90000, maxBuffer: 12 * 1024 * 1024, windowsHide: true, env: { ...process.env, ELECTRON_RUN_AS_NODE: '1' } }, (error, stdout, stderr) => {
                    if (error) reject(new Error((stderr || error.message).slice(-1200)));
                    else resolve(stdout);
                });
        });
    }
    async search(query) {
        if (typeof query !== 'string' || !query.trim() || query.length > 200) throw new Error('Masukkan kata pencarian (maksimal 200 karakter).');
        const data = JSON.parse(await this.run(['--flat-playlist', '--dump-single-json', '--', `ytsearch20:${query.trim()}`]));
        return (data.entries || []).filter(e => e.id && /^[\w-]{11}$/.test(e.id)).map(e => ({
            id: `yt:${e.id}`, videoId: e.id, source: 'youtube', title: e.title || 'Untitled',
            artist: e.artist || e.uploader || e.channel || '', duration: e.duration || 0,
            artwork: `https://i.ytimg.com/vi/${e.id}/hqdefault.jpg`, album: ''
        }));
    }
    async resolve(id) {
        const data = JSON.parse(await this.run(['--no-playlist', '-f', 'bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio', '--dump-single-json', '--', `https://www.youtube.com/watch?v=${youtubeId(id)}`]));
        if (!['m4a', 'webm', 'mp3', 'ogg', 'opus'].includes(data.ext)) throw new Error('Format audio tidak didukung.');
        return { url: safeMediaUrl(data.url), headers: data.http_headers || {}, ext: data.ext };
    }
}
module.exports = { Engine };
