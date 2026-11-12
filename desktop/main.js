'use strict'

const { app, BrowserWindow, ipcMain, shell } = require('electron')
const http = require('node:http')
const https = require('node:https')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const SMOKE = process.argv.includes('--smoke')
if (SMOKE) {
  app.disableHardwareAcceleration()
}
const IDENTITY = {
  name: 'Xbibz Official Technology ID',
  kofi: 'https://ko-fi.com/xbibzofficial',
  saweria: 'https://saweria.co/xbibzofficial',
  telegram: 'https://t.me/xbibzofficial',
  youtube: 'https://www.youtube.com/@XbibzOfficial',
  github: 'https://github.com/XbibzOfficial777/Mochy-Music',
}

let win
let proxyServer
let proxyPort = 0
let innertubePromise

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 640,
    backgroundColor: '#0c1018',
    title: 'Mochy Xbibz',
    autoHideMenuBar: true,
    icon: path.join(__dirname, 'resources', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'))
  if (SMOKE) {
    win.webContents.once('did-finish-load', () => {
      const marker = path.join(process.cwd(), 'desktop-smoke-ok.txt')
      fs.writeFileSync(marker, `ok ${new Date().toISOString()}\n`)
      setTimeout(() => app.quit(), 400)
    })
  }
}

async function getTube() {
  if (!innertubePromise) {
    innertubePromise = (async () => {
      const { Innertube } = await import('youtubei.js')
      return Innertube.create({ generate_session_locally: true })
    })()
  }
  return innertubePromise
}

function thumbUrl(thumbnails) {
  if (!Array.isArray(thumbnails) || thumbnails.length === 0) return ''
  const last = thumbnails[thumbnails.length - 1]
  return last?.url || last?.url || ''
}

function asArtist(item) {
  const artists = item.artists || item.author || item.authors
  if (Array.isArray(artists)) {
    return artists.map((a) => a.name || a.text || String(a)).filter(Boolean).join(', ')
  }
  if (artists && typeof artists === 'object') return artists.name || artists.text || ''
  if (item.artist?.name) return item.artist.name
  return item.author?.name || ''
}

function asId(item) {
  return item.id || item.video_id || item.videoId || item.endpoint?.payload?.videoId || ''
}

function asTitle(item) {
  if (typeof item.title === 'string') return item.title
  return item.title?.text || item.name || item.flex_columns?.[0]?.title?.text || 'Tanpa judul'
}

function asDuration(item) {
  return item.duration?.text || item.duration?.seconds || item.length_text || ''
}

function normalizeItems(list) {
  if (!list) return []
  const contents = Array.isArray(list) ? list : list.contents || list.results || []
  return contents
    .map((item) => {
      const id = asId(item)
      if (!id) return null
      return {
        id: String(id),
        title: asTitle(item),
        artist: asArtist(item),
        duration: asDuration(item),
        thumbnail: thumbUrl(item.thumbnails || item.thumbnail?.contents || item.thumbnail),
      }
    })
    .filter(Boolean)
}

function pickFromSearch(data) {
  const buckets = [
    data?.songs,
    data?.videos,
    data?.contents,
    data?.results,
    data,
  ]
  const out = []
  const seen = new Set()
  for (const bucket of buckets) {
    for (const item of normalizeItems(bucket)) {
      if (seen.has(item.id)) continue
      seen.add(item.id)
      out.push(item)
    }
  }
  return out
}

async function audioUrlFor(videoId) {
  const yt = await getTube()
  const info = await yt.getBasicInfo(videoId)
  const format = info.chooseFormat({ type: 'audio', quality: 'best' })
  if (!format) throw new Error('Tidak ada berkas audio')
  if (format.url) return format.url
  if (typeof format.decipher === 'function') {
    return format.decipher(yt.session.player)
  }
  if (yt.session?.player?.decipher) {
    return yt.session.player.decipher(format.signature_cipher || format.url_cipher || '')
  }
  throw new Error('Tidak bisa mengurai aliran audio')
}

function startProxy() {
  proxyServer = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1')
    if (url.pathname !== '/stream') {
      res.writeHead(404)
      res.end()
      return
    }
    const id = url.searchParams.get('id') || ''
    if (!/^[a-zA-Z0-9_-]{6,}$/.test(id)) {
      res.writeHead(400)
      res.end('id')
      return
    }
    audioUrlFor(id)
      .then((target) => {
        const parsed = new URL(target)
        const lib = parsed.protocol === 'http:' ? http : https
        const headers = {
          'User-Agent': 'Mozilla/5.0 MochyXbibzDesktop/0.1',
        }
        if (req.headers.range) headers.Range = req.headers.range
        const up = lib.request(
          parsed,
          { method: 'GET', headers },
          (incoming) => {
            const outHeaders = {
              'Content-Type': incoming.headers['content-type'] || 'audio/webm',
              'Accept-Ranges': incoming.headers['accept-ranges'] || 'bytes',
            }
            if (incoming.headers['content-length']) {
              outHeaders['Content-Length'] = incoming.headers['content-length']
            }
            if (incoming.headers['content-range']) {
              outHeaders['Content-Range'] = incoming.headers['content-range']
            }
            res.writeHead(incoming.statusCode || 200, outHeaders)
            incoming.pipe(res)
          },
        )
        up.on('error', () => {
          if (!res.headersSent) res.writeHead(502)
          res.end()
        })
        up.end()
      })
      .catch((err) => {
        if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
        res.end(String(err && err.message ? err.message : err))
      })
  })
  proxyServer.listen(0, '127.0.0.1', () => {
    proxyPort = proxyServer.address().port
  })
}

function allowedExternal(url) {
  try {
    const u = new URL(url)
    const hosts = new Set([
      'ko-fi.com',
      'www.ko-fi.com',
      'saweria.co',
      'www.saweria.co',
      't.me',
      'www.youtube.com',
      'youtube.com',
      'github.com',
    ])
    return (u.protocol === 'https:' || u.protocol === 'http:') && hosts.has(u.hostname)
  } catch {
    return false
  }
}

app.whenReady().then(() => {
  startProxy()
  createWindow()
})

app.on('window-all-closed', () => {
  if (proxyServer) proxyServer.close()
  app.quit()
})

ipcMain.handle('app:version', () => ({
  version: app.getVersion(),
  identity: IDENTITY.name,
  links: IDENTITY,
  proxy: proxyPort,
  fileUrl: pathToFileURL(path.join(__dirname, 'renderer', 'index.html')).toString(),
}))

ipcMain.handle('shell:open', async (_e, url) => {
  if (!allowedExternal(url)) return false
  await shell.openExternal(url)
  return true
})

ipcMain.handle('music:search', async (_e, query) => {
  const q = String(query || '').trim()
  if (q.length < 1 || q.length > 120) return { items: [] }
  const yt = await getTube()
  const data = await yt.music.search(q, { type: 'song' })
  return { items: pickFromSearch(data).slice(0, 40) }
})

ipcMain.handle('music:home', async () => {
  try {
    const yt = await getTube()
    const home = await yt.music.getHome()
    const sections = home?.sections || home?.contents || []
    const items = []
    const seen = new Set()
    for (const section of sections) {
      for (const item of normalizeItems(section.contents || section)) {
        if (seen.has(item.id)) continue
        seen.add(item.id)
        items.push(item)
      }
    }
    return { items: items.slice(0, 30) }
  } catch (err) {
    return { items: [], error: String(err && err.message ? err.message : err) }
  }
})

ipcMain.handle('music:stream', async (_e, videoId) => {
  const id = String(videoId || '')
  if (!/^[a-zA-Z0-9_-]{6,}$/.test(id)) throw new Error('id tidak sah')
  // Wait until the loopback proxy has bound.
  for (let i = 0; i < 50 && !proxyPort; i += 1) {
    await new Promise((r) => setTimeout(r, 20))
  }
  return { url: `http://127.0.0.1:${proxyPort}/stream?id=${encodeURIComponent(id)}` }
})

ipcMain.handle('music:lyrics', async (_e, payload) => {
  const track = encodeURIComponent(String(payload?.title || '').slice(0, 120))
  const artist = encodeURIComponent(String(payload?.artist || '').slice(0, 120))
  if (!track) return { plain: '' }
  const url = `https://lrclib.net/api/search?track_name=${track}&artist_name=${artist}`
  const json = await new Promise((resolve, reject) => {
    https
      .get(url, { headers: { 'User-Agent': 'MochyXbibzDesktop/0.1' } }, (res) => {
        const chunks = []
        res.on('data', (c) => chunks.push(c))
        res.on('end', () => {
          try {
            resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '[]'))
          } catch (err) {
            reject(err)
          }
        })
      })
      .on('error', reject)
  })
  const hit = Array.isArray(json) ? json[0] : null
  return {
    plain: hit?.plainLyrics || hit?.syncedLyrics || '',
    synced: hit?.syncedLyrics || '',
  }
})
