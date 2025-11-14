# Script to manage Kafka with Docker for the Vending Machine project
# Usage: .\kafka-manager.ps1 [start|stop|restart|status|logs|topics]

param(
    [Parameter(Position=0)]
    [ValidateSet("start", "stop", "restart", "status", "logs", "topics", "create-topics", "help")]
    [string]$Action = "help"
)

$ComposeFile = "docker-compose.yml"

function ShowHelp {
    Write-Host "=== Kafka Manager for Vending Machine Project ===" -ForegroundColor Green
    Write-Host ""
    Write-Host "Available commands:" -ForegroundColor Yellow
    Write-Host "  start         - Starts Kafka and Zookeeper"
    Write-Host "  stop          - Stops Kafka and Zookeeper"
    Write-Host "  restart       - Restarts the services"
    Write-Host "  status        - Shows the status of the containers"
    Write-Host "  logs          - Displays Kafka logs"
    Write-Host "  topics        - Lists existing topics"
    Write-Host "  create-topics - Creates the necessary topics for the microservices"
    Write-Host "  help          - Shows this help message"
    Write-Host ""
    Write-Host "Example: .\kafka-manager.ps1 start" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Useful URLs:" -ForegroundColor Yellow
    Write-Host "  Kafka UI: http://localhost:9090"
    Write-Host "  Kafka Bootstrap Servers: localhost:9092"
}

function StartKafka {
    Write-Host "Starting Kafka and Zookeeper for Vending Machine..." -ForegroundColor Green
    docker-compose -f $ComposeFile up -d
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Kafka started successfully" -ForegroundColor Green
        Write-Host "Kafka UI available at: http://localhost:9090" -ForegroundColor Cyan
        Write-Host "Bootstrap Servers: localhost:9092" -ForegroundColor Cyan

        # Wait for Kafka to be ready
        Write-Host "Waiting for Kafka to be ready..." -ForegroundColor Yellow
        Start-Sleep -Seconds 10
        
        # Automatically create topics
        CreateTopics
    } else {
        Write-Host "Failed to start Kafka" -ForegroundColor Red
    }
}

function StopKafka {
    Write-Host "Stopping Kafka and Zookeeper..." -ForegroundColor Yellow
    docker-compose -f $ComposeFile down
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Kafka stopped successfully" -ForegroundColor Green
    } else {
        Write-Host "Failed to stop Kafka" -ForegroundColor Red
    }
}

function RestartKafka {
    Write-Host "Restarting Kafka..." -ForegroundColor Yellow
    StopKafka
    Start-Sleep -Seconds 5
    StartKafka
}

function ShowStatus {
    Write-Host "Kafka container status:" -ForegroundColor Green
    docker-compose -f $ComposeFile ps
    Write-Host ""
    Write-Host "Checking connectivity..." -ForegroundColor Yellow
    docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>$null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Kafka is running properly" -ForegroundColor Green
    } else {
        Write-Host "Kafka is not responding" -ForegroundColor Red
    }
}

function ShowLogs {
    Write-Host "Showing Kafka logs (Ctrl+C to exit):" -ForegroundColor Green
    docker-compose -f $ComposeFile logs -f kafka
}

function ListTopics {
    Write-Host "Existing topics in Kafka:" -ForegroundColor Green
    docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --list
}

function CreateTopics {
    Write-Host "Creating required topics for the Vending Machine microservices..." -ForegroundColor Green
    
    $topics = @(
        "transaction-events",
        "payment-events", 
        "inventory-events",
        "dispensing-events",
        "notification-events"
    )
    
    foreach ($topic in $topics) {
        Write-Host "   Creating topic: $topic" -ForegroundColor Cyan
        docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic $topic --partitions 1 --replication-factor 1 --if-not-exists
    }

    Write-Host "Topics created successfully" -ForegroundColor Green
    Write-Host ""
    ListTopics
}

# Check if Docker is running
try {
    docker version | Out-Null
} catch {
    Write-Host "Docker is not running or not installed" -ForegroundColor Red
    exit 1
}

# Check if docker-compose file exists
if (!(Test-Path $ComposeFile)) {
    Write-Host "File $ComposeFile not found" -ForegroundColor Red
    Write-Host "   Make sure to run this script from the kafka-infrastructure directory" -ForegroundColor Yellow
    exit 1
}

# Execute action
switch ($Action) {
    "start" { StartKafka }
    "stop" { StopKafka }
    "restart" { RestartKafka }
    "status" { ShowStatus }
    "logs" { ShowLogs }
    "topics" { ListTopics }
    "create-topics" { CreateTopics }
    "help" { ShowHelp }
    default { ShowHelp }
}