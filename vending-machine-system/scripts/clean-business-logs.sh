#!/bin/bash

# Script to clean current logs from Vending Machine services
# Preserves logs from Gateway, Config Server, and Eureka Server
# Also preserves archived logs (packaged logs in dated directories)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base directory
BASE_DIR="$(dirname "$(realpath "$0")")/.."
LOG_BASE_DIR="$BASE_DIR"

echo -e "${BLUE}=== Vending Machine Log Cleaner ===${NC}"
echo -e "${BLUE}Cleaning logs from business services only${NC}"
echo -e "${YELLOW}Preserving: Gateway, Config Server, Eureka Server${NC}"
echo ""

# Function to safely remove log file
clean_log_file() {
    local log_file="$1"
    local service_name="$2"
    
    if [[ -f "$log_file" ]]; then
        local file_size=$(stat -c%s "$log_file" 2>/dev/null || stat -f%z "$log_file" 2>/dev/null || echo "0")
        
        if [[ $file_size -gt 0 ]]; then
            echo -e "${YELLOW}  Cleaning: $service_name ($file_size bytes)${NC}"
            > "$log_file"  # Truncate file instead of deleting (preserves file handles)
        else
            echo -e "  Skipping: $service_name (already empty)"
        fi
    else
        echo -e "  Not found: $service_name"
    fi
}

# Function to clean service logs
clean_service_logs() {
    local service_dir="$1"
    local service_name="$2"
    
    echo -e "${GREEN}🧹 Cleaning $service_name logs...${NC}"
    
    # Clean main service log with correct format: service.log (service_dir already includes -service)
    local log_file_name="${service_name}.log"
    clean_log_file "$service_dir/logs/$log_file_name" "$log_file_name"
    
    # Clean any additional log files in the service logs directory
    if [[ -d "$service_dir/logs" ]]; then
        find "$service_dir/logs" -name "*.log" -type f 2>/dev/null | while read -r log_file; do
            local filename=$(basename "$log_file")
            if [[ "$filename" != "$log_file_name" ]]; then
                local file_size=$(stat -c%s "$log_file" 2>/dev/null || stat -f%z "$log_file" 2>/dev/null || echo "0")
                if [[ $file_size -gt 0 ]]; then
                    echo -e "${YELLOW}  Cleaning additional: $filename ($file_size bytes)${NC}"
                    > "$log_file"
                fi
            fi
        done || true  # Don't exit on find errors
    fi
}

echo -e "${BLUE}Services to clean:${NC}"

# Services to clean (excluding Gateway, Config Server, Eureka Server)
SERVICES_TO_CLEAN=(
    "inventory-service:Inventory Service"
    "payment-service:Payment Service"
    "transaction-service:Transaction Service"
    "dispensing-service:Dispensing Service"
    "notification-service:Notification Service"
)

# Show what will be cleaned
for service_info in "${SERVICES_TO_CLEAN[@]}"; do
    IFS=':' read -r service_dir service_name <<< "$service_info"
    if [[ -d "$LOG_BASE_DIR/$service_dir" ]]; then
        echo -e "  ✓ $service_name"
    else
        echo -e "  ✗ $service_name (directory not found)"
    fi
done

echo ""

# Confirm action
read -p "Do you want to proceed with cleaning these logs? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Operation cancelled.${NC}"
    exit 0
fi

echo ""
echo -e "${GREEN}🚀 Starting log cleanup...${NC}"
echo ""

# Clean logs for each service
cleaned_count=0
for service_info in "${SERVICES_TO_CLEAN[@]}"; do
    IFS=':' read -r service_dir service_name <<< "$service_info"
    
    if [[ -d "$LOG_BASE_DIR/$service_dir" ]]; then
        # Pass the service directory name (without -service suffix) as the service name
        clean_service_logs "$LOG_BASE_DIR/$service_dir" "$service_dir"
        ((cleaned_count++))
    else
        echo -e "${RED}⚠️  Warning: $service_name directory not found${NC}"
    fi
    echo ""
done

# Clean central logs directory (only business services)
echo -e "${GREEN}🧹 Cleaning central logs directory...${NC}"
if [[ -d "$LOG_BASE_DIR/logs" ]]; then
    find "$LOG_BASE_DIR/logs" -name "*.log" -type f 2>/dev/null | while read -r log_file; do
        filename=$(basename "$log_file")
        
        # Skip infrastructure service logs
        if [[ "$filename" == "config-server.log" ]] || [[ "$filename" == "eureka-server.log" ]] || [[ "$filename" == "api-gateway.log" ]]; then
            echo -e "  Preserving: $filename (infrastructure service)"
        else
            file_size=$(stat -c%s "$log_file" 2>/dev/null || stat -f%z "$log_file" 2>/dev/null || echo "0")
            if [[ $file_size -gt 0 ]]; then
                echo -e "${YELLOW}  Cleaning: $filename ($file_size bytes)${NC}"
                > "$log_file"
            else
                echo -e "  Skipping: $filename (already empty)"
            fi
        fi
    done || true  # Don't exit on find errors
else
    echo -e "  Central logs directory not found"
fi

echo ""
echo -e "${GREEN}✅ Log cleanup completed!${NC}"
echo -e "${BLUE}Summary:${NC}"
echo -e "  🧹 Cleaned logs from $cleaned_count services"
echo -e "  🛡️  Preserved logs from: Gateway, Config Server, Eureka Server"
echo -e "  📦 Archived logs remain untouched"
echo ""
echo -e "${YELLOW}Note: Log files were truncated (not deleted) to preserve file handles${NC}"
echo -e "${BLUE}You can now start your services for clean log viewing${NC}"