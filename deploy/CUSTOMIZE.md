# 自定义前端显示

前端全部在 `src/main/resources/static/` 下，都是普通 HTML/CSS/JS，**不需要任何构建工具**，改完浏览器刷新即可（本地开发时）。

```
src/main/resources/static/
├── index.html      首页（Hero + 文章列表 + 每日推歌）
├── post.html       文章详情页
├── admin.html      后台（登录 / 写文章 / 曲库管理 / 我的资料 / 用户管理 / 站点设置）
├── songs.html      曲库页（浏览全部歌曲 + 点击播放）
├── css/style.css   全部样式（含配色变量）
└── js/
    ├── site.js       ★ 站名、标语（改这个最常用）
    ├── api.js        接口封装 + 转义/日期/头像工具
    ├── theme.js      套用后台设置的主题配色和背景图
    ├── markdown.js   轻量 Markdown 渲染器
    ├── globalplayer.js  全站播放器（切页不中断的关键，见下方说明）
    ├── router.js     站内软导航（pjax）
    ├── index.js      首页逻辑 + 文章卡片模板
    ├── post.js       详情页逻辑 + 评论区
    ├── songs.js      曲库页逻辑
    └── admin.js      后台逻辑
```

### ⚠️ 加脚本时注意位置（很重要）

**`<head>` 里的脚本 = 全局脚本，只在首次进入站点时加载一次；
`<body>` 里的脚本 = 页面脚本，每次切页都会重新执行。**

```
index.html   head: site, api, theme, globalplayer, router      body: index
post.html    head: site, api, theme, markdown, globalplayer, router   body: post
admin.html   head: site, api, theme, markdown, globalplayer, router   body: admin
songs.html   head: site, api, theme, globalplayer, router      body: songs
```

放错位置会有实际问题：

- **库/工具脚本放进 body** → 每次切页都重新加载一遍，白费流量，还可能重复初始化
- **页面脚本放进 head** → 切页后它不会重新执行，页面看起来是"死的"（内容换了但没绑事件）
- **某个 head 脚本只在部分页面引入** → 路由会自动补加载，但页面脚本必须排在它所依赖的库之后

页面脚本如果要往 `window` / `document` / `Player` 上挂监听，**必须把取消函数 push 进
`window.__pageCleanups`**，否则每次切页都会叠一层：

```js
window.__pageCleanups = window.__pageCleanups || [];
window.__pageCleanups.push(window.Player.on('play', myHandler));
```

### 音乐为什么能跨页不中断

浏览器的自动播放策略规定：**整页加载后的新文档没有"用户手势"，`play()` 会被拒绝**。
所以"存进度再恢复"只能恢复歌名和位置，声音一定会停一下（Safari 尤其严格）。

`router.js` 的做法是**不让页面真正重新加载**：拦截站内链接的点击，用 fetch 取回目标页
HTML、只替换 `<body>` 内容，再重新执行该页的页面脚本。播放器所在的 JS 和 audio 元素
始终活着，音乐自然就不断。标了 `data-persist` 的元素（播放条）会在换页时被保留。

这套机制已实测：曲库页播放 → 切首页 → 切文章页 → 浏览器后退，全程音乐不断。

### ⚠️ 往 `<body>` 上加类名要注意

换页时会用新页面的 `className` 覆盖 `<body>`，所以**脚本运行时加在 body 上的类名会被抹掉**。
需要跨页保留的类，必须登记到 `router.js` 的 `RUNTIME_BODY_CLASSES` 里：

```js
var RUNTIME_BODY_CLASSES = ['has-bg', 'has-player'];
//   has-bg      theme.js 加的，控制主题背景图（不登记的话切页背景就没了）
//   has-player  globalplayer.js 加的，给底部播放条让出空间（不登记的话播放条会盖住页脚）
```

以后新增这类"脚本加的 body 类名"，记得同步加进去。


---

## 一、不用改代码：登录后台就能改

| 想改什么 | 在哪儿改 |
|---|---|
| **主题配色** | 后台 →「站点设置」→「主题配色」→ 选预设或自己调 |
| **网站背景图** | 后台 →「站点设置」→ 上传图片、拖滑块调遮罩 |
| 文章标题 / 摘要 / 正文 | 后台 →「写文章」（正文支持 Markdown） |
| 歌名 / 歌手 / 歌词 | 后台 →「曲库管理」→ 上传时填 |
| 歌曲封面 | 上传时的「封面图」，会显示成旋转的黑胶唱片 |
| 今天推哪首歌 | 曲库列表里选日期 → 点「设为推荐」 |

### 关于主题配色（后台「站点设置」）

三个颜色各自控制一大片区域：

| 变量 | 作用范围 |
|---|---|
| 主色 `--accent` | 按钮渐变、链接、区块标题的强调条、卡片悬停光晕、播放按钮、代码块、页脚 ♥ |
| 辅色 `--accent-2` | 渐变另一端、引用块左边框、每日推歌卡片顶部的柔光 |
| 点缀色 `--accent-3` | 首页大标题渐变的末尾、顶栏品牌圆点 |

**连背景的极光渐变也跟着变** —— 那三层径向渐变用的就是这三个颜色。
换成森林绿，整个背景的光晕都会变绿；换成落日橙就整个变暖。

三种用法：

1. **预设**：樱花粉 / 深海蓝 / 森林绿 / 落日橙 / 紫罗兰 / 薄荷青，点一下即可
2. **自己调**：三个取色器 + 十六进制输入框。调色时页面上**立刻**能看到效果，不用先保存
3. **从背景图自动提取**：在已上传的背景图里统计最显眼的有彩色调算出主色，
   再沿色相 +32° / +64° 推出辅色和点缀色，得到一套协调的配色

> 提取是**服务端**做的（JDK 自带的 ImageIO，不引第三方库）：
> 把图缩到 80×80，逐像素转 HSB，丢掉饱和度低于 0.20 或过黑过白的像素，
> 按色相分 18 个桶用「饱和度×亮度」投票，得票最高的桶的平均色就是主色。
> **webp 格式 JDK 默认不支持解码**，会提示换成 jpg/png。

配色和背景图都存在数据库 `site_setting` 表里（键值对），
前端每个页面加载时拉一次 `/setting/site` 套上去。

### 关于背景图（后台「站点设置」）

- 上传后**三个页面（首页 / 文章详情 / 后台）都会套用**，存在 `uploads/bg/` 下
- **遮罩深浅**滑块控制背景图上那层黑色遮罩：
  - 数值越小，背景照片越清楚；越大则文字越突出
  - 实测 **50~70 最舒服**（默认 60）：照片看得清，文字也不吃力
  - 照片很亮很花（白天风景）→ 调到 70 以上
  - 照片偏暗（夜景、深色抽象图）→ 调到 40 左右，否则整张图会糊成一片黑
  - 之所以即使数值很小文字也还清楚，是因为毛玻璃卡片自己带了一层半透明深色底
- 点「恢复默认背景」会回到内置的极光渐变背景（**只清背景图，遮罩值会保留**）
- 图片建议 1920×1080 以上、5MB 以内；图越大访客打开首页越慢

配置存在数据库 `site_setting` 表里（键值对），前端每个页面加载时拉一次 `/setting/site`。
不想要这个功能的话，把 `/opt/shiguang/static/js/theme.js` 从页面里去掉即可 —— 不影响其他功能。

### 如果想改成自己写死的背景

不想用上传功能，也可以直接在 `css/style.css` 的 `body` 里写：

```css
body {
  background-image:
    linear-gradient(rgba(10,10,26,.6), rgba(10,10,26,.6)),   /* 遮罩 */
    url("/uploads/bg/你的图片.jpg");
  background-size: cover;
  background-position: center;
  background-attachment: fixed;
}
```


---

## 二、改站名和标语（最常用）

改 **`js/site.js`** 一个文件：

```js
window.SITE = {
  name: '你的站名',              // 顶栏、首页大标题、页脚、浏览器标签页
  subtitle: '你的个性签名。'      // 首页大标题下面的那句话
};
```

改完顶栏、首页大标题、页脚、浏览器标签页标题会一起变。

> 页脚里「· 用 ♥ 与 Spring Boot 搭建」这半句是写死在
> `index.html`、`post.html`、`admin.html` 三个文件里的，要改得动那三处。

---

## 三、改配色（改 `css/style.css` 最上面那几行）

`style.css` 第 7~30 行是全部设计变量，**改这里就能换整体风格**，不用碰下面的样式代码：

```css
:root {
  --accent: #ff7ac6;        /* 主色（粉色）：按钮渐变、标题、链接、强调条 */
  --accent-2: #8b5cf6;      /* 辅色（紫色）：渐变另一端 */
  --accent-3: #38bdf8;      /* 点缀色（天蓝）：首页标题渐变末尾、品牌圆点 */

  --text: #eef1ff;                    /* 正文颜色 */
  --text-dim: rgba(238,241,255,.70);  /* 次要文字 */
  --text-faint: rgba(238,241,255,.45);/* 最淡的文字（日期、浏览量） */

  --glass-bg: rgba(255,255,255,.065); /* 毛玻璃卡片底色，越大越亮 */
  --glass-border: rgba(255,255,255,.14);      /* 卡片描边 */
  --glass-blur: blur(22px) saturate(180%);     /* 毛玻璃模糊强度 */

  --radius: 22px;           /* 卡片圆角 */
  --radius-sm: 13px;        /* 输入框等小元素圆角 */
}
```

### 现成的几套配色，直接粘

**蓝色系（冷静）**
```css
  --accent: #60a5fa;
  --accent-2: #818cf8;
  --accent-3: #22d3ee;
```

**青绿系（清爽）**
```css
  --accent: #34d399;
  --accent-2: #14b8a6;
  --accent-3: #a3e635;
```

**橙红系（暖）**
```css
  --accent: #fb923c;
  --accent-2: #f43f5e;
  --accent-3: #fbbf24;
```

### 想要更"实"或更"透"的毛玻璃

```css
  --glass-bg: rgba(255,255,255,.12);   /* 更亮更实 */
  --glass-blur: blur(30px) saturate(200%);  /* 更糊 */
```
嫌卡片太模糊伤性能（老电脑）就调小：`blur(12px) saturate(140%)`。

---

## 四、改背景极光

`style.css` 的 `body` 里（第 48~52 行），四层径向渐变叠出来的极光效果，**没有用任何图片**，所以离线也能显示：

```css
  background-image:
    radial-gradient(1150px 780px at 10% -12%, rgba(139, 92, 246, 0.46), transparent 62%),
    radial-gradient(980px 700px at 90% 4%,   rgba(255, 122, 198, 0.36), transparent 60%),
    radial-gradient(900px 900px at 48% 116%, rgba(56, 189, 248, 0.28), transparent 64%),
    linear-gradient(158deg, #0a0a1a 0%, #150f2e 46%, #0b0a1e 100%);
```

- 前三条是三个光斑：`at 10% -12%` 是位置（左上角），`rgba(...)` 里最后一位是**强度**（0.46 → 调到 0.15 就变暗）
- 最后一条是底色渐变，`#0a0a1a` 是最深的底色

想要更暗更干净，把三个光斑的强度都调到 `0.12` 左右。

---

## 五、改文字和文案

| 想改的文字 | 文件 | 关键字 |
|---|---|---|
| 首页「最新文章」「每日推歌」标题 | `index.html` | `section-title` |
| 首页 Hero 下面的统计标签 | `index.html` | `chip` |
| 「曲库还是空的…」这类空状态提示 | `js/index.js` | `曲库还是空的` |
| 文章卡片的日期/浏览量图标 | `js/index.js` | `articleCard` 函数 |
| 后台各标签页和按钮文字 | `admin.html` | 直接搜中文 |
| 登录页提示语 | `admin.html` | `还没有账号` |

---

## 六、改完怎么让它生效

### 方式 1：重新打包上传（适合大改）

```powershell
.\deploy\upload.ps1 -Server root@你的服务器IP
```

然后服务器上替换 jar 并重启（`deploy/DEPLOY.md` 第 10 节有完整命令）。

### 方式 2：外部 static 目录（改个颜色不用传 24MB）★ 推荐

前端改动频繁的话，让应用**优先读服务器上的目录**，改完只要 scp 一个文件 + 重启。

**一次性配置**（需要重新部署一次 jar 才能生效）：

```bash
# 1. 服务器上建目录，把现有前端复制过去
sudo mkdir -p /opt/shiguang/static
# 从 jar 里解出来（或从本地 scp 一份上去）
cd /opt/shiguang/static && sudo unzip -o /opt/shiguang/app.jar 'BOOT-INF/classes/static/*' -d /tmp/x
sudo cp -r /tmp/x/BOOT-INF/classes/static/* /opt/shiguang/static/
sudo chown -R shiguang:shiguang /opt/shiguang/static

# 2. .env 里加一行，让外部目录优先、jar 内兜底
sudo nano /opt/shiguang/.env
#   STATIC_LOCATIONS=file:/opt/shiguang/static/,classpath:/static/

sudo systemctl restart shiguang-blog
```

**以后改前端只需**：

```powershell
# 本地改完 style.css，直接传上去
scp src/main/resources/static/css/style.css root@你的服务器IP:/tmp/
```
```bash
sudo mv /tmp/style.css /opt/shiguang/static/css/style.css
sudo systemctl restart shiguang-blog
```

> 这个机制我已实测：外部目录里**存在**的文件优先用外部，**不存在**的自动回退到 jar 内。
> 所以你可以只把要改的几个文件放过去，不用整个前端都复制。

---

## 七、几个注意点

1. **改完没变化 → 先强制刷新**（`Ctrl + F5`）。
   浏览器会缓存 CSS/JS，普通 F5 可能还是旧样式。

2. **不要引入 CDN 资源**（比如 Google Fonts、Font Awesome、Tailwind CDN）。
   现在整个前端零外部依赖，服务器不联网也能正常显示。引入 CDN 后一旦网络不通，页面就残了。
   需要图标就用内联 SVG（现在的日历/眼睛/播放图标都是这么做的）。

3. **文件编码必须是 UTF-8**。用 VS Code 或 IDEA 改，别用记事本（会加 BOM，可能导致中文乱码）。

4. **改 JS 时注意** `escapeHtml()`。
   所有从数据库取出来塞进 HTML 的地方都要过一遍它，否则文章标题里写 `<script>` 就能执行。
   现在 `js/index.js`、`js/post.js`、`js/admin.js` 里都是这么做的，你自己加内容时别漏。

5. **改完最好本地先看一眼**。本地启动：
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   然后浏览器开 `http://localhost:8080`，确认没问题再传服务器。
