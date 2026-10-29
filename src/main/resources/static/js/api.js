/**
 * 后端接口封装 + 通用小工具。
 * 后端统一返回 { code, msg, data }：code === 200 视为成功。
 */
(function () {
  async function request(url, options) {
    var res;
    try {
      res = await fetch(url, options);
    } catch (e) {
      return { ok: false, status: 0, msg: '网络请求失败，请确认后端已启动' };
    }

    // 未登录时拦截器直接返回 401 和一段 JSON
    if (res.status === 401) {
      return { ok: false, status: 401, msg: '请先登录' };
    }

    var json = null;
    try {
      json = await res.json();
    } catch (e) {
      /* 不是 JSON，下面统一处理 */
    }

    if (!json) {
      return { ok: false, status: res.status, msg: '服务器返回异常（HTTP ' + res.status + '）' };
    }
    if (json.code === 200) {
      return { ok: true, status: res.status, data: json.data, msg: json.msg };
    }
    return { ok: false, status: res.status, msg: json.msg || '请求失败', data: json.data };
  }

  window.API = {
    get: function (url) {
      return request(url);
    },
    postJSON: function (url, body) {
      return request(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
    },
    /** 查询参数形式的 POST，如 /song/recommend?songId=1&date=2026-10-12 */
    postParams: function (url, params) {
      var qs = new URLSearchParams(params).toString();
      return request(url + (qs ? '?' + qs : ''), { method: 'POST' });
    },
    /** multipart/form-data（上传音频） */
    postForm: function (url, formData) {
      return request(url, { method: 'POST', body: formData });
    }
  };

  /* ---------------- 小工具 ---------------- */

  /** 转义 HTML，所有用户输入渲染进页面前都要过一遍，防 XSS */
  window.escapeHtml = function (str) {
    if (str === null || str === undefined) return '';
    return String(str).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  };

  /** "2026-10-12T12:24:58" -> "2026-10-12" */
  window.formatDate = function (value) {
    if (!value) return '';
    return String(value).slice(0, 10);
  };

  /** 本地时区的今天，格式 yyyy-MM-dd（不能用 toISOString，那是 UTC，会差一天） */
  window.todayStr = function () {
    var d = new Date();
    var m = String(d.getMonth() + 1).padStart(2, '0');
    var day = String(d.getDate()).padStart(2, '0');
    return d.getFullYear() + '-' + m + '-' + day;
  };

  /**
   * 头像：有图就显示图，没有就用昵称首字做一个彩色圆。
   * @param {string} name 昵称
   * @param {string} url  头像地址，可为空
   * @param {number} size 边长（px）
   */
  window.avatarHtml = function (name, url, size) {
    var s = size || 36;
    var style = 'width:' + s + 'px;height:' + s + 'px';
    if (url) {
      return '<img class="avatar" style="' + style + '" src="' + window.escapeHtml(url) +
        '" alt="' + window.escapeHtml(name || '') + '" loading="lazy">';
    }
    var initial = String(name || '?').trim().charAt(0) || '?';
    return '<span class="avatar avatar-text" style="' + style + ';font-size:' +
      Math.round(s * 0.44) + 'px">' + window.escapeHtml(initial) + '</span>';
  };

  /** 日期时间：2026-10-12T20:03:11 -> 2026-10-12 20:03 */
  window.formatDateTime = function (value) {
    if (!value) return '';
    var s = String(value).replace('T', ' ');
    return s.length >= 16 ? s.slice(0, 16) : s;
  };

  /** 秒数 -> "3:07" */
  window.formatTime = function (seconds) {
    if (!isFinite(seconds) || seconds < 0) return '0:00';
    var m = Math.floor(seconds / 60);
    var s = Math.floor(seconds % 60);
    return m + ':' + (s < 10 ? '0' + s : s);
  };

  /* ---------------- 轻提示 ---------------- */

  /**
   * 注册入口是否开放。
   *
   * <p>顶栏的「注册」链接要根据这个决定显不显示（站点关闭注册时不该还留着入口）。
   * 结果缓存在 window 上，多个页面脚本共用一次请求；软导航换页也不会重复请求。
   * 请求失败时保守返回 true —— 宁可多显示一个链接，也别把正常访客挡住。
   */
  window.loadRegistrationOpen = function () {
    if (!window.__regOpenPromise) {
      window.__regOpenPromise = request('/user/quota').then(function (res) {
        return !!(res.ok && res.data && res.data.registrationEnabled);
      }).catch(function () {
        return true;
      });
    }
    return window.__regOpenPromise;
  };

  window.toast = function (msg, type) {
    var host = document.querySelector('.toast-host');
    if (!host) {
      host = document.createElement('div');
      host.className = 'toast-host';
      document.body.appendChild(host);
    }
    var el = document.createElement('div');
    el.className = 'toast ' + (type === 'err' ? 'err' : 'ok');
    el.textContent = msg;
    host.appendChild(el);
    setTimeout(function () {
      el.classList.add('out');
      setTimeout(function () {
        el.remove();
      }, 300);
    }, 2600);
  };
})();
