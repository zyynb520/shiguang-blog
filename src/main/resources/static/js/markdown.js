/**
 * 轻量 Markdown 渲染器（零依赖，手写）
 *
 * 支持：标题、段落、粗体/斜体/删除线、行内代码、围栏代码块、
 *       链接、图片、无序/有序列表、引用、分割线
 *
 * 安全：每段文本都先做 HTML 转义再做 Markdown 替换，
 *       因此正文里写 <script>alert(1)</script> 只会原样显示，不会执行。
 *
 * 已知不支持（够用即可）：表格、脚注、setext 式标题（=== / --- 下划线）、
 * 嵌套列表、任务列表。
 */
(function (global) {
  var ESCAPE_MAP = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

  function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, function (c) {
      return ESCAPE_MAP[c];
    });
  }

  var INLINE_CODE = '\u0001C'; // 行内代码占位
  var BLOCK_CODE = '\u0000B';  // 代码块占位

  /** 行内语法；入参是未转义的原文 */
  function inline(raw) {
    var text = escapeHtml(raw);

    // 先把行内代码挖走，避免里面的 * _ 被当成强调
    var codes = [];
    text = text.replace(/`([^`\n]+)`/g, function (m, code) {
      codes.push(code);
      return INLINE_CODE + (codes.length - 1) + '\u0001';
    });

    text = text
      .replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, '<img src="$2" alt="$1" loading="lazy">')
      .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g,
        '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/__([^_]+)__/g, '<strong>$1</strong>')
      .replace(/~~([^~]+)~~/g, '<del>$1</del>')
      .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
      .replace(/(^|[^_\w])_([^_\n]+)_/g, '$1<em>$2</em>');

    return text.replace(/\u0001C(\d+)\u0001/g, function (m, i) {
      return '<code>' + codes[Number(i)] + '</code>';
    });
  }

  function isBlockStart(line) {
    return (
      new RegExp('^' + BLOCK_CODE + '\\d+\\u0000$').test(line) ||
      /^\s*#{1,6}\s+/.test(line) ||
      /^\s*>/.test(line) ||
      /^\s*[-*+]\s+/.test(line) ||
      /^\s*\d+[.)]\s+/.test(line) ||
      /^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)
    );
  }

  function renderLines(lines, blocks) {
    var out = [];
    var list = null;

    function flushList() {
      if (!list) return;
      out.push(
        '<' + list.type + '>' +
        list.items.map(function (it) { return '<li>' + inline(it) + '</li>'; }).join('') +
        '</' + list.type + '>'
      );
      list = null;
    }

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];

      // 代码块占位行
      var cb = line.match(/^\u0000B(\d+)\u0000$/);
      if (cb) {
        flushList();
        var blk = blocks[Number(cb[1])];
        if (blk) {
          var cls = blk.lang ? ' class="language-' + escapeHtml(blk.lang) + '"' : '';
          out.push('<pre><code' + cls + '>' + escapeHtml(blk.code.replace(/\n$/, '')) + '</code></pre>');
        }
        continue;
      }

      if (!line.trim()) {
        flushList();
        continue;
      }

      // 分割线：必须排在列表判断之前，否则 --- 会被当成列表项
      if (/^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
        flushList();
        out.push('<hr>');
        continue;
      }

      // 标题
      var h = line.match(/^\s*(#{1,6})\s+(.*)$/);
      if (h) {
        flushList();
        var lvl = h[1].length;
        out.push('<h' + lvl + '>' + inline(h[2].trim()) + '</h' + lvl + '>');
        continue;
      }

      // 引用：连续多行合并成一块
      if (/^\s*>/.test(line)) {
        flushList();
        var quote = [];
        while (i < lines.length && /^\s*>/.test(lines[i])) {
          quote.push(lines[i].replace(/^\s*>\s?/, ''));
          i++;
        }
        i--; // 抵消循环里的 i++
        out.push('<blockquote>' + renderLines(quote.join('\n').split('\n'), blocks) + '</blockquote>');
        continue;
      }

      // 无序列表
      var ul = line.match(/^\s*[-*+]\s+(.*)$/);
      if (ul) {
        if (!list || list.type !== 'ul') {
          flushList();
          list = { type: 'ul', items: [] };
        }
        list.items.push(ul[1]);
        continue;
      }

      // 有序列表
      var ol = line.match(/^\s*\d+[.)]\s+(.*)$/);
      if (ol) {
        if (!list || list.type !== 'ol') {
          flushList();
          list = { type: 'ol', items: [] };
        }
        list.items.push(ol[1]);
        continue;
      }

      // 段落：吃掉连续的普通行，段内换行转成 <br>
      flushList();
      var para = [line];
      while (i + 1 < lines.length && lines[i + 1].trim() && !isBlockStart(lines[i + 1])) {
        i++;
        para.push(lines[i]);
      }
      out.push('<p>' + para.map(inline).join('<br>') + '</p>');
    }

    flushList();
    return out.join('\n');
  }

  function render(src) {
    if (src === null || src === undefined) return '';
    var text = String(src).replace(/\r\n?/g, '\n');

    // 先把围栏代码块摘出来，用占位符替换，避免块内内容被当成 Markdown
    var blocks = [];
    text = text.replace(/```([^\n`]*)\n([\s\S]*?)(?:\n?```|$)/g, function (m, lang, code) {
      blocks.push({ lang: lang.trim(), code: code });
      return '\u0000B' + (blocks.length - 1) + '\u0000';
    });

    return renderLines(text.split('\n'), blocks);
  }

  global.markdown = { render: render, escapeHtml: escapeHtml };
})(typeof window !== 'undefined' ? window : globalThis);
