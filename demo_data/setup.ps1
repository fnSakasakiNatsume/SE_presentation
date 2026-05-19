# NTU Smarthub — JMeter 压测环境准备（与 PPT Slide 21 一致）
# 用法: .\demo_data\setup.ps1 [-MySqlUser root] [-MySqlPassword xxx] [-RedisHost 127.0.0.1] [-RedisPort 6379]

param(
    [string]$MySqlHost = "127.0.0.1",
    [int]$MySqlPort = 3306,
    [string]$MySqlUser = "root",
    [string]$MySqlPassword = "",
    [string]$MySqlDatabase = "hmdp",
    [string]$RedisHost = "127.0.0.1",
    [int]$RedisPort = 6379,
    [int]$TokenCount = 500,
    [int]$SeckillStock = 100,
    [long]$ShopId = 1
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$tokensFile = Join-Path $root "jmeter\tokens.csv"

Write-Host "==> 1/5 清空秒杀订单表"
$truncateSql = "TRUNCATE TABLE tb_voucher_order;"
if ($MySqlPassword) {
    mysql -h $MySqlHost -P $MySqlPort -u $MySqlUser "-p$MySqlPassword" $MySqlDatabase -e $truncateSql
} else {
    mysql -h $MySqlHost -P $MySqlPort -u $MySqlUser $MySqlDatabase -e $truncateSql
}

Write-Host "==> 2/5 清空 Redis seckill:* 键"
redis-cli -h $RedisHost -p $RedisPort --scan --pattern "seckill:*" | ForEach-Object {
    redis-cli -h $RedisHost -p $RedisPort DEL $_ | Out-Null
}

Write-Host "==> 3/5 创建 $SeckillStock 库存秒杀券"
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$end = (Get-Date).AddDays(7).ToString("yyyy-MM-dd HH:mm:ss")
$insertVoucher = @"
INSERT INTO tb_voucher (shop_id, title, sub_title, rules, pay_value, actual_value, type, status, create_time, update_time, stock, begin_time, end_time)
VALUES ($ShopId, 'JMeter Seckill Voucher', 'Stress test', 'One per user', 100, 200, 1, 1, '$now', '$now', $SeckillStock, '$now', '$end');
SET @vid = LAST_INSERT_ID();
INSERT INTO tb_seckill_voucher (voucher_id, stock, begin_time, end_time, create_time, update_time)
VALUES (@vid, $SeckillStock, '$now', '$end', '$now', '$now');
SELECT @vid AS voucher_id;
"@

if ($MySqlPassword) {
    $voucherResult = mysql -h $MySqlHost -P $MySqlPort -u $MySqlUser "-p$MySqlPassword" $MySqlDatabase -N -e $insertVoucher
} else {
    $voucherResult = mysql -h $MySqlHost -P $MySqlPort -u $MySqlUser $MySqlDatabase -N -e $insertVoucher
}
$voucherId = ($voucherResult | Select-Object -Last 1).Trim()
Write-Host "    voucher_id = $voucherId"

redis-cli -h $RedisHost -p $RedisPort SET "seckill:stock:$voucherId" $SeckillStock | Out-Null

Write-Host "==> 4/5 注入 $TokenCount 个登录 token 到 Redis"
$tokensDir = Split-Path $tokensFile -Parent
if (-not (Test-Path $tokensDir)) { New-Item -ItemType Directory -Path $tokensDir | Out-Null }

$userRows = if ($MySqlPassword) {
    mysql -h $MySqlHost -P $MySqlPort -u $MySqlUser "-p$MySqlPassword" $MySqlDatabase -N -e "SELECT id, nick_name, icon FROM tb_user LIMIT $TokenCount;"
} else {
    mysql -h $MySqlHost -P $MySqlPort -u $MySqlUser $MySqlDatabase -N -e "SELECT id, nick_name, icon FROM tb_user LIMIT $TokenCount;"
}

$tokenLines = New-Object System.Collections.Generic.List[string]
foreach ($row in $userRows) {
    if ([string]::IsNullOrWhiteSpace($row)) { continue }
    $parts = $row -split "`t"
    if ($parts.Count -lt 3) { continue }
    $uid, $nick, $icon = $parts[0], $parts[1], $parts[2]
    $token = [guid]::NewGuid().ToString("N")
    redis-cli -h $RedisHost -p $RedisPort HSET "login:token:$token" id $uid nickName $nick icon $icon | Out-Null
    redis-cli -h $RedisHost -p $RedisPort EXPIRE "login:token:$token" 1800 | Out-Null
    $tokenLines.Add($token)
}

Write-Host "==> 5/5 写入 JMeter tokens.csv"
$tokenLines | Set-Content -Path $tokensFile -Encoding UTF8

$metaFile = Join-Path $tokensDir "seckill-meta.env"
@(
    "VOUCHER_ID=$voucherId"
    "SHOP_ID=$ShopId"
    "SECKILL_STOCK=$SeckillStock"
) | Set-Content -Path $metaFile -Encoding UTF8

Write-Host ""
Write-Host "完成。请在 JMeter 中加载 jmeter/NTU-Smarthub.jmx"
Write-Host "  tokens: $tokensFile"
Write-Host "  meta:   $metaFile"
