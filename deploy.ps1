# Idempotency Service - Automated Deployment Script
# Deploys the service to remote server and handles build, test, and deployment

param(
    [string]$ServerIP = "144.24.119.46",
    [string]$ServerPort = "8080",
    [string]$SSHUser = "opc",
    [string]$SSHKeyPath = "$env:USERPROFILE\.ssh\id_rsa",
    [string]$RemoteAppPath = "/opt/idempotency-service",
    [bool]$RunTests = $true,
    [bool]$BuildJar = $true,
    [bool]$RestartService = $true
)

# Configuration
$ProjectPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$ServiceDir = Join-Path $ProjectPath "idempotent-service"
$RemoteServer = "$SSHUser@$ServerIP"
$JarFile = "target/idempotency-service-1.0.0.jar"
$RemoteJarPath = "$RemoteAppPath/app.jar"
$RemoteBackupPath = "$RemoteAppPath/app.jar.backup"

# Colors for output
$SuccessColor = "Green"
$ErrorColor = "Red"
$InfoColor = "Cyan"
$WarningColor = "Yellow"

function Write-Log {
    param([string]$Message, [string]$Color = "White")
    Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $Message" -ForegroundColor $Color
}

function Check-Prerequisites {
    Write-Log "Checking prerequisites..." -Color $InfoColor
    
    # Check Maven
    try {
        $mavenVersion = mvn -v 2>&1 | Select-Object -First 1
        Write-Log "✓ Maven: $mavenVersion" -Color $SuccessColor
    }
    catch {
        Write-Log "✗ Maven not found. Please install Maven." -Color $ErrorColor
        exit 1
    }
    
    # Check SSH Key
    if (-not (Test-Path $SSHKeyPath)) {
        Write-Log "✗ SSH key not found at: $SSHKeyPath" -Color $ErrorColor
        exit 1
    }
    Write-Log "✓ SSH key found" -Color $SuccessColor
    
    # Check connectivity to server
    Write-Log "Testing SSH connection to $RemoteServer..." -Color $InfoColor
    $sshTest = ssh -i $SSHKeyPath -o ConnectTimeout=5 $RemoteServer "echo 'Connection OK'" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Log "✓ SSH connection successful" -Color $SuccessColor
    }
    else {
        Write-Log "✗ Cannot connect to server: $RemoteServer" -Color $ErrorColor
        exit 1
    }
}

function Build-Project {
    Write-Log "`n========================================" -Color $InfoColor
    Write-Log "Building Project..." -Color $InfoColor
    Write-Log "========================================" -Color $InfoColor
    
    Set-Location $ServiceDir
    
    # Clean previous build
    Write-Log "Running: mvn clean package..." -Color $InfoColor
    $buildOutput = & mvn clean package -DskipTests:$(!$RunTests)
    
    if ($LASTEXITCODE -ne 0) {
        Write-Log "✗ Build failed" -Color $ErrorColor
        exit 1
    }
    
    # Verify JAR file exists
    if (-not (Test-Path $JarFile)) {
        Write-Log "✗ JAR file not created at: $JarFile" -Color $ErrorColor
        exit 1
    }
    
    $jarSize = (Get-Item $JarFile).Length / 1MB
    Write-Log "✓ Build successful - JAR size: $([Math]::Round($jarSize, 2)) MB" -Color $SuccessColor
}

function Run-Tests {
    if (-not $RunTests) {
        Write-Log "Skipping unit tests (RunTests=false)" -Color $WarningColor
        return
    }
    
    Write-Log "`n========================================" -Color $InfoColor
    Write-Log "Running Unit Tests..." -Color $InfoColor
    Write-Log "========================================" -Color $InfoColor
    
    Set-Location $ServiceDir
    $testOutput = & mvn test
    
    if ($LASTEXITCODE -ne 0) {
        Write-Log "✗ Tests failed" -Color $ErrorColor
        exit 1
    }
    
    Write-Log "✓ All tests passed" -Color $SuccessColor
}

function Deploy-ToServer {
    Write-Log "`n========================================" -Color $InfoColor
    Write-Log "Deploying to Server..." -Color $InfoColor
    Write-Log "========================================" -Color $InfoColor
    
    # Create remote directory if it doesn't exist
    Write-Log "Setting up remote directory: $RemoteAppPath" -Color $InfoColor
    ssh -i $SSHKeyPath $RemoteServer "sudo mkdir -p $RemoteAppPath && sudo chown $SSHUser $RemoteAppPath"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Log "✗ Failed to create remote directory" -Color $ErrorColor
        exit 1
    }
    
    # Copy JAR file to server (no backup)
    Write-Log "Uploading JAR file..." -Color $InfoColor
    $jarPath = Join-Path $ServiceDir $JarFile
    scp -i $SSHKeyPath $jarPath "${RemoteServer}:${RemoteJarPath}"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Log "✗ Failed to upload JAR file" -Color $ErrorColor
        exit 1
    }
    
    Write-Log "✓ JAR file uploaded successfully" -Color $SuccessColor
}

function Restart-Service {
    if (-not $RestartService) {
        Write-Log "Skipping service restart (RestartService=false)" -Color $WarningColor
        return
    }
    
    Write-Log "`n========================================" -Color $InfoColor
    Write-Log "Restarting Service..." -Color $InfoColor
    Write-Log "========================================" -Color $InfoColor
    
    # Stop old service
    Write-Log "Stopping service..." -Color $InfoColor
    ssh -i $SSHKeyPath $RemoteServer "sudo systemctl stop idempotency-service 2>/dev/null || true"
    Start-Sleep -Seconds 2
    
    # Start new service
    Write-Log "Starting service on port $ServerPort..." -Color $InfoColor
    ssh -i $SSHKeyPath $RemoteServer @"
        cd $RemoteAppPath
        nohup java -jar app.jar --server.port=$ServerPort > service.log 2>&1 &
        sleep 3
        echo 'Service started with PID:' \$(pgrep -f 'java.*app.jar')
"@
    
    if ($LASTEXITCODE -ne 0) {
        Write-Log "✗ Failed to start service" -Color $ErrorColor
        exit 1
    }
    
    Write-Log "✓ Service started successfully" -Color $SuccessColor
}

function Verify-Deployment {
    Write-Log "`n========================================" -Color $InfoColor
    Write-Log "Verifying Deployment..." -Color $InfoColor
    Write-Log "========================================" -Color $InfoColor
    
    $maxAttempts = 10
    $attempt = 0
    $healthUrl = "http://${ServerIP}:${ServerPort}/idempotency/ping"
    
    while ($attempt -lt $maxAttempts) {
        $attempt++
        Write-Log "Health check attempt $attempt/$maxAttempts..." -Color $InfoColor
        
        try {
            $response = Invoke-WebRequest -Uri $healthUrl -TimeoutSec 5 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Log "✓ Service is healthy and responding" -Color $SuccessColor
                Write-Log "  Endpoint: $healthUrl" -Color $SuccessColor
                return $true
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }
    
    Write-Log "✗ Service health check failed after $maxAttempts attempts" -Color $ErrorColor
    Write-Log "  Please check logs on server: ssh $RemoteServer" -Color $WarningColor
    Write-Log "  Command: tail -f $RemoteAppPath/service.log" -Color $WarningColor
    return $false
}

function Show-Summary {
    Write-Log "`n========================================" -Color $InfoColor
    Write-Log "Deployment Summary" -Color $InfoColor
    Write-Log "========================================" -Color $InfoColor
    
    Write-Host @"
✓ Deployment Completed Successfully

Server Details:
  - IP Address: $ServerIP
  - Port: $ServerPort
  - User: $SSHUser
  - App Path: $RemoteAppPath

Service URLs:
  - Health Check: http://$ServerIP:$ServerPort/idempotency/health
  - Ping: http://$ServerIP:$ServerPort/idempotency/ping
  - Documentation: http://$ServerIP:$ServerPort/

Remote Access:
  - SSH: ssh -i $SSHKeyPath $RemoteServer
  - View Logs: tail -f $RemoteAppPath/service.log
  - Stop Service: sudo systemctl stop idempotency-service
  - Start Service: sudo systemctl start idempotency-service

Rollback (if needed):
  - Restore backup: sudo cp $RemoteBackupPath $RemoteJarPath
  - Restart service: sudo systemctl restart idempotency-service

"@ -ForegroundColor $SuccessColor
}

# Main Deployment Flow
function Start-Deployment {
    Write-Log "========================================" -Color $InfoColor
    Write-Log "IDEMPOTENCY SERVICE - DEPLOYMENT SCRIPT" -Color $InfoColor
    Write-Log "========================================" -Color $InfoColor
    Write-Log "Target Server: $RemoteServer" -Color $InfoColor
    Write-Log "Port: $ServerPort" -Color $InfoColor
    Write-Log "Build JAR: $BuildJar" -Color $InfoColor
    Write-Log "Run Tests: $RunTests" -Color $InfoColor
    Write-Log "Restart Service: $RestartService" -Color $InfoColor
    
    $startTime = Get-Date
    
    try {
        Check-Prerequisites
        
        if ($BuildJar) {
            Build-Project
            Run-Tests
        }
        
        Deploy-ToServer
        Restart-Service
        
        $healthOk = Verify-Deployment
        
        $duration = (Get-Date) - $startTime
        Write-Log "`nDeployment completed in $($duration.TotalSeconds) seconds" -Color $SuccessColor
        
        if ($healthOk) {
            Show-Summary
            exit 0
        }
        else {
            exit 1
        }
    }
    catch {
        Write-Log "✗ Deployment failed: $_" -Color $ErrorColor
        exit 1
    }
}

# Run deployment
Start-Deployment
