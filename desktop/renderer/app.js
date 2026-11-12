'use strict'

const $ = (id) => document.getElementById(id)
const audio = $('audio')
const state = {
  items: [],
  queue: [],
  index: -1,
  view: 'home',
}

function setStatus(text) {
  $('status').textContent = text
}

function fmt(t) {
  if (!Number.isFinite(t) || t < 0) return '0:00'
  const m = Math.floor(t / 60)
  const s = Math.floor(t % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

function show(view) {
  state.view = view
  document.querySelectorAll('.view').forEach((el) => el.classList.toggle('is-on', el.id === `view-${view}`))
  document.querySelectorAll('.nav-btn').forEach((el) => el.classList.toggle('is-on', el.dataset.view === view))
}

function card(item, { row = false } = {}) {
  const el = document.createElement('button')
  el.type = 'button'
  el.className = row ? 'row' : 'card'
  el.innerHTML = row
    ? `<img alt="" src="${item.thumbnail || ''}" /><div class="meta"><strong></strong><span></span></div><span>${item.duration || ''}</span>`
    : `<img alt="" src="${item.thumbnail || ''}" /><div><strong></strong><span></span></div>`
  el.querySelector('strong').textContent = item.title
  el.querySelector('span').textContent = item.artist
  el.addEventListener('click', () => playFrom(item, true))
  return el
}

function renderList(target, items, row) {
  target.replaceChildren()
  if (!items.length) {
    const p = document.createElement('p')
    p.className = 'lede'
    p.textContent = 'Belum ada lagu di sini.'
    target.append(p)
    return
  }
  items.forEach((item) => target.append(card(item, { row })))
}

async function playFrom(item, enqueue) {
  const existing = state.queue.findIndex((x) => x.id === item.id)
  if (existing >= 0) {
    state.index = existing
  } else if (enqueue) {
    state.queue.push(item)
    state.index = state.queue.length - 1
  } else {
    state.queue = [item]
    state.index = 0
  }
  renderList($('queue'), state.queue, true)
  $('title').textContent = item.title
  $('artist').textContent = item.artist || 'YouTube Music'
  $('cover').src = item.thumbnail || ''
  setStatus('Menyiapkan aliran…')
  try {
    const { url } = await window.mochy.stream(item.id)
    audio.src = url
    await audio.play()
    $('play').textContent = '❚❚'
    setStatus('Memutar')
    loadLyrics(item)
  } catch (err) {
    setStatus(err.message || 'Gagal memutar')
  }
}

async function loadLyrics(item) {
  $('lyrics').hidden = true
  try {
    const data = await window.mochy.lyrics({ title: item.title, artist: item.artist })
    if (data.plain) {
      $('lyrics-body').textContent = data.plain
      $('lyrics').hidden = false
    }
  } catch {
    /* lyrics are optional */
  }
}

function current() {
  return state.queue[state.index]
}

async function searchNow() {
  const q = $('q').value.trim()
  if (!q) return
  show('search')
  setStatus('Mencari…')
  try {
    const { items } = await window.mochy.search(q)
    state.items = items
    renderList($('results'), items, true)
    setStatus(items.length ? `${items.length} hasil` : 'Tidak ada hasil')
  } catch (err) {
    setStatus(err.message || 'Pencarian gagal')
  }
}

document.querySelectorAll('.nav-btn').forEach((btn) => {
  btn.addEventListener('click', () => show(btn.dataset.view))
})
$('go').addEventListener('click', searchNow)
$('q').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') searchNow()
})

$('play').addEventListener('click', async () => {
  if (!audio.src) {
    if (current()) await playFrom(current(), false)
    return
  }
  if (audio.paused) {
    await audio.play()
    $('play').textContent = '❚❚'
  } else {
    audio.pause()
    $('play').textContent = '▶'
  }
})
$('next').addEventListener('click', () => {
  if (!state.queue.length) return
  const next = (state.index + 1) % state.queue.length
  playFrom(state.queue[next], false)
})
$('prev').addEventListener('click', () => {
  if (audio.currentTime > 3) {
    audio.currentTime = 0
    return
  }
  if (!state.queue.length) return
  const prev = state.index > 0 ? state.index - 1 : state.queue.length - 1
  playFrom(state.queue[prev], false)
})

$('bar').addEventListener('click', (e) => {
  if (!audio.duration) return
  const rect = e.currentTarget.getBoundingClientRect()
  audio.currentTime = ((e.clientX - rect.left) / rect.width) * audio.duration
})
$('vol').addEventListener('input', () => {
  audio.volume = Number($('vol').value)
})
audio.addEventListener('timeupdate', () => {
  const p = audio.duration ? (audio.currentTime / audio.duration) * 100 : 0
  $('fill').style.width = `${p}%`
  $('thumb').style.left = `${p}%`
  $('t0').textContent = fmt(audio.currentTime)
  $('t1').textContent = fmt(audio.duration)
})
audio.addEventListener('ended', () => {
  if (state.index + 1 < state.queue.length) {
    state.index += 1
    playFrom(state.queue[state.index], false)
  } else {
    $('play').textContent = '▶'
    setStatus('Selesai')
  }
})

document.querySelectorAll('[data-open]').forEach((btn) => {
  btn.addEventListener('click', () => window.mochy.openExternal(btn.dataset.open))
})

;(async function boot() {
  try {
    const info = await window.mochy.version()
    $('about-ver').textContent = `Mochy Xbibz desktop ${info.version} · ${info.identity}`
  } catch {
    $('about-ver').textContent = 'Mochy Xbibz desktop'
  }
  setStatus('Memuat beranda…')
  try {
    const { items, error } = await window.mochy.home()
    renderList($('home-grid'), items, false)
    setStatus(error ? 'Cari lagu untuk mulai' : 'Siap')
  } catch {
    setStatus('Cari lagu untuk mulai')
  }
})()
