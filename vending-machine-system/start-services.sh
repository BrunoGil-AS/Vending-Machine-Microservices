#!/bin/bash

# Vending Machine System Startup Script (No DB / No Kafka Checks)

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

print_header() {
    echo -e "${BLUE}=========================================="
    echo -e "$1"
    echo -e "==========================================${NC}"
}

# Function to check if a service is running
check_service() {
    local port=$1
    local service_name=$2
    
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null ; then
        print_error "$service_name is already running on port $port"
        return 1
    fi
    return 0
}

# Function to wait for service to be ready
wait_for_service() {
    local port=$1
    local service_name=$2
    local max_wait=$3
    local waited=0
    
    print_info "Waiting for $service_name to start..."
    
    while [ $waited -lt $max_wait ]; do
        if curl -s http://localhost:$port/actuator/health > /dev/null 2>&1; then
            print_success "$service_name is ready!"
            return 0
        fi
        sleep 5
        waited=$((waited + 5))
        echo -n "."
    done
    
    echo ""
    print_error "$service_name failed to start within $max_wait seconds"
    return 1
}

# Function to start a service
start_service() {
    local jar_path=$1
    local service_name=$2
    local port=$3
    local wait_time=$4
    
    print_info "Starting $service_name on port $port..."
    
    nohup java -jar $jar_path > logs/${service_name}.log 2>&1 &
    local pid=$!
    echo $pid > logs/${service_name}.pid
    
    if wait_for_service $port "$service_name" $wait_time; then
        print_success "$service_name started successfully (PID: $pid)"
        return 0
    else
        print_error "$service_name failed to start"
        kill $pid 2>/dev/null
        return 1
    fi
}

print_header "Vending Machine System - Startup Script"

# Create logs directory
mkdir -p logs

# Check if JARs exist
print_info "Checking if JAR files exist..."
missing_jars=false

jars=(
    "config-server/target/config-server-1.0.0-SNAPSHOT.jar:Config Server"
    "eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar:Eureka Server"
    "api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar:API Gateway"
    "inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar:Inventory Service"
    "payment-service/target/payment-service-1.0.0-SNAPSHOT.jar:Payment Service"
    "transaction-service/target/transaction-service-1.0.0-SNAPSHOT.jar:Transaction Service"
    "dispensing-service/target/dispensing-service-1.0.0-SNAPSHOT.jar:Dispensing Service"
    "notification-service/target/notification-service-1.0.0-SNAPSHOT.jar:Notification Service"
)

for jar_info in "${jars[@]}"; do
    IFS=':' read -r jar_path service_name <<< "$jar_info"
    if [ ! -f "$jar_path" ]; then
        print_error "Missing: $service_name ($jar_path)"
        missing_jars=true
    fi
done

if [ "$missing_jars" = true ]; then
    print_error "Some JAR files are missing. Please run './build.sh' first."
    exit 1
fi

print_success "All JAR files found"

# Check if services are already running
print_info "Checking if services are already running..."
check_service 8888 "Config Server" || exit 1
check_service 8761 "Eureka Server" || exit 1
check_service 8080 "API Gateway" || exit 1
check_service 8081 "Inventory Service" || exit 1
check_service 8082 "Payment Service" || exit 1
check_service 8083 "Transaction Service" || exit 1
check_service 8084 "Dispensing Service" || exit 1
check_service 8085 "Notification Service" || exit 1

print_success "All ports are available"

# Start services in order
print_header "Starting Services"

start_service "config-server/target/config-server-1.0.0-SNAPSHOT.jar" "config-server" 8888 60 || exit 1
sleep 5

start_service "eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar" "eureka-server" 8761 60 || exit 1
sleep 10

start_service "api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar" "api-gateway" 8080 60 || exit 1
sleep 5

print_info "Starting business services..."

start_service "inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar" "inventory-service" 8081 60 &
start_service "payment-service/target/payment-service-1.0.0-SNAPSHOT.jar" "payment-service" 8082 60 &
start_service "transaction-service/target/transaction-service-1.0.0-SNAPSHOT.jar" "transaction-service" 8083 60 &
start_service "dispensing-service/target/dispensing-service-1.0.0-SNAPSHOT.jar" "dispensing-service" 8084 60 &
start_service "notification-service/target/notification-service-1.0.0-SNAPSHOT.jar" "notification-service" 8085 60 &

wait

print_header "All Services Started Successfully!"

echo ""
print_info "Service URLs:"
echo "  - Config Server:       http://localhost:8888"
echo "  - Eureka Dashboard:    http://localhost:8761"
echo "  - API Gateway:         http://localhost:8080"
echo "  - Inventory Service:   http://localhost:8081"
echo "  - Payment Service:     http://localhost:8082"
echo "  - Transaction Service: http://localhost:8083"
echo "  - Dispensing Service:  http://localhost:8084"
echo "  - Notification Service: http://localhost:8085"
echo ""
print_info "Logs are available in the 'logs/' directory"
print_info "To stop all services, run: ./stop-services.sh"
echo ""
