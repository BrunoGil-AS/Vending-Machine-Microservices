#!/bin/bash

# Vending Machine System Build Script

echo "=========================================="
echo "Building Vending Machine Control System"
echo "=========================================="

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Clean previous builds
print_info "Cleaning previous builds..."
mvn clean
if [ $? -eq 0 ]; then
    print_success "Clean completed"
else
    print_error "Clean failed"
    exit 1
fi

# Install common library first
print_info "Installing common library..."
cd common-library
mvn clean install
if [ $? -eq 0 ]; then
    print_success "Common library installed"
    cd ..
else
    print_error "Common library installation failed"
    exit 1
fi

# Build all modules
print_info "Building all modules..."
mvn clean package -DskipTests
mvn clean install -DskipTests
if [ $? -eq 0 ]; then
    print_success "Build completed successfully"
else
    print_error "Build failed"
    exit 1
fi

# Optional: Run tests
if [ "$1" == "--with-tests" ]; then
    print_info "Running tests..."
    mvn test
    if [ $? -eq 0 ]; then
        print_success "All tests passed"
    else
        print_error "Some tests failed"
        exit 1
    fi
fi

echo ""
print_success "=========================================="
print_success "Build completed successfully!"
print_success "=========================================="
echo ""
print_info "JAR files location:"
echo "  - Config Server: config-server/target/config-server-1.0.0-SNAPSHOT.jar"
echo "  - Eureka Server: eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar"
echo "  - API Gateway: api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar"
echo "  - Inventory Service: inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar"
echo "  - Payment Service: payment-service/target/payment-service-1.0.0-SNAPSHOT.jar"
echo "  - Transaction Service: transaction-service/target/transaction-service-1.0.0-SNAPSHOT.jar"
echo "  - Dispensing Service: dispensing-service/target/dispensing-service-1.0.0-SNAPSHOT.jar"
echo "  - Notification Service: notification-service/target/notification-service-1.0.0-SNAPSHOT.jar"
echo ""
print_info "To start services, run: ./start-services.sh"