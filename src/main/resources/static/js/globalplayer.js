/**
 * 全站播放器：切换页面时音乐不中断。
 *
 * 原理：把播放状态（当前歌曲、播放到第几秒、是否正在播）写进 sessionStorage，
 * 新页面加载时立刻读取并恢复。脚本放在 <head> 里【同步执行】，
 * 这样 audio 元素的恢复不用等页面渲染完，切页时听感的空档最短。
 *
 * 对外的 API（挂在 window.Player 上）：
 *   Player.playSong(song)   播放某一首（song 是 /song/list 返回的对象）
 *   Player.toggle()         播放 / 暂停
 *   Player.next() / prev()  上一首 / 下一首
 *   Player.current()        当前歌曲，没有则 null
 *   Player.isPlaying()      是否正在播放
 *   Player.on(type, fn)     订阅事件：change / play / pause（返回取消订阅函数）
 */
(function () {
  var KEY = 'shiguang.player.state';
  /** 超过这个时长的旧状态就不恢复了（比如昨天听的歌） */
  var MAX_AGE = 2 * 60 * 60 * 1000;

  var listeners = { change: [], play: [], pause: [] };
  var songs = null;      // 全部歌曲，用于上一首/下一首，懒加载
  var currentSong = null;
  var ui = null;         // 播放条上的各个元素
  var lastSaved = 0;

  /* ---------------- 状态存取 ---------------- */
  function loadState() {
    try {
      var raw = sessionStorage.getItem(KEY);
      if (!raw) return null;
      var s = JSON.parse(raw);
      if (!s || !s.song || !s.song.audioUrl) return null;
      if (Date.now() - (s.ts || 0) > MAX_AGE) return null;
      return s;
    } catch (e) {
      return null;
    }
  }

  function saveState(force) {
    if (!currentSong) return;
    var now = Date.now();
    if (!force && now - lastSaved < 800) return;   // 别写太频繁
    lastSaved = now;
    try {
      sessionStorage.setItem(KEY, JSON.stringify({
        song: currentSong,
        time: audio.currentTime || 0,
        playing: !audio.paused,
        ts: now
      }));
    } catch (e) { /* 隐私模式下可能写不了，忽略 */ }
  }

  function clearState() {
    try { sessionStorage.removeItem(KEY); } catch (e) { /* ignore */ }
  }

  /* ---------------- audio ---------------- */
  var audio = new Audio();
  audio.preload = 'metadata';

  function emit(type) {
    (listeners[type] || []).forEach(function (fn) {
      try { fn(currentSong); } catch (e) { /* 订阅方自己的错，不影响播放 */ }
    });
  }

  function fmt(sec) { return window.formatTime ? window.formatTime(sec) : '0:00'; }

  /* ---------------- 播放条 UI（延迟注入，不阻塞恢复） ---------------- */
  function buildUI() {
    var bar = document.createElement('div');
    bar.className = 'player-bar';
    bar.id = 'player-bar';
    bar.hidden = true;
    // 软导航换页时会保留带 data-persist 的元素，播放条靠这个活下来
    bar.setAttribute('data-persist', '');
    bar.innerHTML =
      '<div class="player-bar-inner">' +
        '<div class="pb-cover" id="pb-cover">♪</div>' +
        '<div class="pb-info">' +
          '<div class="pb-title" id="pb-title">—</div>' +
          '<div class="pb-artist" id="pb-artist">—</div>' +
        '</div>' +
        '<div class="pb-controls">' +
          '<button class="icon-btn" id="pb-prev" title="上一首" aria-label="上一首">' +
            '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>' +
          '</button>' +
          '<button class="play-btn" id="pb-play" aria-label="播放 / 暂停">' +
            '<svg class="icon-play" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>' +
            '<svg class="icon-pause" viewBox="0 0 24 24"><path d="M6 5h4v14H6zm8 0h4v14h-4z"/></svg>' +
          '</button>' +
          '<button class="icon-btn" id="pb-next" title="下一首" aria-label="下一首">' +
            '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M16 6h2v12h-2zM6 6l8.5 6L6 18z"/></svg>' +
          '</button>' +
        '</div>' +
        '<div class="pb-progress-wrap">' +
          '<div class="pb-progress" id="pb-progress"><div class="bar" id="pb-bar"></div></div>' +
          '<span class="pb-time" id="pb-time">0:00 / 0:00</span>' +
        '</div>' +
        '<button class="icon-btn" id="pb-lyric-btn" title="显示歌词" hidden>' +
          '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M4 6h16v2H4zm0 5h16v2H4zm0 5h10v2H4z"/></svg>' +
        '</button>' +
      '</div>' +
      '<div class="pb-lyric" id="pb-lyric" hidden></div>';

    document.body.appendChild(bar);

    ui = {
      bar: bar,
      cover: bar.querySelector('#pb-cover'),
      title: bar.querySelector('#pb-title'),
      artist: bar.querySelector('#pb-artist'),
      play: bar.querySelector('#pb-play'),
      prev: bar.querySelector('#pb-prev'),
      next: bar.querySelector('#pb-next'),
      progress: bar.querySelector('#pb-progress'),
      fill: bar.querySelector('#pb-bar'),
      time: bar.querySelector('#pb-time'),
      lyricBtn: bar.querySelector('#pb-lyric-btn'),
      lyric: bar.querySelector('#pb-lyric')
    };

    ui.play.addEventListener('click', toggle);
    ui.prev.addEventListener('click', prev);
    ui.next.addEventListener('click', next);

    ui.progress.addEventListener('click', function (e) {
      var rect = this.getBoundingClientRect();
      var ratio = Math.min(Math.max((e.clientX - rect.left) / rect.width, 0), 1);
      if (audio.duration) audio.currentTime = ratio * audio.duration;
      saveState(true);
    });

    ui.lyricBtn.addEventListener('click', function () {
      ui.lyric.hidden = !ui.lyric.hidden;
      this.classList.toggle('on', !ui.lyric.hidden);
    });

    refreshUI();
  }

  function refreshUI() {
    if (!ui || !currentSong) return;
    ui.bar.hidden = false;
    document.body.classList.add('has-player');

    ui.title.textContent = currentSong.title || '—';
    ui.artist.textContent = (currentSong.artist || '未知歌手') +
      (currentSong.uploaderName ? ' · 由 ' + currentSong.uploaderName + ' 推荐' : '');

    if (currentSong.coverUrl) {
      ui.cover.outerHTML = '<img class="pb-cover" id="pb-cover" src="' +
        window.escapeHtml(currentSong.coverUrl) + '" alt="">';
      ui.cover = document.getElementById('pb-cover');
    } else {
      ui.cover.outerHTML = '<div class="pb-cover" id="pb-cover">♪</div>';
      ui.cover = document.getElementById('pb-cover');
    }

    ui.lyricBtn.hidden = !currentSong.lyric;
    ui.lyric.textContent = currentSong.lyric || '';
    ui.lyric.hidden = true;
    ui.lyricBtn.classList.remove('on');

    ui.play.classList.toggle('playing', !audio.paused);
  }

  /* ---------------- 播放控制 ---------------- */
  function playSong(song, startAt) {
    if (!song || !song.audioUrl) return;
    var sameSong = currentSong && String(currentSong.id) === String(song.id);

    currentSong = song;
    emit('change');

    if (!sameSong) {
      audio.src = song.audioUrl;
      if (startAt) {
        audio.addEventListener('loadedmetadata', function seekOnce() {
          audio.removeEventListener('loadedmetadata', seekOnce);
          try { audio.currentTime = startAt; } catch (e) { /* ignore */ }
        });
      }
    }

    refreshUI();
    audio.play().catch(function () {
      // 浏览器可能拦截自动播放：这种情况下就让用户自己点一下
      if (ui) ui.play.classList.remove('playing');
      saveState(true);
    });
  }

  function toggle() {
    if (!currentSong) {
      loadSongs().then(function () { if (songs && songs.length) playSong(songs[0]); });
      return;
    }
    if (audio.paused) {
      audio.play().catch(function () { window.toast && window.toast('点击一下页面再试', 'err'); });
    } else {
      audio.pause();
    }
  }

  function step(delta) {
    var go = function (list) {
      if (!list || !list.length) return;
      var i = 0;
      if (currentSong) {
        for (var k = 0; k < list.length; k++) {
          if (String(list[k].id) === String(currentSong.id)) { i = k; break; }
        }
      }
      var next = (i + delta + list.length) % list.length;
      playSong(list[next]);
    };
    if (songs) { go(songs); return; }
    loadSongs().then(go);
  }

  function next() { step(1); }
  function prev() { step(-1); }

  function loadSongs() {
    if (songs) return Promise.resolve(songs);
    return fetch('/song/list', { credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (res) {
        songs = (res && res.data) || [];
        return songs;
      })
      .catch(function () { songs = []; return songs; });
  }

  /* ---------------- audio 事件 ---------------- */
  audio.addEventListener('play', function () {
    if (ui) ui.play.classList.add('playing');
    emit('play');
    saveState(true);
  });
  audio.addEventListener('pause', function () {
    if (ui) ui.play.classList.remove('playing');
    emit('pause');
    saveState(true);
  });
  audio.addEventListener('timeupdate', function () {
    if (!ui) return;
    var pct = audio.duration ? (audio.currentTime / audio.duration) * 100 : 0;
    ui.fill.style.width = pct + '%';
    ui.time.textContent = fmt(audio.currentTime) + ' / ' + fmt(audio.duration);
    saveState(false);
  });
  audio.addEventListener('loadedmetadata', function () {
    if (ui) ui.time.textContent = '0:00 / ' + fmt(audio.duration);
  });
  audio.addEventListener('ended', function () {
    // 播完自动下一首；只有一首就重头播
    step(1);
  });
  audio.addEventListener('error', function () {
    if (ui && currentSong) ui.time.textContent = '音频加载失败';
  });

  // 关页面 / 切到后台时把进度存准
  window.addEventListener('pagehide', function () { saveState(true); });
  window.addEventListener('beforeunload', function () { saveState(true); });
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden') saveState(true);
  });

  /* ---------------- 启动：先把音频恢复起来 ---------------- */
  var saved = loadState();
  if (saved) {
    currentSong = saved.song;
    audio.src = saved.song.audioUrl;

    // 位置要等元数据到了才能设
    audio.addEventListener('loadedmetadata', function restoreOnce() {
      audio.removeEventListener('loadedmetadata', restoreOnce);
      try { audio.currentTime = saved.time || 0; } catch (e) { /* ignore */ }
      if (saved.playing) {
        audio.play().catch(function () {
          // 自动播放被拦：保持暂停但进度已经恢复，用户点一下就能接着听
          if (ui) ui.play.classList.remove('playing');
        });
      }
    });
  }

  // UI 等 DOM 就绪再注入，不挡着上面恢复播放
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', buildUI);
  } else {
    buildUI();
  }

  /* ---------------- 对外 API ---------------- */
  window.Player = {
    playSong: playSong,
    toggle: toggle,
    next: next,
    prev: prev,
    current: function () { return currentSong; },
    isPlaying: function () { return !audio.paused; },
    /** 当前播放位置（秒）与总时长（秒） */
    time: function () { return audio.currentTime || 0; },
    duration: function () { return audio.duration || 0; },
    list: function () { return songs; },
    on: function (type, fn) {
      if (!listeners[type]) listeners[type] = [];
      listeners[type].push(fn);
      return function () {
        listeners[type] = listeners[type].filter(function (f) { return f !== fn; });
      };
    },
    /** 供曲库页判断某一首是不是正在播 */
    isCurrent: function (id) {
      return !!currentSong && String(currentSong.id) === String(id);
    }
  };
})();
