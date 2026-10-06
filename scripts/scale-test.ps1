# Sends requests through the load balancer and shows:
#   - how many were allowed (200) vs blocked (429)
#   - which app copy served each allowed request
# Usage (from the project folder):  .\scripts\scale-test.ps1
#                                    .\scripts\scale-test.ps1 -Requests 50
param(
    [int]$Requests = 30,
    [string]$Url = "http://localhost:8000/api/orders",
    [string]$Client = "scale-test-$(Get-Random)"    # a fresh client = a full bucket of 10 (free tier)
)

$codes = @{}
$instances = @{}
$watch = [System.Diagnostics.Stopwatch]::StartNew()

for ($i = 1; $i -le $Requests; $i++) {
    try {
        $r = Invoke-WebRequest -Uri $Url -Headers @{ "X-Client-Id" = $Client } -UseBasicParsing
        $code = [int]$r.StatusCode
        $instance = ($r.Content | ConvertFrom-Json).instance
        $instances[$instance]++
    } catch {
        $code = [int]$_.Exception.Response.StatusCode
    }
    $codes[$code]++
}
$watch.Stop()

$seconds = [math]::Round($watch.Elapsed.TotalSeconds, 1)
$expected = 10 + [math]::Floor($watch.Elapsed.TotalSeconds)   # bucket of 10 + 1 token per second

Write-Host ""
Write-Host "Client:   $Client  (free tier: bucket 10, refill 1/sec)"
Write-Host "Sent:     $Requests requests in $seconds s"
Write-Host "Allowed:  $($codes[200])   (expected about $expected for ONE shared bucket)"
Write-Host "Blocked:  $($codes[429])"
Write-Host ""
Write-Host "Allowed requests per app copy:"
$instances.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ("  {0}  {1}" -f $_.Name, $_.Value) }
