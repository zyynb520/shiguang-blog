/** 后台页：登录 / 写文章 / 曲库管理 */
(function () {
  var esc = window.escapeHtml;
  var viewLogin = document.getElementById('view-login');
  var viewAdmin = document.getElementById('view-admin');

  /* ---------------- 站点配置 ---------------- */
  function applySite() {
    var s = window.SITE || {};
    if (s.name) {
      document.title = '后台 · ' + s.name;
      document.querySelectorAll('[data-site-name]').forEach(function (el) { el.textContent = s.name; });
    }
  }

  /* ---------------- 登录状态 ---------------- */
  var currentUser = null;

  function showNav(user) {
    var host = document.getElementById('nav-user');
    if (!user) {
      host.innerHTML = '';
      return;
    }
    var name = user.nickname || user.username;
    host.innerHTML =
      '<span class="nav-user">' + window.avatarHtml(name, user.avatar, 26) + esc(name) + '</span>' +
      '<button type="button" id="logout-btn">退出</button>';

    document.getElementById('logout-btn').addEventListener('click', async function () {
      await API.postParams('/user/logout', {});
      window.toast('已退出登录');
      setTimeout(function () { location.reload(); }, 500);
    });
  }

  async function refresh() {
    var res = await API.get('/user/me');
    var user = (res.ok && res.data) ? res.data : null;
    currentUser = user;

    showNav(user);
    viewLogin.hidden = !!user;
    viewAdmin.hidden = !user;

    // 「站点设置」是站级配置，只有管理员能改，普通用户看不到这个标签
    var isAdmin = !!user && user.role === 'admin';
    document.querySelectorAll('[data-admin-only]').forEach(function (btn) {
      btn.hidden = !isAdmin;
    });
    var activeAdminTab = document.querySelector('.tabs button.active[data-admin-only]');
    if (activeAdminTab) {
      switchTab('write');
    }

    if (user) {
      loadPosts();
      loadSongs();
      loadProfile();
      if (isAdmin) {
        loadSiteSetting();
        loadUsers();
      }
    }
  }

  document.getElementById('login-form').addEventListener('submit', async function (e) {
    e.preventDefault();
    var btn = document.getElementById('login-btn');
    btn.disabled = true;
    btn.textContent = '登录中…';

    var res = await API.postJSON('/user/login', {
      username: document.getElementById('login-username').value.trim(),
      password: document.getElementById('login-password').value
    });

    btn.disabled = false;
    btn.textContent = '登录';

    if (res.ok) {
      window.toast('登录成功');
      document.getElementById('login-password').value = '';
      refresh();
    } else {
      window.toast(res.msg, 'err');
    }
  });

  /* ---------------- 我的资料 ---------------- */
  async function loadProfile() {
    if (!currentUser) return;
    var name = currentUser.nickname || currentUser.username;
    document.getElementById('profile-avatar').innerHTML =
      window.avatarHtml(name, currentUser.avatar, 84);
    document.getElementById('nickname-input').value = currentUser.nickname || '';

    var res = await API.get('/user/limits');
    if (res.ok && res.data) {
      var d = res.data;
      document.getElementById('profile-quota').innerHTML =
        '你已发布 <b>' + d.myArticles + '/' + d.maxArticles + '</b> 篇文章，' +
        '上传 <b>' + d.mySongs + '/' + d.maxSongs + '</b> 首歌' +
        (d.isAdmin ? '　·　你是本站管理员' : '');
      document.getElementById('article-quota').textContent =
        '已发 ' + d.myArticles + '/' + d.maxArticles + ' 篇';
      document.getElementById('song-quota').textContent =
        '已传 ' + d.mySongs + '/' + d.maxSongs + ' 首';
    }
  }

  document.getElementById('avatar-file').addEventListener('change', async function () {
    if (!this.files.length) return;
    var fd = new FormData();
    fd.append('avatar', this.files[0]);
    var res = await API.postForm('/user/avatar', fd);
    this.value = '';
    if (res.ok) {
      window.toast('头像已更新');
      await refresh();
    } else {
      window.toast(res.msg, 'err');
    }
  });

  document.getElementById('nickname-save').addEventListener('click', async function () {
    var name = document.getElementById('nickname-input').value.trim();
    if (!name) { window.toast('昵称不能为空', 'err'); return; }
    var res = await API.postParams('/user/nickname', { nickname: name });
    if (res.ok) {
      window.toast(res.msg || '昵称已保存');
      await refresh();
    } else {
      window.toast(res.msg, 'err');
    }
  });

  /* ---------------- 标签页切换 ---------------- */
  function switchTab(tab) {
    document.querySelectorAll('.tabs button').forEach(function (b) {
      b.classList.toggle('active', b.dataset.tab === tab);
    });
    document.querySelectorAll('.panel').forEach(function (p) {
      p.classList.toggle('active', p.id === 'panel-' + tab);
    });
  }

  document.querySelectorAll('.tabs button').forEach(function (btn) {
    btn.addEventListener('click', function () { switchTab(btn.dataset.tab); });
  });

  /* ---------------- 写文章 / 编辑文章 ---------------- */
  var contentEl = document.getElementById('art-content');
  var previewEl = document.getElementById('art-preview');

  /** null 表示新建；有值表示正在编辑这篇文章 */
  var editingId = null;
  /** 文章列表缓存：编辑时按 id 直接取回内容，省掉一次请求（浏览量已与读取解耦，不再受影响） */
  var articleCache = [];

  function updatePreview() {
    var text = contentEl.value;
    previewEl.innerHTML = text.trim()
      ? window.markdown.render(text)
      : '<p style="color:var(--text-faint)">左边写，这里实时预览</p>';
  }
  contentEl.addEventListener('input', updatePreview);
  updatePreview();

  /* 插入图片：上传后在光标处插入 Markdown 图片语法 */
  document.getElementById('insert-image-btn').addEventListener('click', function () {
    document.getElementById('image-file').click();
  });

  document.getElementById('image-file').addEventListener('change', async function () {
    if (!this.files.length) return;
    var btn = document.getElementById('insert-image-btn');
    var original = btn.textContent;
    btn.disabled = true;
    btn.textContent = '上传中…';

    var fd = new FormData();
    fd.append('image', this.files[0]);
    var res = await API.postForm('/upload/image', fd);

    btn.disabled = false;
    btn.textContent = original;
    this.value = '';

    if (!res.ok || !res.data) {
      window.toast(res.msg || '上传失败', 'err');
      return;
    }

    // 插到光标所在位置；没聚焦过就追加到末尾
    var start = contentEl.selectionStart;
    var end = contentEl.selectionEnd;
    if (typeof start !== 'number') {
      start = end = contentEl.value.length;
    }
    var snippet = res.data.markdown + '\n';
    contentEl.value = contentEl.value.slice(0, start) + snippet + contentEl.value.slice(end);
    contentEl.selectionStart = contentEl.selectionEnd = start + snippet.length;
    contentEl.focus();
    updatePreview();
    window.toast('图片已插入正文');
  });

  /** 传入文章进入编辑模式，传 null 回到新建模式 */
  function setEditing(article) {
    editingId = article ? article.id : null;

    document.getElementById('art-title').value = article ? (article.title || '') : '';
    document.getElementById('art-summary').value = article ? (article.summary || '') : '';
    contentEl.value = article ? (article.content || '') : '';

    var banner = document.getElementById('edit-banner');
    banner.hidden = !article;
    if (article) {
      document.getElementById('edit-title').textContent = article.title || '';
    }

    document.getElementById('publish-btn').textContent = article ? '更新文章' : '发布文章';
    document.getElementById('publish-hint').innerHTML = '';
    updatePreview();
  }

  document.getElementById('cancel-edit-btn').addEventListener('click', function () {
    if (contentEl.value.trim() && !confirm('取消编辑？当前改动不会保存。')) return;
    setEditing(null);
    window.toast('已取消编辑');
  });

  document.getElementById('publish-btn').addEventListener('click', async function () {
    var title = document.getElementById('art-title').value.trim();
    var summary = document.getElementById('art-summary').value.trim();
    var content = contentEl.value;
    var isEdit = editingId !== null;

    if (!title) { window.toast('标题不能为空', 'err'); return; }
    if (!content.trim()) { window.toast('正文不能为空', 'err'); return; }

    var btn = this;
    btn.disabled = true;
    btn.textContent = isEdit ? '更新中…' : '发布中…';

    var res = isEdit
      ? await API.postJSON('/article/update', {
          id: editingId, title: title, summary: summary || null, content: content
        })
      : await API.postJSON('/article/publish', {
          title: title, summary: summary || null, content: content
        });

    btn.disabled = false;

    if (res.ok) {
      window.toast(res.msg || (isEdit ? '已更新' : '发布成功'));
      if (isEdit) {
        setEditing(null);
        loadPosts();
        switchTab('posts');
      } else {
        setEditing(null);
        document.getElementById('publish-hint').innerHTML =
          '<a href="/" style="color:var(--accent)">发布成功，去首页看看 →</a>';
      }
    } else {
      // 失败时保留用户填的内容，只把按钮文字改回去
      btn.textContent = isEdit ? '更新文章' : '发布文章';
      window.toast(res.msg, 'err');
    }
  });

  /* ---------------- 上传歌曲 ---------------- */
  document.getElementById('upload-form').addEventListener('submit', async function (e) {
    e.preventDefault();

    var title = document.getElementById('song-title').value.trim();
    var audioInput = document.getElementById('song-audio');
    var coverInput = document.getElementById('song-cover');

    if (!title) { window.toast('歌名不能为空', 'err'); return; }
    if (!audioInput.files.length) { window.toast('请选择音频文件', 'err'); return; }

    var fd = new FormData();
    fd.append('title', title);
    fd.append('artist', document.getElementById('song-artist').value.trim());
    fd.append('lyric', document.getElementById('song-lyric').value);
    fd.append('audio', audioInput.files[0]);
    if (coverInput.files.length) fd.append('cover', coverInput.files[0]);

    var btn = document.getElementById('upload-btn');
    btn.disabled = true;
    btn.textContent = '上传中…（大文件请稍等）';

    var res = await API.postForm('/song/upload', fd);

    btn.disabled = false;
    btn.textContent = '上传';

    if (res.ok) {
      window.toast('上传成功');
      e.target.reset();
      loadSongs();
    } else {
      window.toast(res.msg, 'err');
    }
  });

  /* ---------------- 曲库列表 ---------------- */
  async function loadSongs() {
    var box = document.getElementById('song-list');
    var countEl = document.getElementById('song-count');
    var res = await API.get('/song/list');

    if (!res.ok) {
      box.innerHTML = '<div class="empty">' + esc(res.msg) + '</div>';
      return;
    }

    var list = res.data || [];
    countEl.textContent = list.length ? '共 ' + list.length + ' 首' : '';

    if (!list.length) {
      box.innerHTML = '<div class="empty glass"><div class="big">♪</div>还没有歌曲，上传第一首吧</div>';
      return;
    }

    var today = window.todayStr();

    box.innerHTML = list.map(function (s) {
      var thumb = s.coverUrl
        ? '<img class="song-thumb" src="' + esc(s.coverUrl) + '" alt="">'
        : '<div class="song-thumb">♪</div>';

      var tag = s.recommendDate === today ? '<span class="tag-today">今日推荐</span>' : '';
      var extra = s.recommendDate ? ' · 已指定 ' + esc(s.recommendDate) : '';
      var isAdmin = !!currentUser && currentUser.role === 'admin';
      // 管理员能删所有人的；普通用户只能删自己传的
      var canManage = currentUser && (isAdmin || currentUser.id === s.userId);

      return '' +
        '<div class="song-row" data-id="' + s.id + '">' +
          thumb +
          '<div class="info">' +
            '<div class="t">' + esc(s.title) + tag + '</div>' +
            '<div class="a">' + esc(s.artist || '未知歌手') +
              (s.uploaderName ? ' · 由 ' + esc(s.uploaderName) + ' 推荐' : '') +
              ' · 上传于 ' + esc(window.formatDate(s.createTime)) + extra +
            '</div>' +
          '</div>' +
          '<div class="actions">' +
            // 「今日推荐」是站级位置，只有管理员能指定
            (isAdmin
              ? '<input type="date" class="input" style="width:150px;padding:7px 10px;font-size:13px" ' +
                  'value="' + esc(s.recommendDate || today) + '">' +
                '<button type="button" class="btn btn-sm" data-act="recommend">设为推荐</button>'
              : '') +
            (canManage
              ? '<button type="button" class="btn btn-sm btn-danger" data-act="delete">删除</button>'
              : '') +
          '</div>' +
        '</div>';
    }).join('');
  }

  // 事件委托：曲库列表里的按钮
  document.getElementById('song-list').addEventListener('click', async function (e) {
    var btn = e.target.closest('button[data-act]');
    if (!btn) return;

    var row = btn.closest('.song-row');
    var id = row.dataset.id;

    if (btn.dataset.act === 'delete') {
      if (!confirm('确定删除这首歌吗？\n\n音频和封面文件也会一并从服务器删除，无法恢复。')) return;
      var del = await API.postParams('/song/delete', { id: id });
      if (del.ok) { window.toast('已删除'); loadSongs(); } else { window.toast(del.msg, 'err'); }
      return;
    }

    var date = row.querySelector('input[type="date"]').value;
    if (!date) { window.toast('请选择日期', 'err'); return; }

    var res = await API.postParams('/song/recommend', { songId: id, date: date });
    if (res.ok) {
      window.toast(res.msg || '已设置');
      loadSongs();
    } else {
      window.toast(res.msg, 'err');
    }
  });

  /* ---------------- 文章管理 ---------------- */
  var DOC_ICON = '<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">' +
    '<path d="M6 2h8l6 6v14H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zm7 1.5V9h5.5L13 3.5zM8 12h8v2H8zm0 4h6v2H8z"/></svg>';

  async function loadPosts() {
    var box = $id('post-list');
    var countEl = $id('post-count');
    var res = await API.get('/article/list');

    if (!res.ok) {
      box.innerHTML = '<div class="empty">' + esc(res.msg) + '</div>';
      return;
    }

    var list = res.data || [];
    articleCache = list;
    countEl.textContent = list.length ? '共 ' + list.length + ' 篇' : '';

    if (!list.length) {
      box.innerHTML = '<div class="empty glass"><div class="big">✎</div>还没有文章' +
        '<br><span style="font-size:12.5px">去「写文章」发布第一篇吧</span></div>';
      return;
    }

    box.innerHTML = list.map(function (a) {
      var summary = (a.summary || '').trim();
      if (summary.length > 36) summary = summary.slice(0, 36) + '…';
      // 管理员能管所有人的；普通用户只能管自己的
      var canManage = currentUser &&
        (currentUser.role === 'admin' || currentUser.id === a.userId);

      return '' +
        '<div class="song-row" data-id="' + a.id + '">' +
          '<div class="song-thumb" style="border-radius:12px">' + DOC_ICON + '</div>' +
          '<div class="info">' +
            '<div class="t">' + esc(a.title) + '</div>' +
            '<div class="a">' +
              esc(a.authorName || '已注销') +
              (a.authorRole === 'admin' ? ' · 站长' : '') +
              ' · ' + esc(window.formatDate(a.createTime)) +
              ' · ' + (a.viewCount || 0) + ' 次浏览' +
              (summary ? ' · ' + esc(summary) : '') +
            '</div>' +
          '</div>' +
          '<div class="actions">' +
            '<a class="btn btn-sm" href="/post.html?id=' + encodeURIComponent(a.id) +
              '" target="_blank" rel="noopener">查看</a>' +
            (canManage
              ? '<button type="button" class="btn btn-sm" data-act="edit-post">编辑</button>' +
                '<button type="button" class="btn btn-sm btn-danger" data-act="delete-post">删除</button>'
              : '') +
          '</div>' +
        '</div>';
    }).join('');
  }

  $id('post-list').addEventListener('click', async function (e) {
    var row = e.target.closest('.song-row');
    if (!row) return;
    var id = Number(row.dataset.id);

    // ---- 编辑：回填编辑器并跳到「写文章」页 ----
    if (e.target.closest('button[data-act="edit-post"]')) {
      var article = articleCache.filter(function (a) { return a.id === id; })[0];
      if (!article) {
        window.toast('文章数据已过期，正在刷新列表', 'err');
        loadPosts();
        return;
      }
      setEditing(article);
      switchTab('write');
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }

    // ---- 删除 ----
    var btn = e.target.closest('button[data-act="delete-post"]');
    if (!btn) return;

    var title = row.querySelector('.info .t').textContent;
    if (!confirm('确定删除《' + title + '》吗？\n\n' +
                 '数据库记录和这篇文章的评论都会被删除，无法恢复。')) return;

    btn.disabled = true;
    var res = await API.postParams('/article/delete', { id: id });
    btn.disabled = false;

    if (res.ok) {
      // 如果删的正是当前正在编辑的那篇，退出编辑模式
      if (editingId === id) setEditing(null);
      window.toast(res.msg || '已删除');
      loadPosts();
    } else {
      window.toast(res.msg, 'err');
    }
  });

  /* ---------------- 用户管理（仅站长） ---------------- */
  async function loadUsers() {
    var box = $id('user-list');
    var countEl = $id('user-count');
    var res = await API.get('/admin/users');

    if (!res.ok) {
      box.innerHTML = '<div class="empty">' + esc(res.msg) + '</div>';
      return;
    }

    var users = (res.data && res.data.users) || [];
    countEl.textContent = '共 ' + users.length + ' 个账号';

    box.innerHTML = users.map(function (u) {
      var isMe = currentUser && currentUser.id === u.id;
      var isAdmin = u.role === 'admin';
      var name = u.nickname || u.username;

      return '' +
        '<div class="song-row" data-id="' + u.id + '">' +
          window.avatarHtml(name, u.avatar, 48) +
          '<div class="info">' +
            '<div class="t">' + esc(name) +
              (isAdmin ? '<span class="tag-today">站长</span>' : '') +
              (isMe ? '<span class="tag-me">这是你</span>' : '') +
            '</div>' +
            '<div class="a">@' + esc(u.username) +
              ' · 文章 ' + u.articles + ' · 歌曲 ' + u.songs +
              ' · 创建于 ' + esc(window.formatDate(u.createTime)) +
            '</div>' +
          '</div>' +
        '</div>';
    }).join('');
  }

  /* ---------------- 站点设置：站点图标 ---------------- */
  var logoFileEl = document.getElementById('logo-file');

  document.getElementById('logo-upload-btn').addEventListener('click', async function () {
    if (!logoFileEl.files.length) {
      window.toast('请先选一张图片', 'err');
      return;
    }
    var btn = this;
    btn.disabled = true;
    btn.textContent = '上传中…';

    var fd = new FormData();
    fd.append('image', logoFileEl.files[0]);
    var res = await API.postForm('/setting/logo', fd);

    btn.disabled = false;
    btn.textContent = '上传并应用';

    if (res.ok) {
      logoFileEl.value = '';
      // 图标是通过 :root 的 CSS 变量生效的，重拉一次设置就会套用
      if (window.Theme) window.Theme.reload();
      window.toast('图标已更新，顶栏和标签页都会变');
    } else {
      window.toast(res.msg, 'err');
    }
  });

  document.getElementById('logo-clear-btn').addEventListener('click', async function () {
    if (!confirm('确定恢复默认图标吗？')) return;
    var res = await API.postParams('/setting/clearLogo', {});
    if (res.ok) {
      if (window.Theme) window.Theme.reload();
      window.toast('已恢复默认图标');
    } else {
      window.toast(res.msg, 'err');
    }
  });

  /* ---------------- 站点设置：背景图 ---------------- */
  var bgImageUrl = '';
  var bgFileEl = document.getElementById('bg-file');
  var bgDimEl = document.getElementById('bg-dim');
  var bgDimValueEl = document.getElementById('bg-dim-value');
  var bgPreviewEl = document.getElementById('bg-preview');

  function paintPreview() {
    bgDimValueEl.textContent = bgDimEl.value + '%';
    bgPreviewEl.style.backgroundImage = bgImageUrl ? 'url("' + bgImageUrl + '")' : 'none';
    bgPreviewEl.style.setProperty('--preview-dim', (parseInt(bgDimEl.value, 10) / 100).toFixed(2));
  }

  async function loadSiteSetting() {
    var res = await API.get('/setting/site');
    if (!res.ok || !res.data) return;
    var d = res.data;

    // 背景
    bgImageUrl = d.bgImage || '';
    var dim = parseInt(d.bgDim, 10);
    if (isNaN(dim)) dim = 60;
    bgDimEl.value = dim;
    paintPreview();

    // 主题配色
    currentPreset = d.themePreset || 'sakura';
    setColors({ accent: d.accent, accent2: d.accent2, accent3: d.accent3 });
    renderPresets();
  }

  // 拖动时只更新预览，松手才提交，避免疯狂发请求
  bgDimEl.addEventListener('input', paintPreview);

  bgDimEl.addEventListener('change', async function () {
    var res = await API.postParams('/setting/dim', { value: bgDimEl.value });
    if (res.ok) {
      if (window.Theme) window.Theme.reload();
      window.toast(res.msg);
    } else {
      window.toast(res.msg, 'err');
    }
  });

  document.getElementById('bg-upload-btn').addEventListener('click', async function () {
    if (!bgFileEl.files.length) {
      window.toast('请先选一张图片', 'err');
      return;
    }
    var btn = this;
    btn.disabled = true;
    btn.textContent = '上传中…';

    var fd = new FormData();
    fd.append('image', bgFileEl.files[0]);
    var res = await API.postForm('/setting/bg', fd);
    // 顺手把当前滑块的值一起存下来
    await API.postParams('/setting/dim', { value: bgDimEl.value });

    btn.disabled = false;
    btn.textContent = '上传并应用';

    if (res.ok && res.data) {
      bgImageUrl = res.data.bgImage || '';
      bgFileEl.value = '';
      paintPreview();
      if (window.Theme) window.Theme.reload();
      window.toast('背景已更新，去首页看看效果');
    } else {
      window.toast(res.msg, 'err');
    }
  });

  document.getElementById('bg-clear-btn').addEventListener('click', async function () {
    if (!confirm('确定恢复默认的极光背景吗？')) return;
    var res = await API.postParams('/setting/clearBg', {});
    if (res.ok) {
      bgImageUrl = '';
      paintPreview();
      if (window.Theme) window.Theme.reload();
      window.toast('已恢复默认背景');
    } else {
      window.toast(res.msg, 'err');
    }
  });

  /* ---------------- 站点设置：主题配色 ---------------- */
  var PRESETS = [
    { id: 'sakura', name: '樱花粉', colors: ['#ff7ac6', '#8b5cf6', '#38bdf8'] },
    { id: 'ocean',  name: '深海蓝', colors: ['#38bdf8', '#6366f1', '#22d3ee'] },
    { id: 'forest', name: '森林绿', colors: ['#34d399', '#10b981', '#a3e635'] },
    { id: 'sunset', name: '落日橙', colors: ['#fb923c', '#f43f5e', '#fbbf24'] },
    { id: 'violet', name: '紫罗兰', colors: ['#a78bfa', '#7c3aed', '#f0abfc'] },
    { id: 'mint',   name: '薄荷青', colors: ['#2dd4bf', '#0ea5e9', '#a7f3d0'] }
  ];

  var themeFields = [
    { key: 'accent',  colorId: 'theme-accent',  hexId: 'theme-accent-hex' },
    { key: 'accent2', colorId: 'theme-accent2', hexId: 'theme-accent2-hex' },
    { key: 'accent3', colorId: 'theme-accent3', hexId: 'theme-accent3-hex' }
  ];

  var currentPreset = 'sakura';

  function $id(id) { return document.getElementById(id); }

  function renderPresets() {
    $id('preset-grid').innerHTML = PRESETS.map(function (p) {
      return '<button type="button" class="preset-item' + (p.id === currentPreset ? ' active' : '') +
        '" data-preset="' + p.id + '">' +
        '<span class="preset-swatch" style="background:linear-gradient(100deg,' + p.colors.join(',') + ')"></span>' +
        esc(p.name) +
        '</button>';
    }).join('');
  }

  function getColors() {
    return {
      accent: $id('theme-accent').value,
      accent2: $id('theme-accent2').value,
      accent3: $id('theme-accent3').value
    };
  }

  /** 只改 CSS 变量做实时预览，不落库 */
  function previewColors() {
    var c = getColors();
    if (window.Theme) window.Theme.applyColors(c.accent, c.accent2, c.accent3);
  }

  function setColors(colors) {
    themeFields.forEach(function (f) {
      var v = colors[f.key];
      if (!v) return;
      $id(f.colorId).value = v;
      $id(f.hexId).value = v;
    });
    previewColors();
  }

  themeFields.forEach(function (f) {
    $id(f.colorId).addEventListener('input', function () {
      $id(f.hexId).value = this.value;
      previewColors();
      markCustom();
    });

    $id(f.hexId).addEventListener('input', function () {
      var m = /^#?([0-9a-f]{6})$/i.exec(this.value.trim());
      if (!m) return;
      $id(f.colorId).value = '#' + m[1].toLowerCase();
      previewColors();
      markCustom();
    });
  });

  function markCustom() {
    currentPreset = '';
    renderPresets();
  }

  $id('preset-grid').addEventListener('click', function (e) {
    var btn = e.target.closest('[data-preset]');
    if (!btn) return;
    var p = PRESETS.filter(function (x) { return x.id === btn.dataset.preset; })[0];
    if (!p) return;
    currentPreset = p.id;
    setColors({ accent: p.colors[0], accent2: p.colors[1], accent3: p.colors[2] });
    renderPresets();
  });

  $id('theme-save-btn').addEventListener('click', async function () {
    var c = getColors();
    var btn = this;
    btn.disabled = true;
    btn.textContent = '保存中…';
    var res = await API.postParams('/setting/theme', {
      accent: c.accent,
      accent2: c.accent2,
      accent3: c.accent3,
      preset: currentPreset || 'custom'
    });
    btn.disabled = false;
    btn.textContent = '保存配色';
    if (res.ok) {
      if (window.Theme) window.Theme.reload();
      window.toast('配色已保存，全站生效');
    } else {
      window.toast(res.msg, 'err');
    }
  });

  $id('theme-extract-btn').addEventListener('click', async function () {
    var btn = this;
    btn.disabled = true;
    btn.textContent = '提取中…';
    var res = await API.postParams('/setting/theme/extract', {});
    btn.disabled = false;
    btn.textContent = '从背景图自动提取';
    if (res.ok && res.data) {
      currentPreset = 'fromImage';
      setColors(res.data);
      renderPresets();
      window.toast('已按背景图配色，满意就点「保存配色」');
    } else {
      window.toast(res.msg, 'err');
    }
  });

  $id('theme-reset-btn').addEventListener('click', async function () {
    if (!confirm('确定恢复默认配色吗？')) return;
    var res = await API.postParams('/setting/theme/reset', {});
    if (res.ok && res.data) {
      currentPreset = 'sakura';
      setColors(res.data);
      renderPresets();
      window.toast('已恢复默认配色');
    } else {
      window.toast(res.msg, 'err');
    }
  });

  renderPresets();

  /* ---------------- 启动 ---------------- */
  applySite();
  refresh();
})();
