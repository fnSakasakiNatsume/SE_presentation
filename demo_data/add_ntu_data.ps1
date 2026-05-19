# 新增校园商铺数据后执行缓存预热（与 PPT Slide 15 一致）
# 用法: .\demo_data\add_ntu_data.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "==> 运行 ShopCacheTest.testCacheAllShops 预热商铺逻辑过期缓存"
Push-Location $root
mvn -q test -Dtest=com.hmdp.ShopCacheTest#testCacheAllShops
Pop-Location

Write-Host "==> 缓存预热完成"
