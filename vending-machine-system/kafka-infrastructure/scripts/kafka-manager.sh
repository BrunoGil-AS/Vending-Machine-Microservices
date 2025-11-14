#!/bin/bash

# Script to manage Kafka with Docker for the Vending Machine project
# Usage: ./kafka-manager.sh [start|stop|restart|status|logs|topics]

COMPOSE_FILE="docker-compose.yml"

show_help() {
    echo "=== Kafka Manager for Vending Machine Project ==="
    echo ""
    echo "Available commands:"
    echo "  start         - Starts Kafka and Zookeeper"
    echo "  stop          - Stops Kafka and Zookeeper"
    echo "  restart       - Restarts the services"
    echo "  status        - Shows the status of the containers"
    echo "  logs          - Displays Kafka logs"
    echo "  topics        - Lists existing topics"
    echo "  create-topics - Creates the necessary topics for the microservices"
    echo "  help          - Shows this help message"
    echo ""
    echo "Example: ./kafka-manager.sh start"
    echo ""
    echo "Useful URLs:"
    echo "  Kafka UI: http://localhost:9090"
    echo "  Kafka Bootstrap Servers: localhost:9092"
}

start_kafka() {
    echo "Starting Kafka and Zookeeper for Vending Machine..."
    docker-compose -f $COMPOSE_FILE up -d
    
    if [ $? -eq 0 ]; then
        echo "✅ Kafka started successfully"
        echo "🌐 Kafka UI available at: http://localhost:9090"
        echo "🔗 Bootstrap Servers: localhost:9092"

        # Wait for Kafka to be ready
        echo "⏳ Waiting for Kafka to be ready..."
        sleep 10
        
        # Automatically create topics
        create_topics
    else
        echo "❌ Failed to start Kafka"
    fi
}

stop_kafka() {
    echo "Stopping Kafka and Zookeeper..."
    docker-compose -f $COMPOSE_FILE down
    
    if [ $? -eq 0 ]; then
        echo "✅ Kafka stopped successfully"
    else
        echo "❌ Failed to stop Kafka"
    fi
}

restart_kafka() {
    echo "Restarting Kafka..."
    stop_kafka
    sleep 5
    start_kafka
}

show_status() {
    echo "📊 Kafka container status:"
    docker-compose -f $COMPOSE_FILE ps
    echo ""
    echo "🔍 Checking connectivity..."
    docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        echo "✅ Kafka is running properly"
    else
        echo "❌ Kafka is not responding"
    fi
}

show_logs() {
    echo "📋 Showing Kafka logs (Ctrl+C to exit):"
    docker-compose -f $COMPOSE_FILE logs -f kafka
}

list_topics() {
    echo "📝 Existing topics in Kafka:"
    docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --list
}

create_topics() {
    echo "🚀 Creating required topics for the Vending Machine microservices..."
    
    topics=(
        "transaction-events"
        "payment-events"
        "inventory-events"
        "dispensing-events"
        "notification-events"
    )
    
    for topic in "${topics[@]}"; do
        echo "   📌 Creating topic: $topic"
        docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic "$topic" --partitions 1 --replication-factor 1 --if-not-exists
    done

    echo "✅ Topics created successfully"
    echo ""
    list_topics
}

# Check if Docker is running
if ! docker version >/dev/null 2>&1; then
    echo "❌ Docker is not running or not installed"
    exit 1
fi

# Check if docker-compose file exists
if [ ! -f "$COMPOSE_FILE" ]; then
    echo "❌ File $COMPOSE_FILE not found"
    echo "   Make sure to run this script from the kafka-infrastructure directory"
    exit 1
fi

# Execute action based on parameter
case "${1:-help}" in
    start)
        start_kafka
        ;;
    stop)
        stop_kafka
        ;;
    restart)
        restart_kafka
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    topics)
        list_topics
        ;;
    create-topics)
        create_topics
        ;;
    help|*)
        show_help
        ;;
esac