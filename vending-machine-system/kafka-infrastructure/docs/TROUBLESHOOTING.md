# 🔍 Troubleshooting - Kafka Infrastructure

## 🚨 Common Issues and Solutions

### 1. Kafka Fails to Start Properly

#### Symptoms - Kafka Fails to Start

- Containers stop unexpectedly
- Error: "Connection to node -1 could not be established"
- Logs show connection errors with Zookeeper

#### Solutions

```powershell
# Check that Docker is running
docker version

# Clean up existing containers
.\scripts\kafka-manager.ps1 stop
docker system prune -f

# Verify available ports
netstat -an | findstr ":9092"
netstat -an | findstr ":2181"
netstat -an | findstr ":9090"

# Restart from scratch
.\scripts\kafka-manager.ps1 start
```

#### Verification

```powershell
# Check container status
docker ps --filter "name=vending-"

# Check logs
docker logs vending-zookeeper
docker logs vending-kafka
docker logs vending-kafka-ui
```

### 2. Microservices Cannot Connect to Kafka

#### Symptoms - Connection Failures

- Error: "Failed to send ProducerRecord"
- "Connection to localhost:9092 failed"
- Connection timeouts in microservice logs

#### Connectivity Solutions

```powershell
# Check Kafka status
.\scripts\kafka-manager.ps1 status

# Test connectivity from host
telnet localhost 9092

# Inspect network settings
docker network ls
docker network inspect vending-machine-system_vending-kafka-network
```

#### Microservice Configuration

```properties
# In application.properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.group-id=vending-machine-group
```

### 3. Port Conflicts

#### Symptoms - Port Already in Use

- Error: "Port already in use"
- Cannot bind to localhost:9092, 2181, or 9090

#### Port Solutions

```powershell
# Check what's using the ports
netstat -ano | findstr ":9092"
netstat -ano | findstr ":2181"
netstat -ano | findstr ":9090"

# Kill processes if necessary (be careful)
taskkill /PID <process_id> /F

# Or change ports in docker-compose.yml
# Example: "9093:9092" instead of "9092:9092"
```

### 4. Kafka UI Is Not Accessible

#### Symptoms - UI Unreachable

- 404 Error at <http://localhost:9090>
- "This site can't be reached"
- Blank page

#### UI Solutions

```powershell
# Check that the container is running
docker ps --filter "name=vending-kafka-ui"

# Check UI logs
docker logs vending-kafka-ui

# Verify port usage
netstat -an | findstr ":9090"

# Restart only Kafka UI
docker restart vending-kafka-ui
```

### 5. Messages Are Not Consumed

#### Symptoms - No Consumers

- Producers send but consumers do not receive
- Consumer group lag increases
- Pending messages accumulate

#### Diagnosis

```powershell
# List consumer groups
docker exec vending-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list

# Describe a specific group
docker exec vending-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group payment-group

# Read messages from topic
docker exec vending-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic transaction-events --from-beginning
```

#### Consumer Solutions

```java
// Verify consumer configuration
@KafkaListener(topics = "transaction-events", groupId = "payment-group")
public void handleMessage(String message) {
    log.info("Received: {}", message);
}
```

### 6. High Memory Usage

#### Symptoms - Resource Issues

- Docker containers consuming too much memory
- System becoming slow
- OutOfMemoryError in logs

#### Memory Solutions

```yaml
# Add memory limits to docker-compose.yml
kafka:
  mem_limit: 1g
  environment:
    KAFKA_HEAP_OPTS: "-Xmx512M -Xms512M"

zookeeper:
  mem_limit: 512m
```

### 7. Topics Not Created

#### Symptoms - Missing Topics

- "Topic does not exist" errors
- Empty topic list
- Producers failing to send

#### Topic Solutions

```powershell
# Manually create topics
.\scripts\kafka-manager.ps1 create-topics

# Or create individual topics
docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic transaction-events --partitions 1 --replication-factor 1

# List topics to verify
docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### 8. Docker Volume Issues

#### Symptoms - Data Persistence

- Data lost on restart
- Permission denied errors
- Volume mount failures

#### Volume Solutions

```powershell
# Clean up volumes (WARNING: This removes data)
docker-compose down -v
docker volume prune

# Restart with fresh volumes
.\scripts\kafka-manager.ps1 start

# Check volume status
docker volume ls
docker volume inspect kafka-infrastructure_kafka-data
```

## 📊 Proactive Monitoring

### Automated Health Checks

```powershell
# Kafka health check script
function Test-VendingKafkaHealth {
    Write-Host "Checking Vending Machine Kafka health..." -ForegroundColor Yellow

    # Check containers
    $containers = docker ps --filter "name=vending-" --format "{{.Names}}" 2>$null
    if ($containers.Count -lt 3) {
        Write-Host "Not all containers are running" -ForegroundColor Red
        return $false
    }

    # Check connectivity
    docker exec vending-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Kafka is not responding" -ForegroundColor Red
        return $false
    }

    Write-Host "Vending Machine Kafka is healthy" -ForegroundColor Green
    return $true
}
```

### Log Alerts

```powershell
# Search for errors in logs
docker logs vending-kafka 2>&1 | Select-String -Pattern "ERROR|WARN|Exception"
docker logs vending-zookeeper 2>&1 | Select-String -Pattern "ERROR|WARN|Exception"
docker logs vending-kafka-ui 2>&1 | Select-String -Pattern "ERROR|WARN|Exception"
```

## 🆘 Escalation Procedures

### Information to Collect

```powershell
# Generate diagnostic report
$reportPath = "vending-kafka-diagnostic-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"

@"
=== VENDING MACHINE KAFKA DIAGNOSTIC REPORT ===
Date: $(Get-Date)

=== CONTAINER STATUS ===
$(docker ps --filter "name=vending-")

=== KAFKA LOGS (last 50 lines) ===
$(docker logs vending-kafka --tail 50 2>&1)

=== ZOOKEEPER LOGS (last 50 lines) ===
$(docker logs vending-zookeeper --tail 50 2>&1)

=== TOPICS ===
$(docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>&1)

=== CONSUMER GROUPS ===
$(docker exec vending-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>&1)

=== DOCKER SYSTEM INFO ===
$(docker system df)

=== DOCKER VERSION ===
$(docker version)
"@ | Out-File -FilePath $reportPath

Write-Host "Diagnostic report created: $reportPath" -ForegroundColor Green
```

## 🔄 Recovery Procedures

### Quick Recovery

```powershell
# Stop services
.\scripts\kafka-manager.ps1 stop

# Wait a moment
Start-Sleep -Seconds 5

# Start services
.\scripts\kafka-manager.ps1 start

# Verify status
.\scripts\kafka-manager.ps1 status
```

### Full Recovery

```powershell
# Full stop and cleanup
.\scripts\kafka-manager.ps1 stop
docker system prune -f
docker volume prune -f

# Full restart
.\scripts\kafka-manager.ps1 start

# Verification
.\scripts\kafka-manager.ps1 status
.\scripts\kafka-manager.ps1 topics
```

### Data Recovery

```powershell
# Backup important topics before issues
docker exec vending-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic transaction-events --from-beginning > backup-transaction-events.json

# Restore messages after recovery
docker exec -i vending-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic transaction-events < backup-transaction-events.json
```

---

**Remember**: Most issues are resolved with a full stack restart. Always check logs to identify the root cause.
