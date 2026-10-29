# 部署到服务器

目标：Ubuntu / Debian + systemd 托管 + 服务器本机 MySQL，用 `http://服务器IP:8080` 访问。

按顺序做，每一步都有验证命令。**第 1 步没做完不要往后走**，否则服务器上没有东西可部署。

---

## 0. 先看清楚要部署什么

| 项目 | 值 |
|---|---|
| 运行方式 | 一个 jar 包，systemd 托管，开机自启 |
| 端口 | 8080 |
| 数据库 | 服务器本机 MySQL，库名 `blog` |
| 上传文件 | `/opt/shiguang/uploads`（在 jar 外面，更新程序不会丢） |
| 配置来源 | 环境变量 `/opt/shiguang/.env` |
| jar 里有密码吗 | **没有**。密钥已挪到项目根目录的 `application-local.properties`，不会被打进 jar |

---

## 1. 本地构建 jar

在项目根目录执行（Windows 用 `mvnw.cmd`）：

```bash
./mvnw clean package -DskipTests
```

产物：`target/hello-spring-0.0.1-SNAPSHOT.jar`

> `-DskipTests` 是必要的：集成测试 `contextLoads` 要连数据库，构建机上没有 MySQL 会失败。
> 第一次构建需要联网下载依赖。

**验证**：`ls -lh target/*.jar` 能看到一个 20MB 左右的 jar。

---

## 2. 服务器装 JDK 和 MySQL

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless mysql-server
sudo systemctl enable --now mysql
```

**验证**：

```bash
java -version          # 显示 17 或更高即可
systemctl is-active mysql   # 输出 active
```

> 如果源里没有 17（比如 Ubuntu 24.04 默认是 21），装 `openjdk-21-jre-headless` 也一样，
> 项目编译目标是 Java 17，跑在 21 上没问题。

---

## 3. 建库、建专用账号、导表结构

**不要用 root 连数据库**，给应用建一个只管 `blog` 库的账号：

```bash
sudo mysql -u root
```

```sql
CREATE DATABASE blog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'shiguang'@'localhost' IDENTIFIED BY '换成你自己的强密码';
GRANT ALL PRIVILEGES ON blog.* TO 'shiguang'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

把 `schema.sql` 传上去并导入（在**本地**执行第一条）：

```bash
scp schema.sql root@你的服务器IP:/tmp/schema.sql
```

```bash
# 下面这些在服务器上执行
mysql -u shiguang -p blog < /tmp/schema.sql
```

**验证**：

```bash
mysql -u shiguang -p -e "USE blog; SHOW TABLES;"
# 应该看到 5 张表：article  comment  site_setting  song  user
```

---

## 4. 创建运行账号和目录

不要用 root 跑应用：

```bash
sudo useradd --system --create-home --shell /usr/sbin/nologin shiguang
sudo mkdir -p /opt/shiguang/uploads
sudo chown -R shiguang:shiguang /opt/shiguang
```

---

## 5. 上传 jar

在**本地**执行：

```bash
scp target/hello-spring-0.0.1-SNAPSHOT.jar root@你的服务器IP:/tmp/app.jar
```

在**服务器**上执行：

```bash
sudo mv /tmp/app.jar /opt/shiguang/app.jar
sudo chown shiguang:shiguang /opt/shiguang/app.jar
```

---

## 6. 写配置文件 `/opt/shiguang/.env`

仓库里的 `deploy/env.example` 可以直接参考。在服务器上执行：

```bash
sudo tee /opt/shiguang/.env > /dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod
DB_USERNAME=shiguang
DB_PASSWORD=第3步设置的数据库密码
UPLOAD_DIR=/opt/shiguang/uploads
TZ=Asia/Shanghai
EOF

sudo chown shiguang:shiguang /opt/shiguang/.env
sudo chmod 600 /opt/shiguang/.env
```

> **密码含特殊字符**（`#`、空格、引号）时，用双引号把值包起来：
> `DB_PASSWORD="a#b c"`。等号两边不要留空格。

**验证**：`sudo cat /opt/shiguang/.env` 内容正确，且 `ls -l` 显示权限是 `-rw-------`。

---

## 7. 装 systemd 服务

把 `deploy/shiguang-blog.service` 传上去：

```bash
# 本地
scp deploy/shiguang-blog.service root@你的服务器IP:/tmp/
```

```bash
# 服务器
sudo cp /tmp/shiguang-blog.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now shiguang-blog
```

**验证**：

```bash
systemctl status shiguang-blog     # 应为 active (running)
curl http://localhost:8080/hello   # 应返回「你好，Spring Boot！…」
```

看日志：

```bash
journalctl -u shiguang-blog -f
```

---

## 8. 开放 8080 端口

```bash
sudo ufw allow 8080/tcp
sudo ufw allow OpenSSH        # 别把自己 SSH 关在外面
sudo ufw enable
sudo ufw status
```

> ⚠️ **最容易踩的坑**：`ufw` 放行了不代表能访问。
> 阿里云 / 腾讯云 / 华为云还需要在**控制台的「安全组」里单独放行 8080 入方向**（TCP）。
> 如果本机 `curl` 通、用手机流量访问不通，基本就是安全组没开。

---

## 9. 创建管理员账号（关键一步）

**本项目不提供注册功能，只有站长（`role='admin'`）能登录。**
所以第一次部署必须直接用 SQL 建一个管理员账号，密码要用项目自带的工具生成哈希。

### 9.1 生成密码哈希

`deploy/PasswordHash.java` 是项目自带的生成器（PBKDF2-HMAC-SHA256，210000 次迭代）：

```bash
java deploy/PasswordHash.java '你的密码'
# 输出形如：pbkdf2$sha256$210000$<salt>$<hash>   （87 个字符）
```

### 9.2 写入数据库

把上一步的输出替换到下面的 `$HASH` 里：

```bash
HASH=$(java deploy/PasswordHash.java '你的密码')
mysql -u shiguang -p -e "USE blog; INSERT INTO \`user\`(username, password, nickname, role) \
  VALUES('你的用户名', '$HASH', '站长', 'admin');"
```

验证：

```bash
mysql -u shiguang -p -e "USE blog; SELECT id, username, role FROM \`user\`;"
# role 必须是 admin，否则登录会被拒绝
```

### 9.3 登录

浏览器打开 `https://你的域名或服务器IP/admin.html` 登录，进去就能发文章、传歌。

> 登录接口只接受 `role='admin'` 的账号，并且要求走 **HTTPS**
> （`REQUIRE_HTTPS_LOGIN=true`）；用 `http://` 登录会被拒绝并提示改用 https。

### 9.4 登录开关与 IP 白名单

登录入口默认只对**站长本人**和**IP 白名单**内的地址显示，公开访客页面上看不到任何入口：

| 变量 | 作用 |
|---|---|
| `LOGIN_ENABLED` | 是否受理登录；设 `false` 则整站对外只读，但白名单 IP 仍可登录 |
| `LOGIN_ALLOWED_IPS` | 允许登录的 IP，逗号分隔；家里是动态 IP 时，SSH 上去跑 `bash /opt/shiguang/allow-login-from-me.sh` 自动更新 |

---

## 10. 以后怎么更新代码

改完代码后，在本地**一条命令**：

```powershell
.\deploy\deploy.ps1
```

它做的事（全自动）：

1. `mvnw clean package -DskipTests` 构建，并算出 jar 的 SHA256
2. 上传 jar 到服务器 `/tmp/app.jar.new`；同时把 `remote-deploy.sh` 去掉 BOM、转成 LF 后一起上传
3. 在服务器上**校验 SHA256**，不一致（比如传了一半）直接中止
4. 备份服务器上当前的 jar 到 `/opt/shiguang/jar-backup-<时间戳>.jar`（只保留最近 5 个）
5. 停服务 → 换包（`/opt/shiguang/app.jar`）→ `chown shiguang:shiguang` → 起服务
6. **轮询等待就绪**（最多 90 秒，探测 `/hello`）—— 这台服务器启动要约 14 秒，不能傻等固定秒数；
   **超时会自动回滚**到备份包并重启，然后打印日志里的异常
7. 验证 **15 个读接口**（应全 200）
8. 验证 **15 个写接口**在未登录时（应全 401）—— 这样拦截器配置被改坏时能立刻发现
9. 验证**浏览上报接口**（`POST /article/0/view`）匿名可访问：
   返回 401 说明被拦截器误拦了，返回 404 说明路由没注册，都不行
10. 打印最近 2 分钟日志里的错误条数

> 第 6 步超时会**自动回滚**；第 7、8 步不通过则报错退出并给出手工回滚命令，不会自动回滚。

换服务器或临时不重新构建：

```powershell
.\deploy\deploy.ps1 -Server root@1.2.3.4
.\deploy\deploy.ps1 -SkipBuild
```

> 你上传的歌曲、封面和背景图都在 `/opt/shiguang/uploads`，
> **替换 jar 不会影响它们**，放心更新。

要手改服务器上的文件，用：

```powershell
scp 本地文件 root@你的服务器IP:/tmp/
ssh root@你的服务器IP
```


---

## 11. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 服务起来就退出 | `journalctl -u shiguang-blog -n 50`。多半是 `DB_PASSWORD` 填错，或者第 3 步的库没建成功 |
| 服务器上 `curl localhost:8080` 通，外面访问不通 | 云服务器**安全组**没放行 8080（ufw 只是本机防火墙） |
| 页面能开但登录就报错 | 数据库连不上，看日志里的 `Access denied` 或 `Unknown database` |
| 「每日推歌」换歌的时间不对 | `.env` 里的 `TZ=Asia/Shanghai` 没生效，检查 service 里的 `-Duser.timezone` |
| 重启服务后要重新登录 | 正常现象。Session 存在内存里，单机部署够用；以后要多机部署再考虑存 Redis |
| 上传大文件失败 | 默认限制 50MB（见 `application.properties` 的 `spring.servlet.multipart.max-file-size`） |

---

## 12. 两个安全提醒

1. **现在是纯 HTTP**（因为你选了 IP + 端口）。登录密码是明文传输的，
   浏览器也会提示"不安全"。以后如果买了域名，加上 Nginx + certbot 免费证书就能解决。
2. **数据库密码只存在于 `/opt/shiguang/.env`**（权限 600）和环境变量里，
   没有写进 jar，也没有提交到 git。
   仓库根目录的 `application-local.properties`（你本地的密码）已被 `.gitignore` 忽略 —— 
   **提交代码前用 `git status` 确认它没被跟踪**。

---

## 附：想让访问地址不带 `:8080`

**方法一：直接跑 80 端口**（最简单，不用装额外软件）

```bash
# .env 里加一行
PORT=80
```

```bash
# service 的 [Service] 段加一行，否则非 root 用户绑不了 80 端口
AmbientCapabilities=CAP_NET_BIND_SERVICE
```

改完 `sudo systemctl daemon-reload && sudo systemctl restart shiguang-blog`。

**方法二：装 Nginx 反向代理**（以后要上 HTTPS 的话反正要装）

服务器上已经有 nginx 的话，直接用仓库里的 `deploy/nginx-shiguang.conf`：

```bash
sudo cp /tmp/nginx-shiguang.conf /etc/nginx/sites-available/shiguang
sudo ln -sf /etc/nginx/sites-available/shiguang /etc/nginx/sites-enabled/shiguang
sudo rm -f /etc/nginx/sites-enabled/default      # 关掉默认欢迎页
sudo nginx -t && sudo systemctl reload nginx
```

配置好后确认 `http://你的IP/` 能打开，**再把安全组里的 8080 关掉**（只留 80 和 22），
这样别人就没法绕过 nginx 直连应用端口了。

从零装 nginx 的话：

```bash
sudo apt install -y nginx
```

```nginx
server {
    listen 80;
    server_name 你的域名或IP;
    client_max_body_size 60m;          # 要 ≥ 上传限制，否则传歌会被 Nginx 拦掉

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 附：配置 SSH 免密登录（省掉每次输密码）

默认情况下 `deploy/upload.ps1` 每上传一个文件就要输一次密码（共 5 次）。
在**本地 Windows** 上执行一次，以后就都不用输了：

```powershell
# 1. 生成密钥（一路回车即可，不用设密码短语）
ssh-keygen -t ed25519

# 2. 把公钥追加到服务器
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh root@你的服务器IP "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"

# 3. 验证：这次应该不用输密码就打印 connected
ssh root@你的服务器IP "echo connected"
```

之后 `.\deploy\upload.ps1 -Server root@你的服务器IP` 就能一路跑完。

