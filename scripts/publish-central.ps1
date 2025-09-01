# 发布到 Maven Central
# 用法：
#   $env:SONATYPE_USERNAME = "<token-username>"
#   $env:SONATYPE_PASSWORD = "<token-password>"
#   .\scripts\publish-central.ps1

$ErrorActionPreference = "Stop"

if (-not $env:SONATYPE_USERNAME -or -not $env:SONATYPE_PASSWORD) {
    Write-Error @"
缺少 Sonatype User Token。请先在 https://central.sonatype.com/account 生成 Token，然后执行：

  `$env:SONATYPE_USERNAME = '<token-username>'
  `$env:SONATYPE_PASSWORD = '<token-password>'
  .\scripts\publish-central.ps1
"@
}

$settingsPath = Join-Path $env:USERPROFILE ".m2\settings-central.xml"
@"

<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <servers>
    <server>
      <id>central</id>
      <username>$($env:SONATYPE_USERNAME)</username>
      <password>$($env:SONATYPE_PASSWORD)</password>
    </server>
  </servers>
</settings>
"@ | Set-Content -Path $settingsPath -Encoding UTF8

$mvn = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $mvn) {
    Write-Error "未找到 Maven，请安装 Maven 或配置 Maven Wrapper。"
}

$root = Split-Path -Parent $PSScriptRoot
& $mvn -s $settingsPath -f (Join-Path $root "pom.xml") clean deploy -Prelease -DskipTests
