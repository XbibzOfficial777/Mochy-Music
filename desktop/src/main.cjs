const { app, BrowserWindow, ipcMain, dialog, protocol, session } = require('electron');
const fs = require('node:fs/promises');
const { createReadStream, createWriteStream } = require('node:fs');
const { Readable } = require('node:stream');
const { pipeline } = require('node:stream/promises');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { createHash, randomUUID } = require('node:crypto');
const { Library, byteRange } = require('./core.cjs');
const { Engine } = require('./engine.cjs');
const { fetchMedia } = require('./media.cjs');
const root = path.resolve(__dirname, '..');
const pageUrl = pathToFileURL(path.join(root, 'renderer/index.html')).href;
const resources = app.isPackaged ? path.join(process.resourcesPath, 'resources') : path.join(root, 'resources');
const engine = new Engine(path.join(resources, `yt-dlp${process.platform === 'win32' ? '.exe' : ''}`));
const streams = new Map();
const downloads = new Set();
let library, window;
// Isolated user data is also useful for portable development runs; packaged apps use the OS profile.
if (process.env.MOCHI_USER_DATA) {
    if (!path.isAbsolute(process.env.MOCHI_USER_DATA)) throw new Error('MOCHI_USER_DATA must be an absolute path.');
    app.setPath('userData', process.env.MOCHI_USER_DATA);
}
const primaryInstance = app.requestSingleInstanceLock();
if (!primaryInstance) {
    app.quit();
    process.exit(0);
}
app.on('second-instance', () => { if (window) { if (window.isMinimized()) window.restore(); window.focus(); } });
protocol.registerSchemesAsPrivileged([{ scheme: 'mochi', privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true } }]);
function handle(name, fn) {
    ipcMain.handle(`mochi:${name}`, (event, ...args) => {
        if (event.senderFrame !== window?.webContents.mainFrame || event.senderFrame.url !== pageUrl) throw new Error('Unauthorized frame');
        return fn(...args);
    });
}
async function importPaths(paths) {
    const { parseFile } = await import('music-metadata');
    const errors = [];
    for (const file of paths) {
        if (!/\.(mp3|flac|wav|ogg|opus|m4a|aac|webm)$/i.test(file)) continue;
        try {
            const real = await fs.realpath(file);
            const { common, format } = await parseFile(real);
            const picture = common.picture?.[0];
            library.add({ id: `local:${createHash('sha256').update(real).digest('hex')}`, source: 'local', path: real,
                title: common.title || path.basename(real, path.extname(real)), artist: common.artist || 'Unknown artist',
                album: common.album || '', duration: Number.isFinite(format.duration) ? format.duration : 0,
                artwork: picture && picture.data.length < 4000000 ? `data:${picture.format};base64,${Buffer.from(picture.data).toString('base64')}` : '' });
        } catch (error) { errors.push(`${path.basename(file)}: ${error.message}`); }
    }
    await library.save();
    return { data: library.data, errors };
}
function registerHandlers() {
    handle('version', () => ({ version: app.getVersion(), platform: process.platform }));
    handle('library', () => library.data);
    handle('importFiles', async () => {
        const result = await dialog.showOpenDialog(window, { title: 'Tambah musik', properties: ['openFile', 'multiSelections'], filters: [{ name: 'Audio', extensions: ['mp3', 'flac', 'wav', 'ogg', 'opus', 'm4a', 'aac', 'webm'] }] });
        return result.canceled ? { data: library.data, errors: [] } : importPaths(result.filePaths);
    });
    handle('search', async query => {
        const tracks = await engine.search(query);
        tracks.forEach(t => library.add(t));
        await library.save();
        return tracks;
    });
    handle('resolve', async id => {
        const track = library.get(id);
        const token = randomUUID();
        const source = track.source === 'local' || track.downloadPath ? { path: track.downloadPath || track.path } : await engine.resolve(track.videoId);
        // Keep in-flight media requests valid, but cap stale tokens.
        streams.set(token, source);
        if (streams.size > 100) streams.delete(streams.keys().next().value);
        return `mochi://audio/${token}`;
    });
    handle('like', async id => {
        library.get(id);
        const liked = library.data.liked;
        library.data.liked = liked.includes(id) ? liked.filter(x => x !== id) : [...liked, id];
        return library.save();
    });
    handle('playlist', async name => { library.playlist(name); return library.save(); });
    handle('playlistAdd', async (playlistId, id) => {
        library.get(id);
        const list = library.data.playlists.find(p => p.id === playlistId);
        if (!list) throw new Error('Playlist tidak ditemukan.');
        if (!list.tracks.includes(id)) list.tracks.push(id);
        return library.save();
    });
    handle('playlistRemove', async (playlistId, id) => {
        const list = library.data.playlists.find(p => p.id === playlistId);
        if (!list) throw new Error('Playlist tidak ditemukan.');
        list.tracks = list.tracks.filter(t => t !== id);
        return library.save();
    });
    handle('playlistDelete', async id => { library.data.playlists = library.data.playlists.filter(p => p.id !== id); return library.save(); });
    handle('played', async id => {
        library.get(id);
        library.data.history = [id, ...library.data.history.filter(x => x !== id)].slice(0, 100);
        return library.save();
    });
    handle('settings', async changes => {
        if (!changes || typeof changes !== 'object') throw new Error('Pengaturan tidak valid.');
        const s = library.data.settings;
        if ('volume' in changes && Number.isFinite(changes.volume)) s.volume = Math.min(1, Math.max(0, changes.volume));
        if (['off', 'all', 'one'].includes(changes.repeat)) s.repeat = changes.repeat;
        if (typeof changes.shuffle === 'boolean') s.shuffle = changes.shuffle;
        s.theme = 'dark';
        if (Array.isArray(changes.eq) && changes.eq.length === 6 && changes.eq.every(n => Number.isFinite(n) && Math.abs(n) <= 12)) s.eq = changes.eq;
        return library.save();
    });
    handle('lyrics', async id => {
        const t = library.get(id);
        const params = new URLSearchParams({ track_name: t.title, artist_name: t.artist });
        const response = await fetch(`https://lrclib.net/api/search?${params}`, { signal: AbortSignal.timeout(15000), headers: { 'User-Agent': 'MochiBibz/0.1.0 (https://github.com/XbibzOfficial777/Mochy-Music)' } });
        if (!response.ok) throw new Error(`Layanan lirik: HTTP ${response.status}`);
        const results = await response.json();
        return results.sort((a, b) => Math.abs(a.duration - t.duration) - Math.abs(b.duration - t.duration))[0] || null;
    });
    handle('download', async id => {
        const t = library.get(id);
        if (t.source !== 'youtube') throw new Error('Lagu ini sudah tersimpan lokal.');
        if (downloads.has(id)) throw new Error('Unduhan lagu ini sedang berlangsung.');
        downloads.add(id);
        try {
        const media = await engine.resolve(t.videoId);
        const filename = `Mochi-${t.title.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').slice(0, 120)}.${media.ext}`;
        const result = await dialog.showSaveDialog(window, { defaultPath: path.join(app.getPath('music'), filename) });
        if (result.canceled) return library.data;
        const temp = `${result.filePath}.${randomUUID()}.part`;
        try {
            const response = await fetchMedia(media.url, { headers: media.headers, signal: AbortSignal.timeout(300000) });
            if (!response.ok) throw new Error(`Unduhan gagal: HTTP ${response.status}`);
            await pipeline(Readable.fromWeb(response.body), createWriteStream(temp));
            await fs.rename(temp, result.filePath);
            t.downloadPath = result.filePath;
            return library.save();
        } catch (error) { await fs.unlink(temp).catch(() => {}); throw error; }
        } finally { downloads.delete(id); }
    });
}
async function media(request) {
    const url = new URL(request.url);
    const source = url.hostname === 'audio' && streams.get(url.pathname.slice(1));
    if (!source || !['GET', 'HEAD'].includes(request.method)) return new Response(null, { status: 404 });
    const cors = { 'Access-Control-Allow-Origin': '*', 'Accept-Ranges': 'bytes' };
    try {
        if (source.path) {
            const stat = await fs.stat(source.path);
            if (!stat.size) return new Response(null, { status: 204, headers: cors });
            const range = byteRange(request.headers.get('range'), stat.size);
            if (!range) return new Response(null, { status: 416, headers: { ...cors, 'Content-Range': `bytes */${stat.size}` } });
            const types = { '.wav': 'audio/wav', '.mp3': 'audio/mpeg', '.flac': 'audio/flac', '.m4a': 'audio/mp4', '.aac': 'audio/aac', '.opus': 'audio/ogg', '.ogg': 'audio/ogg', '.webm': 'audio/webm' };
            const headers = { ...cors, 'Content-Type': types[path.extname(source.path).toLowerCase()] || 'application/octet-stream', 'Content-Length': String(range.end - range.start + 1) };
            if (range.partial) headers['Content-Range'] = `bytes ${range.start}-${range.end}/${stat.size}`;
            return new Response(request.method === 'HEAD' ? null : Readable.toWeb(createReadStream(source.path, { start: range.start, end: range.end })), { status: range.partial ? 206 : 200, headers });
        }
        const headers = { ...source.headers };
        if (request.headers.has('range')) headers.Range = request.headers.get('range');
        const response = await fetchMedia(source.url, { headers, method: request.method, signal: AbortSignal.any([request.signal, AbortSignal.timeout(120000)]) });
        const out = new Headers(cors);
        for (const h of ['content-type', 'content-length', 'content-range']) if (response.headers.has(h)) out.set(h, response.headers.get(h));
        return new Response(response.body, { status: response.status, headers: out });
    } catch { return new Response('Audio unavailable', { status: 502, headers: cors }); }
}
function createWindow() {
    window = new BrowserWindow({ width: 1280, height: 900, minWidth: 390, minHeight: 620, backgroundColor: '#000000', title: 'Mochi Bibz', icon: path.join(resources, 'icon.png'), autoHideMenuBar: true,
        webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true } });
    window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
    window.webContents.on('will-navigate', (event, url) => { if (url !== pageUrl) event.preventDefault(); });
    window.loadURL(pageUrl);
}
app.whenReady().then(async () => {
    if (!primaryInstance) return;
    library = new Library(path.join(app.getPath('userData'), 'library.json'));
    await library.load();
    const files = process.argv.slice(app.isPackaged ? 1 : 2).filter(arg => path.isAbsolute(arg) && /\.(mp3|flac|wav|ogg|opus|m4a|aac|webm)$/i.test(arg));
    if (files.length) await importPaths(files);
    protocol.handle('mochi', media);
    session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => callback(false));
    registerHandlers();
    createWindow();
    app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
}).catch(error => { dialog.showErrorBox('Mochi Bibz gagal dibuka', error.message); app.quit(); });
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
