/**
 * 站点外观：拉取后台设置的背景图和主题配色并套用。
 * 三个页面都会加载它；拉不到就保持默认外观，不会报错。
 */
(function () {
  /**
   * 记住 HTML 里写死的默认 favicon（一段内联 SVG）。
   * 后台清空自定义图标时用它还原，省得在 JS 里再抄一份。
   */
  var defaultFavicon = (function () {
    var link = document.querySelector('link[rel="icon"]');
    return link ? link.getAttribute('href') : null;
  })();

  /** 只接受站内 /uploads/ 路径或 http(s) 链接，避免配置里被塞进奇怪的 URL */
  function safeUrl(url) {
    if (!url) return null;
    if (/^\/uploads\/[A-Za-z0-9_\-./]+$/.test(url)) return url;
    if (/^https?:\/\/[^\s"'<>]+$/i.test(url)) return url;
    return null;
  }

  /** "#ff7ac6" -> [255, 122, 198] */
  function hexToRgb(hex) {
    var m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
    if (!m) return null;
    var n = parseInt(m[1], 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }

  /** 套用主题配色：同时写 --xxx 和 --xxx-rgb，后者供 rgba() 拼透明度用 */
  function applyTheme(data) {
    var root = document.documentElement;
    var map = [
      ['accent', '--accent', '--accent-rgb'],
      ['accent2', '--accent-2', '--accent-2-rgb'],
      ['accent3', '--accent-3', '--accent-3-rgb']
    ];

    map.forEach(function (item) {
      var rgb = hexToRgb(data[item[0]]);
      if (!rgb) return;
      root.style.setProperty(item[1], '#' + rgb.map(function (v) {
        return ('0' + v.toString(16)).slice(-2);
      }).join(''));
      root.style.setProperty(item[2], rgb.join(', '));
    });

    // 让手机浏览器的地址栏也跟着变色
    var meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'theme-color');
      document.head.appendChild(meta);
    }
    var bg = hexToRgb(data.accent);
    meta.setAttribute('content', bg ? '#' + bg.map(function (v) {
      return ('0' + v.toString(16)).slice(-2);
    }).join('') : '#0a0a1a');
  }

  function applyBackground(data) {
    var bg = safeUrl(data && data.bgImage);
    if (!bg) return;

    var dim = parseInt(data.bgDim, 10);
    if (isNaN(dim)) dim = 60;
    dim = Math.max(0, Math.min(100, dim));

    var root = document.documentElement;
    root.style.setProperty('--bg-image', 'url("' + bg + '")');
    root.style.setProperty('--bg-dim', (dim / 100).toFixed(2));
    document.body.classList.add('has-bg');
  }

  /**
   * 站点图标：顶栏站名左边那个圆点，以及浏览器标签页的图标。
   *
   * 刻意「改 :root 上的 CSS 变量 + <html> 上的类」而不是直接操作那个圆点元素：
   * 软导航换页时 body 整体被替换，圆点元素会重新创建，直接改它会丢掉图标；
   * 而 :root 和 <html> 是跨页存活的，胶囊一改，新页面自然就带上图标了。
   */
  function applyLogo(data) {
    var logo = safeUrl(data && data.logoImage);
    var root = document.documentElement;

    if (logo) {
      root.style.setProperty('--logo-image', 'url("' + logo + '")');
      root.classList.add('has-logo');
    } else {
      root.style.removeProperty('--logo-image');
      root.classList.remove('has-logo');
    }

    // 标签页图标跟着一起换；没设置就用 HTML 里写死的默认 SVG
    var link = document.querySelector('link[rel="icon"]');
    if (link && defaultFavicon) {
      link.setAttribute('href', logo || defaultFavicon);
    }
  }

  function apply(data) {
    if (!data) return;
    applyTheme(data);
    applyBackground(data);
    applyLogo(data);
  }

  function load() {
    fetch('/setting/site', { credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (res) {
        if (res && res.code === 200) apply(res.data);
      })
      .catch(function () { /* 接口异常就用默认外观 */ });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', load);
  } else {
    load();
  }

  window.Theme = {
    reload: load,
    hexToRgb: hexToRgb,
    /** 后台调色时的实时预览：只改 CSS 变量，不落库 */
    applyColors: function (accent, accent2, accent3) {
      applyTheme({ accent: accent, accent2: accent2, accent3: accent3 });
    }
  };
})();
