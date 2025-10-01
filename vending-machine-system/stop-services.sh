#!/bin/bash

# Vending Machine System Shutdown Script

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

# Function to stop a service
stop_service() {
    local service_name=$1
    local pid_file="logs/${service_name}.pid"
    
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p $pid > /dev/null 2>&1; then
            print_info "Stopping $service_name (PID: $pid)..."
            kill $pid
            
            # Wait for process to stop
            local waited=0
            while ps -p $pid > /dev/null 2>&1 && [ $waited -lt 30 ]; do
                sleep 1
                waited=$((waited + 1))
            done
            
            if ps -p $pid > /dev/null 2>&1; then
                print_info "Force killing $service_name..."
                kill -9 $pid
            fi
            
            rm "$pid_file"
            print_success "$service_name stopped"
        else
            print_info "$service_name is not running (stale PID file)"
            rm "$pid_file"
        fi
    else
        print_info "No PID file found for $service_name"
    fi
}

# Function to kill process by port
kill_by_port() {
    local port=$1
    local service_name=$2
    
    local pid=$(lsof -ti:$port)
    if [ ! -z "$pid" ]; then
        print_info "Found process on port $port (PID: $pid) - $service_name"
        kill $pid 2>/dev/null
        sleep 2
        
        if lsof -ti:$port > /dev/null 2>&1; then
            print_info "Force killing process on port $port..."
            kill -9 $pid 2>/dev/null
        fi
        
        print_success "Stopped process on port $port"
    fi
}

print_header "Vending Machine System - Shutdown Script"

# Stop services in reverse order
services=(
    "notification-service"
    "dispensing-service"
    "transaction-service"
    "payment-service"
    "inventory-service"
    "api-gateway"
    "eureka-server"
    "config-server"
)

print_info "Stopping services..."

for service in "${services[@]}"; do
    stop_service "$service"
done

# Additional cleanup by port (in case PID files are missing)
print_info "Cleaning up any remaining processes by port..."

ports=(
    "8085:Notification Service"
    "8084:Dispensing Service"
    "8083:Transaction Service"
    "8082:Payment Service"
    "8081:Inventory Service"
    "8080:API Gateway"
    "8761:Eureka Server"
    "8888:Config Server"
)

for port_info in "${ports[@]}"; do
    IFS=':' read -r port service_name <<< "$port_info"
    kill_by_port $port "$service_name"
done

print_header "All Services Stopped"

echo ""
print_info "Logs are still available in the 'logs/' directory"
print_info "To start services again, run: ./start-services.sh"
echo ""