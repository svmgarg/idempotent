# Automated Deployment Guide

This directory contains the automated deployment script for the Idempotency Service.

## Quick Start

### Deploy to Server (Manual Control)

```powershell
# From project root
.\deploy.ps1 -ServerIP 144.24.119.46 -SSHKeyPath "$env:USERPROFILE\.ssh\id_rsa"
```

---

## PowerShell Deployment Script

### Prerequisites

1. **Java 17+** - For building the project
2. **Maven 3.8+** - For building the JAR
3. **SSH Access** - Access to remote server
4. **SSH Key** - Private key for authentication

### Usage

```powershell
# Basic deployment (with defaults)
.\deploy.ps1

# Custom server IP
.\deploy.ps1 -ServerIP 192.168.1.100

# Custom SSH key path
.\deploy.ps1 -SSHKeyPath "C:\Users\username\.ssh\my-key"

# Skip tests (faster deployment)
.\deploy.ps1 -RunTests $false

# Skip build (deploy existing JAR)
.\deploy.ps1 -BuildJar $false

# Combined options
.\deploy.ps1 -ServerIP 144.24.119.46 -RunTests $false -RestartService $true
```

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `ServerIP` | 144.24.119.46 | Target server IP address |
| `ServerPort` | 8080 | Service port |
| `SSHUser` | opc | SSH user on remote server |
| `SSHKeyPath` | `~/.ssh/id_rsa` | Path to private SSH key |
| `RemoteAppPath` | `/opt/idempotency-service` | Application directory on server |
| `RunTests` | $true | Run unit tests before deployment |
| `BuildJar` | $true | Build JAR file |
| `RestartService` | $true | Restart service after deployment |

### Deployment Flow

1. ✓ Check prerequisites (Maven, SSH, connectivity)
2. ✓ Build project (`mvn clean package`)
3. ✓ Run tests (optional)
4. ✓ Upload JAR to server
5. ✓ Backup existing deployment
6. ✓ Restart service
7. ✓ Verify deployment (health check)

### Example Output

```
[15:30:42] IDEMPOTENCY SERVICE - DEPLOYMENT SCRIPT
[15:30:42] ========================================
[15:30:42] Target Server: opc@144.24.119.46
[15:30:42] Port: 8080
[15:30:42] ✓ SSH connection successful
[15:30:45] ✓ Build successful - JAR size: 45.23 MB
[15:30:52] ✓ All tests passed
[15:31:02] ✓ JAR file uploaded successfully
[15:31:05] ✓ Service started successfully
[15:31:08] ✓ Service is healthy and responding
[15:31:08] Deployment completed in 26 seconds

✓ Deployment Completed Successfully
```

---

## Server Rollback

### If Deployment Fails

Connect to server and restore backup:

```bash
ssh opc@144.24.119.46
cd /opt/idempotency-service

# Restart service
sudo systemctl restart idempotency-service

# Check status
sudo systemctl status idempotency-service
tail -f service.log
```

### View Deployment Logs

```bash
ssh ubuntu@144.24.119.46
tail -f /opt/idempotency-service/service.log
```

---

## Production Configuration

### Systemd Service (on server)

Create `/etc/systemd/system/idempotency-service.service`:

```ini
[Unit]
Description=Idempotency Service
After=network.target

[Service]
Type=simple
User=opc
WorkingDirectory=/opt/idempotency-service
ExecStart=/usr/bin/java -jar /opt/idempotency-service/app.jar \
  --server.port=8080 \
  --idempotency.storage=redis \
  --spring.data.redis.host=localhost \
  --logging.level.com.idempotent=INFO
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl enable idempotency-service
sudo systemctl start idempotency-service
sudo systemctl status idempotency-service
```

---

## Troubleshooting

### SSH Connection Error

```
Error: Cannot connect to server
```

**Solution:**
```bash
# Check SSH key permissions
chmod 600 ~/.ssh/id_rsa

# Test connection
ssh -i ~/.ssh/id_rsa ubuntu@144.24.119.46

# Add to known_hosts
ssh-keyscan -H 144.24.119.46 >> ~/.ssh/known_hosts
```

### Maven Not Found

```
Error: Maven not found
```

**Solution:**
```bash
# Install Maven
choco install maven  # Windows
brew install maven   # macOS
sudo apt install maven  # Linux

# Verify
mvn -v
```

### Service Won't Start

```bash
# Check service logs
tail -f /opt/idempotency-service/service.log

# Check if port is in use
sudo netstat -tlnp | grep 8080

# Kill process on port
sudo lsof -i :8080
kill -9 <PID>
```

### Health Check Fails

```bash
# SSH to server
ssh opc@144.24.119.46

# Check service status
sudo systemctl status idempotency-service

# Check if Java process is running
ps aux | grep java

# Check port
sudo netstat -tlnp | grep 8080

# View logs
tail -100 /opt/idempotency-service/service.log
```

---

## Environment Variables

Set on the server for Redis configuration:

```bash
export REDIS_HOST=localhost
export REDIS_PORT=6379
export IDEMPOTENCY_STORAGE=redis
export IDEMPOTENCY_DEFAULT_TTL_SECONDS=3600
```

---

## Monitoring & Health Checks

### Health Endpoint

```bash
curl -H "X-API-KEY: oUNtfxXl" http://144.24.119.46:8080/idempotency/health
```

### API Test

```bash
curl -X POST http://144.24.119.46:8080/idempotency/check \
  -H "X-API-KEY: oUNtfxXl" \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "test-key"}'
```

---

## Security Notes

⚠️ **Important:**

- Never commit SSH private keys to git
- Store SSH keys in `~/.ssh/` with permissions `600`
- Use environment variables for production secrets
- Rotate API keys regularly
- Keep deployment scripts in `.gitignore`

---

## Support & Debugging

For more information, see:
- [Service README](../idempotent-service/README.md)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
