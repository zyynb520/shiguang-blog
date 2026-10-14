<#
  一键部署到云端服务器（在本地 Windows 上执行）

  用法：
    .\deploy\deploy.ps1 -Server root@你的服务器IP
    .\deploy\deploy.ps1 -Server root@你的服务器IP -SkipBuild   # 不重新构建，用现有 jar

  也可以先设一次环境变量，之后直接跑：
    $env:SHIGUANG_SERVER = 'root@你的服务器IP'
    .\deploy\deploy.ps1

  做的事：构建 -> 上传 -> 在服务器上校验并备份 -> 替换 -> 重启 -> 等就绪 -> 验证接口
  等待就绪超时会自动回滚；接口验证不通过则报错退出并给出手工回滚命令。

  前置：已配置 SSH 免密登录（见 deploy/DEPLOY.md 附录）
#>
param(
    # 服务器地址，形如 root@1.2.3.4
    # 刻意不写死默认值：真实地址不应该提交进仓库
    [string]$Server = $env:SHIGUANG_SERVER,
    [switch]$SkipBuild
)

if ([string]::IsNullOrWhiteSpace($Server)) {
    throw "请指定服务器地址：.\deploy\deploy.ps1 -Server root@你的服务器IP`n或者先设置环境变量 SHIGUANG_SERVER"
}

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host ""
Write-Host "=== 部署到 $Server ===" -ForegroundColor Magenta

# ---------- 1. 构建 ----------
if ($SkipBuild) {
    Write-Host "[1/4] 跳过构建" -ForegroundColor DarkGray
} else {
    Write-Host "[1/4] 构建 jar ..." -ForegroundColor Cyan
    & .\mvnw.cmd -B clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败" }
}

$jar = Get-ChildItem .\target\*.jar | Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw "target\ 下找不到 jar" }
$sha = (Get-FileHash $jar.FullName -Algorithm SHA256).Hash.ToLower()
Write-Host "      $($jar.Name)  $([math]::Round($jar.Length/1MB,1)) MB" -ForegroundColor DarkGray
Write-Host ("      sha256 " + $sha.Substring(0, 16) + "...") -ForegroundColor DarkGray

# ---------- 2. 上传 ----------
Write-Host "[2/4] 上传 jar 和部署脚本 ..." -ForegroundColor Cyan
& scp -q $jar.FullName "${Server}:/tmp/app.jar.new"
if ($LASTEXITCODE -ne 0) { throw "jar 上传失败" }

# 远程脚本要用 LF 换行
$remoteScript = Join-Path $root 'deploy\remote-deploy.sh'
$tmpScript = Join-Path $root 'target\remote-deploy-lf.sh'
$text = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($remoteScript))
# 注意：不能用 StartsWith([char]0xFEFF) —— PowerShell 会把该字符转成空字符串，
# 而 StartsWith("") 恒为 True，会误删文件的第一个字符（曾把 #!/bin/bash 削成 !/bin/bash）。
# 必须比较码位。
if ($text.Length -gt 0 -and [int]$text[0] -eq 0xFEFF) { $text = $text.Substring(1) }
[System.IO.File]::WriteAllBytes($tmpScript, (New-Object System.Text.UTF8Encoding($false)).GetBytes(($text -replace "`r`n", "`n")))

& scp -q $tmpScript "${Server}:/tmp/remote-deploy.sh"
if ($LASTEXITCODE -ne 0) { throw "部署脚本上传失败" }

# ---------- 3. 服务器上执行 ----------
Write-Host "[3/4] 在服务器上替换并重启 ..." -ForegroundColor Cyan
& ssh -o BatchMode=yes $Server "bash /tmp/remote-deploy.sh $sha"
$code = $LASTEXITCODE

# ---------- 4. 结果 ----------
$hostOnly = $Server -replace '^.*@', ''
Write-Host ""
if ($code -ne 0) {
    Write-Host "[4/4] 部署失败，退出码 $code" -ForegroundColor Red
    Write-Host "      卡在「等待就绪」时服务器已自动回滚；若是接口验证失败，请按上面的提示手工回滚。" -ForegroundColor DarkGray
    exit $code
}
Write-Host "[4/4] 部署成功" -ForegroundColor Green
Write-Host ""
Write-Host "  访问:  http://$hostOnly/" -ForegroundColor White
Write-Host "  后台:  http://$hostOnly/admin.html" -ForegroundColor White
Write-Host "  日志:  ssh $Server 'journalctl -u shiguang-blog -f'" -ForegroundColor DarkGray
Write-Host ""
