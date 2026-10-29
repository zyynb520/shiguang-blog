/** 文章详情页：正文 + 浏览量上报 */
(function () {
  var esc = window.escapeHtml;
  var postEl = document.getElementById('post');
  var currentUser = null;
  var articleId = null;

  function applySite() {
    var s = window.SITE || {};
    document.querySelectorAll('[data-site-name]').forEach(function (el) {
      el.textContent = s.name || '';
    });
  }

  function fail(message, hint) {
    postEl.innerHTML =
      '<div class="empty"><div class="big">✕</div>' + esc(message) +
      (hint ? '<br><span style="font-size:12.5px">' + esc(hint) + '</span>' : '') +
      '</div>';
  }

  /* ---------------- 顶栏登录状态 ---------------- */
  async function loadNav() {
    var host = document.getElementById('nav-user');
    var res = await API.get('/user/me');
    currentUser = (res.ok && res.data) ? res.data : null;

    if (currentUser) {
      var name = currentUser.nickname || currentUser.username;
      // 登录后给一个回后台的入口：「后台」链接只在已登录时渲染，公开访客看不到
      host.innerHTML = '<a href="/admin.html">后台</a><span class="nav-user">' +
        window.avatarHtml(name, currentUser.avatar, 26) + esc(name) + '</span>';
    } else {
      // 只有站长（或登录白名单内的 IP）会拿到 true；其余访客这里一个入口都不留
      host.innerHTML = (await window.loadLoginEntryVisible())
        ? '<a href="/admin.html">登录</a>'
        : '';
    }
  }

  /* ---------------- 正文 ---------------- */
  async function loadPost() {
    var id = new URLSearchParams(location.search).get('id');
    if (!id || !/^\d+$/.test(id)) {
      fail('没有指定文章编号', '请从首页点进文章');
      return;
    }
    articleId = Number(id);

    var res = await API.get('/article/detail/' + encodeURIComponent(id));
    if (!res.ok || !res.data) {
      fail(res.msg || '文章不存在', '它可能已经被删除了');
      return;
    }

    var a = res.data;
    document.title = a.title + ' · ' + ((window.SITE && window.SITE.name) || '博客');

    var authorBlock =
      '<span class="author-line">' + window.avatarHtml(a.authorName, a.authorAvatar, 22) +
        esc(a.authorName || '已注销') +
        (a.authorRole === 'admin' ? '<span class="comment-tag">站长</span>' : '') +
      '</span>';

    postEl.innerHTML = '' +
      '<h1>' + esc(a.title) + '</h1>' +
      '<div class="meta">' +
        authorBlock +
        '<span>' +
          '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 2a1 1 0 0 1 1 1v1h8V3a1 1 0 1 1 2 0v1h1a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h1V3a1 1 0 0 1 1-1zm12 8H5v9h14v-9z"/></svg>' +
          esc(window.formatDate(a.createTime)) +
        '</span>' +
        '<span>' +
          '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 5C6.5 5 2.7 9.1 1.5 12c1.2 2.9 5 7 10.5 7s9.3-4.1 10.5-7C21.3 9.1 17.5 5 12 5zm0 11a4 4 0 1 1 0-8 4 4 0 0 1 0 8zm0-2a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"/></svg>' +
          '<span id="view-count">' + (a.viewCount || 0) + '</span> 次浏览' +
        '</span>' +
      '</div>' +
      '<div class="md">' + window.markdown.render(a.content) + '</div>';

    // 正文渲染完成后才上报浏览。
    // 这样爬虫、预取、加载失败的访问都不会计入；服务端还会做
    // 「同一浏览器 24 小时、同一 IP 10 分钟」的重复过滤，返回最新浏览数。
    API.postParams('/article/' + articleId + '/view').then(function (r) {
      if (r.ok && typeof r.data === 'number') {
        var el = document.getElementById('view-count');
        if (el) el.textContent = r.data;
      }
    });
  }

  /* ---------------- 启动 ---------------- */
  applySite();
  (async function () {
    await loadNav();
    await loadPost();
  })();
})();
