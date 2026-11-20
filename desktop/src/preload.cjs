const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('mochi', Object.freeze(Object.fromEntries(
    ['library', 'importFiles', 'search', 'resolve', 'like', 'playlist', 'playlistAdd', 'playlistRemove', 'playlistDelete', 'settings', 'played', 'lyrics', 'download', 'version'].map(name => [name, (...args) => ipcRenderer.invoke(`mochi:${name}`, ...args)])
)));
