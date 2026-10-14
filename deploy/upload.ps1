<#
  上传到服务器（在本地 Windows 上执行）

  用法：
    .\deploy\upload.ps1 -Server root@你的服务器IP              # 构建并上传
    .\deploy\upload.ps1 -Server root@你的服务器IP -SkipBuild   # 用现有 jar，只上传

  首次部署的服务器端步骤见 deploy\DEPLOY.md
#>
param(
    [Parameter(Mandatory = $true)][string]$Server,
    [string]$RemoteDir = '/opt/shiguang',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

Write-Host ""
Write-Host "=== 上传到服务器 $Server ===" -ForegroundColor Magenta

# ---------- 1. 构建 ----------
if ($SkipBuild) {
    Write-Host "[1/3] 跳过构建（-SkipBuild）" -ForegroundColor DarkGray
} else {
    Write-Host "[1/3] 构建 jar ..." -ForegroundColor Cyan
    & .\mvnw.cmd clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败，请先修好编译错误" }
}

$jar = Get-ChildItem .\target\*.jar -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw "target\ 下找不到 jar。先执行 .\mvnw.cmd clean package -DskipTests" }
Write-Host "      产物 $($jar.Name)  $([math]::Round($jar.Length/1MB,1)) MB" -ForegroundColor DarkGray

# ---------- 2. 测试连接 ----------
# 注意：这里刻意不用 BatchMode，否则用密码登录的服务器会被直接判定失败。
# 没配 SSH 密钥的话，下面每一步都会让你输一次密码。
Write-Host "[2/3] 测试 SSH 连接（会提示输入密码）..." -ForegroundColor Cyan
& ssh -o ConnectTimeout=15 $Server "echo connected"
if ($LASTEXITCODE -ne 0) {
    Write-Host "      连接失败。检查 IP、端口、密码；非 22 端口要用 ssh -p / scp -P" -ForegroundColor Yellow
    throw "SSH 连接失败"
}
Write-Host "      连接正常" -ForegroundColor DarkGray

# ---------- 3. 上传 ----------
Write-Host "[3/3] 上传文件 ..." -ForegroundColor Cyan

Write-Host "      app.jar" -ForegroundColor DarkGray
# 用相对路径 + 正斜杠：Windows 的 scp 会把 "C:\..." 里的冒号误认成主机名分隔符
& scp "target/$($jar.Name)" "${Server}:/tmp/app.jar"
if ($LASTEXITCODE -ne 0) { throw "jar 上传失败" }

Write-Host "      schema.sql" -ForegroundColor DarkGray
& scp "schema.sql" "${Server}:/tmp/schema.sql"
if ($LASTEXITCODE -ne 0) { throw "schema.sql 上传失败" }

Write-Host "      shiguang-blog.service" -ForegroundColor DarkGray
& scp "deploy/shiguang-blog.service" "${Server}:/tmp/shiguang-blog.service"
if ($LASTEXITCODE -ne 0) { throw "service 文件上传失败" }

Write-Host "      nginx-shiguang.conf" -ForegroundColor DarkGray
& scp "deploy/nginx-shiguang.conf" "${Server}:/tmp/nginx-shiguang.conf"
if ($LASTEXITCODE -ne 0) { throw "nginx 配置上传失败" }

# ---------- 完成 ----------
Write-Host ""
Write-Host "文件已传到 /tmp。现在登上去：" -ForegroundColor Green
Write-Host "    ssh $Server" -ForegroundColor White
Write-Host ""
Write-Host "【先看应用现在是怎么起的】" -ForegroundColor Yellow
Write-Host "    ps -ef | grep '[j]ava'"
Write-Host "    sudo systemctl list-units --type=service | grep -i -E 'blog|spring|shiguang'"
Write-Host ""
Write-Host "【systemd 托管时这样更新】" -ForegroundColor Yellow
Write-Host "    sudo mv /tmp/app.jar $RemoteDir/app.jar"
Write-Host "    sudo chown shiguang:shiguang $RemoteDir/app.jar"
Write-Host "    sudo systemctl restart <上面查到的服务名>"
Write-Host ""
Write-Host "【补数据库表】新版每日推歌需要 song 表，不建会 500" -ForegroundColor Yellow
Write-Host "    mysql -u 你的数据库用户 -p blog < /tmp/schema.sql"
Write-Host "    mysql -u 你的数据库用户 -p -e 'USE blog; SHOW TABLES;'"
Write-Host ""
Write-Host "【想用 http://你的服务器IP/ 不带端口】服务器上已经有 nginx" -ForegroundColor Yellow
Write-Host "    sudo cp /tmp/nginx-shiguang.conf /etc/nginx/sites-available/shiguang"
Write-Host "    sudo ln -sf /etc/nginx/sites-available/shiguang /etc/nginx/sites-enabled/shiguang"
Write-Host "    sudo rm -f /etc/nginx/sites-enabled/default"
Write-Host "    sudo nginx -t && sudo systemctl reload nginx"
Write-Host ""
Write-Host "提示：没配 SSH 密钥的话每条命令都会要一次密码。" -ForegroundColor DarkGray
Write-Host "      配一次密钥就再也不用输了，见 deploy\DEPLOY.md" -ForegroundColor DarkGray
Write-Host ""
