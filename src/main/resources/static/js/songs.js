/** 曲库页：浏览全部歌曲，点击播放（播放由全站播放器 globalplayer.js 负责，切页不中断） */
(function () {
  var esc = window.escapeHtml;
  var songs = [];
  var todaySong = null;

  /* ---------------- 站点配置 / 顶栏 ---------------- */
  function applySite() {
    var s = window.SITE || {};
    if (s.name) {
      document.title = '曲库 · ' + s.name;
      document.querySelectorAll('[data-site-name]').forEach(function (el) { el.textContent = s.name; });
    }
  }

  async function loadNav() {
    var host = document.getElementById('nav-user');
    var res = await API.get('/user/me');
    if (res.ok && res.data) {
      var name = res.data.nickname || res.data.username;
      host.innerHTML = '<span class="nav-user">' +
        window.avatarHtml(name, res.data.avatar, 26) + esc(name) + '</span>';
    } else {
      // 站点关闭注册时，顶栏不该还留着「注册」入口
      host.innerHTML = '<a href="/admin.html">登录</a>' +
        (await window.loadRegistrationOpen() ? '<a href="/admin.html#register">注册</a>' : '');
    }
  }

  /* ---------------- 今日推荐 ---------------- */
  function todayHtml(s) {
    var cover = s.coverUrl
      ? '<img class="today-cover" src="' + esc(s.coverUrl) + '" alt="' + esc(s.title) + '">'
      : '<div class="today-cover">♪</div>';

    return '<div class="today-card">' + cover +
      '<div class="today-main">' +
        '<span class="label">' +
          (s.recommendDate === window.todayStr() ? '站长指定的今日推荐' : '今日自动轮换') +
        '</span>' +
        '<h3>' + esc(s.title) + '</h3>' +
        '<div class="artist">' + esc(s.artist || '未知歌手') +
          (s.uploaderName ? ' · 由 ' + esc(s.uploaderName) + ' 推荐' : '') + '</div>' +
      '</div>' +
      '<button class="btn btn-primary" type="button" data-play="' + s.id + '">播放这首</button>' +
    '</div>';
  }

  async function loadToday() {
    var res = await API.get('/song/today');
    todaySong = (res.ok && res.data) ? res.data : null;

    var block = document.getElementById('today-block');
    var stat = document.getElementById('stat-today');

    if (!todaySong) {
      block.hidden = true;
      stat.textContent = '暂无';
      return;
    }
    stat.textContent = todaySong.title;
    block.hidden = false;
    block.innerHTML = todayHtml(todaySong);
  }

  /* ---------------- 歌曲网格 ---------------- */
  function playIcon() {
    return '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>';
  }

  function eqIcon() {
    return '<div class="eq"><span></span><span></span><span></span><span></span></div>';
  }

  function cardHtml(s) {
    var cover = s.coverUrl
      ? '<img src="' + esc(s.coverUrl) + '" alt="' + esc(s.title) + '" loading="lazy">'
      : '<span>♪</span>';

    return '' +
      '<div class="lib-card" data-id="' + s.id + '" title="点击播放">' +
        '<div class="lib-cover">' + cover +
          '<div class="play-veil">' + playIcon() + '</div>' +
        '</div>' +
        '<div class="lib-title">' + esc(s.title) + '</div>' +
        '<div class="lib-artist">' + esc(s.artist || '未知歌手') + '</div>' +
        '<div class="lib-meta">' +
          window.avatarHtml(s.uploaderName, null, 18) +
          '<span>' + esc(s.uploaderName || '佚名') + ' 推荐</span>' +
        '</div>' +
      '</div>';
  }

  async function loadSongs() {
    var grid = document.getElementById('song-grid');
    var res = await API.get('/song/list');

    if (!res.ok) {
      grid.innerHTML = '<div class="empty">' + esc(res.msg) + '</div>';
      return;
    }

    songs = res.data || [];
    document.getElementById('stat-total').textContent = songs.length;

    if (!songs.length) {
      grid.innerHTML = '<div class="empty glass" style="grid-column:1/-1"><div class="big">♪</div>' +
        '曲库还是空的<br><span style="font-size:12.5px">登录后可以在后台推荐自己的歌</span></div>';
      document.getElementById('list-count').textContent = '';
      return;
    }

    document.getElementById('list-count').textContent = '共 ' + songs.length + ' 首';
    grid.innerHTML = songs.map(cardHtml).join('');
    syncCards();
  }

  /* ---------------- 跟随全站播放器高亮 ---------------- */
  function syncCards() {
    var cur = window.Player.current();
    var playing = window.Player.isPlaying();

    document.querySelectorAll('.lib-card').forEach(function (card) {
      var isCurrent = cur && String(card.dataset.id) === String(cur.id);
      card.classList.toggle('playing', !!(isCurrent && playing));
      card.classList.toggle('current', !!isCurrent);
      var veil = card.querySelector('.play-veil');
      if (veil) veil.innerHTML = (isCurrent && playing) ? eqIcon() : playIcon();
    });

    // 今日推荐那张卡片上的按钮文字也跟着变
    var btn = document.querySelector('#today-block [data-play]');
    if (btn && todaySong) {
      var on = cur && String(cur.id) === String(todaySong.id);
      btn.textContent = on ? (playing ? '暂停' : '继续播放') : '播放这首';
    }
  }

  /* ---------------- 交互 ---------------- */
  document.getElementById('song-grid').addEventListener('click', function (e) {
    var card = e.target.closest('.lib-card');
    if (!card) return;
    var song = songs.filter(function (s) { return String(s.id) === String(card.dataset.id); })[0];
    if (!song) return;

    // 点正在播的那首 = 暂停 / 继续
    if (window.Player.isCurrent(song.id)) {
      window.Player.toggle();
    } else {
      window.Player.playSong(song);
    }
  });

  document.getElementById('today-block').addEventListener('click', function (e) {
    var btn = e.target.closest('[data-play]');
    if (!btn || !todaySong) return;
    if (window.Player.isCurrent(todaySong.id)) {
      window.Player.toggle();
    } else {
      window.Player.playSong(todaySong);
    }
  });

  /* ---------------- 启动 ---------------- */
  applySite();
  loadNav();
  loadSongs();
  loadToday();

  // 软导航换页会重新执行本脚本，取消函数要登记，避免监听重复叠加
  window.__pageCleanups = window.__pageCleanups || [];
  window.__pageCleanups.push(
    window.Player.on('change', syncCards),
    window.Player.on('play', syncCards),
    window.Player.on('pause', syncCards)
  );
})();
