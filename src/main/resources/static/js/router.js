/**
 * 站内软导航（pjax）—— 让音乐真正不中断。
 *
 * 为什么需要它：整页加载后新文档没有「用户手势」，浏览器的自动播放策略会
 * 拒绝 play()，所以"存进度再恢复"只能做到恢复歌名和播放位置，声音会停一下。
 * 拦截站内链接、用 fetch 换掉 body 内容，播放器所在的 JS 和 audio 元素就一直活着，
 * 音乐完全不断。
 *
 * 约定：
 *   - <head> 里的脚本视为「全局脚本」，只加载一次（site / theme / api / globalplayer）
 *   - <body> 里的脚本视为「页面脚本」，每次换页重新加载执行（index / post / songs / admin）
 *   - 页面脚本如果要挂 window / document / Player 上的监听，把取消函数
 *     push 进 window.__pageCleanups，换页时会被调用，避免重复叠加
 *   - 标了 data-persist 的元素在换页时保留（播放条就是靠这个活下来的）
 */
(function () {
  if (window.__routerInstalled) return;
  window.__routerInstalled = true;

  /**
   * 已经加载过的「全局脚本」（head 里的）。
   * 注意不能简单假设各页 head 里的脚本完全一样 —— 比如 markdown.js 只在
   * 文章页和后台的 head 里，从首页切过去时必须补加载，否则页面脚本会报
   * "Cannot read properties of undefined"。
   */
  var loadedGlobal = {};
  document.querySelectorAll('head script[src]').forEach(function (s) {
    loadedGlobal[s.getAttribute('src')] = true;
  });

  /**
   * 由 JS 在运行时加到 <body> 上的类名，换页时必须保留。
   *
   * 坑在这里：新页面的 HTML 里没有这些类（它们是脚本加的），
   * 直接 `body.className = 新页面的className` 会把它们抹掉，
   * 于是背景图会消失、播放条会盖住页脚。
   *   has-bg      theme.js 加的，开关主题背景图
   *   has-player  globalplayer.js 加的，给播放条让出底部空间
   */
  var RUNTIME_BODY_CLASSES = ['has-bg', 'has-player'];

  function runCleanups() {
    var list = window.__pageCleanups || [];
    list.forEach(function (fn) {
      try { fn(); } catch (e) { /* 清理失败不影响换页 */ }
    });
    window.__pageCleanups = [];
  }

  function loadScript(src) {
    return new Promise(function (resolve) {
      var s = document.createElement('script');
      s.src = src;
      s.onload = resolve;
      s.onerror = resolve;   // 单个脚本失败不能卡住整次导航
      document.head.appendChild(s);
    });
  }

  function scriptsOf(doc, where) {
    return Array.prototype.map.call(doc.querySelectorAll(where + ' script[src]'), function (s) {
      return s.getAttribute('src');
    });
  }

  async function navigate(url, push) {
    var res = await fetch(url, { credentials: 'same-origin' });
    if (!res.ok) throw new Error('HTTP ' + res.status);

    var html = await res.text();
    var doc = new DOMParser().parseFromString(html, 'text/html');
    var headScripts = scriptsOf(doc, 'head');
    var pageScripts = scriptsOf(doc, 'body');

    runCleanups();

    // 记下运行时类名，换完 className 再补回来
    var keepClasses = RUNTIME_BODY_CLASSES.filter(function (c) {
      return document.body.classList.contains(c);
    });

    // 保留需要跨页存活的元素（播放条）
    var keep = Array.prototype.slice.call(document.querySelectorAll('[data-persist]'));
    keep.forEach(function (el) { el.remove(); });

    document.title = doc.title;
    document.body.className = doc.body.className;
    document.body.innerHTML = doc.body.innerHTML;

    keep.forEach(function (el) { document.body.appendChild(el); });
    keepClasses.forEach(function (c) { document.body.classList.add(c); });

    // 1) 先补齐这一页需要、但之前没加载过的全局脚本（只加载一次）
    for (var i = 0; i < headScripts.length; i++) {
      if (!loadedGlobal[headScripts[i]]) {
        loadedGlobal[headScripts[i]] = true;
        await loadScript(headScripts[i]);
      }
    }

    // 2) 再执行这一页独有的页面脚本（每次换页都要重新执行一遍）
    for (var j = 0; j < pageScripts.length; j++) {
      await loadScript(pageScripts[j]);
    }

    if (push) history.pushState({ pjax: true }, '', url);
    window.scrollTo(0, 0);
  }

  /** 这个链接能不能走软导航 */
  function handled(e) {
    if (e.defaultPrevented || e.button !== 0) return null;
    if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return null;

    var a = e.target.closest && e.target.closest('a');
    if (!a) return null;
    if (a.target && a.target !== '_self') return null;
    if (a.hasAttribute('download')) return null;

    var href = a.getAttribute('href') || '';
    if (!href || href.charAt(0) === '#') return null;

    var url;
    try { url = new URL(a.href, location.href); } catch (err) { return null; }
    if (url.origin !== location.origin) return null;
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;

    // 目标就是当前页（只是 hash 或完全一样）→ 交给浏览器默认行为
    if (url.pathname === location.pathname && url.search === location.search) return null;

    // 只处理指向页面（.html 或目录）的链接，其它交给浏览器
    if (!/\.html?$/.test(url.pathname) && url.pathname !== '/') return null;

    return url;
  }

  document.addEventListener('click', function (e) {
    var url = handled(e);
    if (!url) return;
    e.preventDefault();
    navigate(url.pathname + url.search, true).catch(function () {
      // 软导航失败就老老实实整页跳转，别把用户卡住
      location.href = url.href;
    });
  });

  window.addEventListener('popstate', function () {
    navigate(location.pathname + location.search, false).catch(function () {
      location.reload();
    });
  });

  window.__router = { navigate: navigate, runCleanups: runCleanups };
})();
