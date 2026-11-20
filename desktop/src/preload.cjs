const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('mochi', Object.freeze(Object.fromEntries(
    ['library', 'importFiles', 'suggest', 'search', 'resolve', 'diagnostics', 'like', 'playlist', 'playlistAdd', 'playlistRemove', 'playlistDelete', 'settings', 'played', 'lyrics', 'download', 'version'].map(name => [name, (...args) => ipcRenderer.invoke(`mochi:${name}`, ...args)])
)));
