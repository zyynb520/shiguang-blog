/** 文章详情页：正文 + 评论区 */
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
      host.innerHTML = '<span class="nav-user">' +
        window.avatarHtml(name, currentUser.avatar, 26) + esc(name) + '</span>';
    } else {
      // 站点关闭注册时，顶栏不该还留着「注册」入口
      host.innerHTML = '<a href="/admin.html">登录</a>' +
        (await window.loadRegistrationOpen() ? '<a href="/admin.html#register">注册</a>' : '');
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

  /* ---------------- 评论区 ---------------- */
  function commentHtml(c) {
    // 自己能删自己的；管理员能删所有人的
    var canDelete = currentUser && (currentUser.role === 'admin' || currentUser.id === c.userId);
    return '' +
      '<div class="comment" data-id="' + c.id + '">' +
        window.avatarHtml(c.authorName, c.authorAvatar, 40) +
        '<div class="comment-body">' +
          '<div class="comment-head">' +
            '<span class="comment-name">' + esc(c.authorName) + '</span>' +
            (c.authorRole === 'admin' ? '<span class="comment-tag">站长</span>' : '') +
            '<span class="comment-time">' + esc(window.formatDateTime(c.createTime)) + '</span>' +
            (canDelete ? '<button type="button" class="comment-del" data-act="del-comment">删除</button>' : '') +
          '</div>' +
          '<div class="comment-text">' + esc(c.content) + '</div>' +
        '</div>' +
      '</div>';
  }

  async function loadComments() {
    if (!articleId) return;
    document.getElementById('comments').hidden = false;

    var res = await API.get('/comment/list?articleId=' + articleId);
    // 接口返回的是 { enabled, comments }：enabled=false 表示评论功能已关闭
    var data = (res.ok && res.data) ? res.data : { enabled: false, comments: [] };
    var list = data.comments || [];
    var open = data.enabled !== false;

    document.getElementById('comment-count').textContent =
      (open && list.length) ? '共 ' + list.length + ' 条' : '';
    document.getElementById('comment-list').innerHTML = list.length
      ? list.map(commentHtml).join('')
      : '<div class="empty" style="padding:28px 12px">' +
        (open ? '还没有评论，来说第一句吧' : '评论功能已暂时关闭') + '</div>';

    // 登录了才能评论；功能关闭时整个输入区都不显示
    document.getElementById('comment-form').hidden = !open || !currentUser;
    document.getElementById('comment-login-tip').hidden = !open || !!currentUser;
    if (open && currentUser) {
      document.getElementById('comment-hint').innerHTML =
        '以 <b>' + esc(currentUser.nickname || currentUser.username) + '</b> 的身份评论';
    }
  }

  async function submitComment(e) {
    e.preventDefault();
    var input = document.getElementById('comment-input');
    var content = input.value.trim();
    if (!content) {
      window.toast('评论内容不能为空', 'err');
      return;
    }

    var btn = document.getElementById('comment-submit');
    btn.disabled = true;
    btn.textContent = '发表中…';

    var res = await API.postJSON('/comment/add', { articleId: articleId, content: content });

    btn.disabled = false;
    btn.textContent = '发表评论';

    if (res.ok) {
      input.value = '';
      window.toast('评论成功');
      loadComments();
    } else {
      window.toast(res.msg, 'err');
    }
  }

  document.getElementById('comment-form').addEventListener('submit', submitComment);

  document.getElementById('comment-list').addEventListener('click', async function (e) {
    var btn = e.target.closest('button[data-act="del-comment"]');
    if (!btn) return;
    if (!confirm('确定删除这条评论吗？')) return;

    var row = btn.closest('.comment');
    btn.disabled = true;
    var res = await API.postParams('/comment/delete', { id: row.dataset.id });
    btn.disabled = false;

    if (res.ok) {
      window.toast('已删除');
      loadComments();
    } else {
      window.toast(res.msg, 'err');
    }
  });

  /* ---------------- 启动 ---------------- */
  applySite();
  (async function () {
    await loadNav();     // 先确定登录状态，评论才知道能不能删
    await loadPost();
    await loadComments();
  })();
})();
