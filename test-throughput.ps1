# Idempotency Service Throughput Test Script
# Generates random keys and measures API performance

param(
    [string]$ApiUrl = "http://144.24.119.46:8080/idempotency/check",
    [string]$ApiKey = "oUNtfxXl",
    [int]$NumberOfRequests = 100,
    [int]$ConcurrentRequests = 10
)

# Colors for output
$SuccessColor = "Green"
$ErrorColor = "Red"
$InfoColor = "Cyan"

Write-Host "========================================" -ForegroundColor $InfoColor
Write-Host "Idempotency Service Throughput Test" -ForegroundColor $InfoColor
Write-Host "========================================" -ForegroundColor $InfoColor
Write-Host "API URL: $ApiUrl" -ForegroundColor $InfoColor
Write-Host "Total Requests: $NumberOfRequests" -ForegroundColor $InfoColor
Write-Host "Concurrent Requests: $ConcurrentRequests" -ForegroundColor $InfoColor
Write-Host "========================================`n" -ForegroundColor $InfoColor

# Initialize counters
$successCount = 0
$failureCount = 0
$duplicateCount = 0
$newKeyCount = 0
$totalTime = 0
$responseTimes = @()

# Function to generate random string
function Get-RandomKey {
    param([int]$Length = 16)
    $chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    $random = New-Object System.Random
    $result = ""
    for ($i = 0; $i -lt $Length; $i++) {
        $result += $chars[$random.Next($chars.Length)]
    }
    return $result
}

# Function to make API request
function Invoke-IdempotencyCheck {
    param(
        [string]$IdempotencyKey,
        [string]$ClientId
    )
    
    $startTime = Get-Date
    $headers = @{
        "Content-Type" = "application/json"
        "X-API-KEY" = $ApiKey
    }
    
    $body = @{
        idempotencyKey = $IdempotencyKey
        clientId = $ClientId
    } | ConvertTo-Json
    
    try {
        $response = Invoke-WebRequest -Uri $ApiUrl -Method POST -Headers $headers -Body $body -TimeoutSec 30
        $endTime = Get-Date
        $duration = ($endTime - $startTime).TotalMilliseconds
        
        $responseBody = $response.Content | ConvertFrom-Json
        
        return @{
            Success = $true
            StatusCode = $response.StatusCode
            IsDuplicate = $responseBody.isDuplicate
            Duration = $duration
            Error = $null
        }
    }
    catch {
        $endTime = Get-Date
        $duration = ($endTime - $startTime).TotalMilliseconds
        
        return @{
            Success = $false
            StatusCode = $_.Exception.Response.StatusCode
            IsDuplicate = $null
            Duration = $duration
            Error = $_.Exception.Message
        }
    }
}

# Perform sequential requests first
Write-Host "Starting throughput test..." -ForegroundColor $InfoColor
$testStartTime = Get-Date

for ($i = 1; $i -le $NumberOfRequests; $i++) {
    $randomKey = Get-RandomKey
    $clientId = "client-$(Get-Random -Minimum 1 -Maximum 5)"
    
    $result = Invoke-IdempotencyCheck -IdempotencyKey $randomKey -ClientId $clientId
    
    if ($result.Success) {
        $successCount++
        if ($result.IsDuplicate) {
            $duplicateCount++
        }
        else {
            $newKeyCount++
        }
    }
    else {
        $failureCount++
    }
    
    $responseTimes += $result.Duration
    $totalTime += $result.Duration
    
    # Progress indicator
    if ($i % 10 -eq 0) {
        Write-Host "Completed: $i/$NumberOfRequests requests" -ForegroundColor $InfoColor
    }
}

$testEndTime = Get-Date
$totalElapsedSeconds = ($testEndTime - $testStartTime).TotalSeconds
$throughput = $NumberOfRequests / $totalElapsedSeconds

# Calculate statistics
$avgResponseTime = $responseTimes | Measure-Object -Average | Select-Object -ExpandProperty Average
$minResponseTime = $responseTimes | Measure-Object -Minimum | Select-Object -ExpandProperty Minimum
$maxResponseTime = $responseTimes | Measure-Object -Maximum | Select-Object -ExpandProperty Maximum
$p95ResponseTime = ($responseTimes | Sort-Object)[[int]($responseTimes.Count * 0.95)]
$p99ResponseTime = ($responseTimes | Sort-Object)[[int]($responseTimes.Count * 0.99)]

# Display results
Write-Host "`n========================================" -ForegroundColor $InfoColor
Write-Host "Test Results" -ForegroundColor $InfoColor
Write-Host "========================================" -ForegroundColor $InfoColor

Write-Host "Total Requests: $NumberOfRequests" -ForegroundColor $SuccessColor
Write-Host "Successful: $successCount" -ForegroundColor $SuccessColor
Write-Host "Failed: $failureCount" -ForegroundColor $(if ($failureCount -gt 0) { $ErrorColor } else { $SuccessColor })
Write-Host "New Keys: $newKeyCount" -ForegroundColor $SuccessColor
Write-Host "Duplicates: $duplicateCount" -ForegroundColor $InfoColor

Write-Host "`n========================================" -ForegroundColor $InfoColor
Write-Host "Performance Metrics" -ForegroundColor $InfoColor
Write-Host "========================================" -ForegroundColor $InfoColor

Write-Host "Total Time: $([Math]::Round($totalElapsedSeconds, 2)) seconds" -ForegroundColor $InfoColor
Write-Host "Throughput: $([Math]::Round($throughput, 2)) requests/sec" -ForegroundColor $SuccessColor
Write-Host "Avg Response Time: $([Math]::Round($avgResponseTime, 2)) ms" -ForegroundColor $InfoColor
Write-Host "Min Response Time: $([Math]::Round($minResponseTime, 2)) ms" -ForegroundColor $SuccessColor
Write-Host "Max Response Time: $([Math]::Round($maxResponseTime, 2)) ms" -ForegroundColor $(if ($maxResponseTime -gt 1000) { $ErrorColor } else { $SuccessColor })
Write-Host "P95 Response Time: $([Math]::Round($p95ResponseTime, 2)) ms" -ForegroundColor $InfoColor
Write-Host "P99 Response Time: $([Math]::Round($p99ResponseTime, 2)) ms" -ForegroundColor $InfoColor

Write-Host "`n========================================" -ForegroundColor $InfoColor
Write-Host "Success Rate: $(([Math]::Round(($successCount / $NumberOfRequests) * 100, 2)))%" -ForegroundColor $(if ($successCount -eq $NumberOfRequests) { $SuccessColor } else { $ErrorColor })
Write-Host "========================================`n" -ForegroundColor $InfoColor
