const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('mochy', {
  search: (query) => ipcRenderer.invoke('music:search', query),
  home: () => ipcRenderer.invoke('music:home'),
  stream: (videoId) => ipcRenderer.invoke('music:stream', videoId),
  lyrics: (payload) => ipcRenderer.invoke('music:lyrics', payload),
  openExternal: (url) => ipcRenderer.invoke('shell:open', url),
  version: () => ipcRenderer.invoke('app:version'),
})
