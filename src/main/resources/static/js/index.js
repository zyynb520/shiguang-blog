/** 首页：文章列表 + 每日推歌 */
(function () {
  var esc = window.escapeHtml;
  /** 首页卡片上展示的那首「今日推歌」，用来跟随全站播放器的状态 */
  var currentSong = null;

  /* ---------------- 站点配置 ---------------- */
  function applySite() {
    var s = window.SITE || {};
    if (s.name) {
      document.title = s.name;
      document.querySelectorAll('[data-site-name]').forEach(function (el) {
        el.textContent = s.name;
      });
    }
    var sub = document.querySelector('[data-site-subtitle]');
    if (sub) sub.textContent = s.subtitle || '';
  }

  /* ---------------- 顶栏登录状态 ---------------- */
  async function loadNav() {
    var host = document.getElementById('nav-user');
    var res = await API.get('/user/me');
    if (res.ok && res.data) {
      var name = res.data.nickname || res.data.username;
      // 登录后给一个回后台的入口：「后台」链接只在已登录时渲染，公开访客看不到
      host.innerHTML = '<a href="/admin.html">后台</a><span class="nav-user">' +
        window.avatarHtml(name, res.data.avatar, 26) + esc(name) + '</span>';
    } else {
      // 只读模式下顶栏不留任何入口按钮；开放登录后再按注册开关决定是否显示「注册」
      var cap = await window.loadSiteCapabilities();
      host.innerHTML = cap.loginEnabled
        ? '<a href="/admin.html">登录</a>' +
          (cap.registrationEnabled ? '<a href="/admin.html#register">注册</a>' : '')
        : '';
    }
  }

  /* ---------------- 文章列表 ---------------- */
  function excerptOf(article) {
    if (article.summary && article.summary.trim()) return article.summary.trim();
    var text = String(article.content || '')
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
      .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
      .replace(/[#>*`_~]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    return text.length > 92 ? text.slice(0, 92) + '…' : text;
  }

  function articleCard(a) {
    return '' +
      '<a class="article-card" href="/post.html?id=' + encodeURIComponent(a.id) + '">' +
        '<h3>' + esc(a.title) + '</h3>' +
        '<p class="excerpt">' + esc(excerptOf(a)) + '</p>' +
        '<div class="meta">' +
          '<span class="author-line">' +
            window.avatarHtml(a.authorName, a.authorAvatar, 20) +
            esc(a.authorName || '已注销') +
            (a.authorRole === 'admin' ? '<span class="comment-tag">站长</span>' : '') +
          '</span>' +
          '<span>' +
            '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 2a1 1 0 0 1 1 1v1h8V3a1 1 0 1 1 2 0v1h1a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h1V3a1 1 0 0 1 1-1zm12 8H5v9h14v-9z"/></svg>' +
            esc(window.formatDate(a.createTime)) +
          '</span>' +
          '<span>' +
            '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 5C6.5 5 2.7 9.1 1.5 12c1.2 2.9 5 7 10.5 7s9.3-4.1 10.5-7C21.3 9.1 17.5 5 12 5zm0 11a4 4 0 1 1 0-8 4 4 0 0 1 0 8zm0-2a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"/></svg>' +
            (a.viewCount || 0) + ' 次浏览' +
          '</span>' +
        '</div>' +
      '</a>';
  }

  async function loadArticles() {
    var listEl = document.getElementById('article-list');
    var countEl = document.getElementById('article-count');
    var res = await API.get('/article/list');

    if (!res.ok) {
      listEl.innerHTML = '<div class="empty"><div class="big">⚠</div>' + esc(res.msg) + '</div>';
      return;
    }
    var list = res.data || [];
    document.getElementById('stat-articles').textContent = list.length;

    if (!list.length) {
      listEl.innerHTML = '<div class="empty glass"><div class="big">✎</div>还没有文章<br>' +
        '<span style="font-size:12.5px">去后台发布第一篇吧</span></div>';
      countEl.textContent = '';
      return;
    }
    countEl.textContent = '共 ' + list.length + ' 篇';
    listEl.innerHTML = list.map(articleCard).join('');
  }

  /* ---------------- 每日推歌 ---------------- */
  function renderSong(song, dateStr) {
    var body = document.getElementById('song-body');
    var dateEl = document.getElementById('song-date');
    if (dateEl) dateEl.textContent = dateStr.slice(5); // 只显示 MM-DD

    if (!song) {
      body.innerHTML = '<div class="empty"><div class="big">♪</div>曲库还是空的<br>' +
        '<span style="font-size:12.5px">去后台「曲库管理」上传第一首歌吧</span></div>';
      return;
    }

    var hasCover = !!song.coverUrl;
    var cover = hasCover
      ? '<img src="' + esc(song.coverUrl) + '" alt="' + esc(song.title) + '">'
      : '<span class="placeholder">♪</span>';

    // 注意：要拿 recommendDate 和"当前请求的日期"比对，而不是判断它是否为空。
    // 因为自动轮换选中的歌可能恰好带着另一天的手动指定日期。
    var badge = song.recommendDate === dateStr
      ? '<span class="badge manual">手动指定</span>'
      : '<span class="badge">按日期自动轮换</span>';

    body.innerHTML = '' +
      '<div class="vinyl-wrap"><div class="vinyl' + (hasCover ? ' has-cover' : '') + '" id="vinyl">' +
        cover +
      '</div></div>' +
      '<div class="song-info">' +
        '<div class="title">' + esc(song.title) + '</div>' +
        '<div class="artist">' + esc(song.artist || '未知歌手') + '</div>' +
        badge +
      '</div>' +
      '<div class="player">' +
        '<div class="player-row">' +
          '<button class="play-btn" id="play-btn" aria-label="播放 / 暂停">' +
            '<svg class="icon-play" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>' +
            '<svg class="icon-pause" viewBox="0 0 24 24"><path d="M6 5h4v14H6zm8 0h4v14h-4z"/></svg>' +
          '</button>' +
          '<span class="hint" style="margin:0">播放后可在底部播放条调进度，切页不会停</span>' +
        '</div>' +
      '</div>';

    currentSong = song;

    document.getElementById('play-btn').addEventListener('click', function () {
      if (window.Player.isCurrent(song.id)) {
        window.Player.toggle();
      } else {
        window.Player.playSong(song);
      }
    });

    syncSongCard();
  }

  /** 让卡片上的播放按钮和转动的唱片跟随全站播放器的状态 */
  function syncSongCard() {
    if (!currentSong) return;
    var btn = document.getElementById('play-btn');
    var vinyl = document.getElementById('vinyl');
    if (!btn) return;

    var isCurrent = window.Player.isCurrent(currentSong.id);
    var playing = isCurrent && window.Player.isPlaying();

    btn.classList.toggle('playing', playing);
    if (vinyl) vinyl.classList.toggle('playing', playing);
  }

  async function loadSong() {
    var res = await API.get('/song/today');
    if (!res.ok) {
      document.getElementById('song-body').innerHTML =
        '<div class="empty"><div class="big">⚠</div>' + esc(res.msg) + '</div>';
      return;
    }
    // 后端把 LocalDate 序列化成 yyyy-MM-dd，data 为空表示曲库还没有歌
    renderSong(res.data, window.todayStr());
  }

  async function loadSongCount() {
    var res = await API.get('/song/list');
    document.getElementById('stat-songs').textContent = res.ok ? (res.data || []).length : '–';
  }

  /* ---------------- 启动 ---------------- */
  applySite();
  loadNav();
  loadArticles();
  loadSong();
  loadSongCount();

  // 播放状态由全站播放器管理，卡片上的按钮跟着它变。
  // 软导航换页会重新执行本脚本，所以要把取消函数登记起来，否则监听会越叠越多。
  window.__pageCleanups = window.__pageCleanups || [];
  window.__pageCleanups.push(
    window.Player.on('change', syncSongCard),
    window.Player.on('play', syncSongCard),
    window.Player.on('pause', syncSongCard)
  );
})();
