# Vending Machine Test Scripts

This directory contains Python test scripts for testing the Vending Machine Microservices system.

## Scripts Overview

### 1. `admin_test.py` - Administrator Testing Script

This script simulates an administrator testing all administrative endpoints of the vending machine system.

**Features:**
- User authentication (admin login)
- User management (create, read, update, delete users)
- Product management (create, update, delete products)
- Stock management (update stock levels, set thresholds)
- View payment transaction history
- Comprehensive error handling and formatted output

**Endpoints Tested:**
- `POST /api/auth/login` - Admin authentication
- `GET /api/auth/users` - List all users
- `POST /api/auth/users` - Create new user
- `GET /api/auth/users/{id}` - Get user by ID
- `PUT /api/auth/users/{id}` - Update user
- `DELETE /api/auth/users/{id}` - Delete user
- `GET /api/inventory/products` - List all products
- `POST /api/admin/inventory/products` - Create product
- `PUT /api/admin/inventory/stock/{id}` - Update stock
- `GET /api/inventory/availability/{id}` - Check product availability
- `DELETE /api/admin/inventory/products/{id}` - Delete product
- `GET /api/admin/payment/transactions` - View payment transactions

### 2. `customer_flow_test.py` - Customer Flow Testing Script

This script simulates normal customer usage of the vending machine, including browsing products and making purchases.

**Features:**
- Browse available products
- Check product availability
- Single product purchases with different payment methods
- Multiple product purchases with different payment methods
- Supports CASH, CREDIT_CARD, and DEBIT_CARD payment methods

**Scenarios Tested:**
1. Product browsing and listing
2. Single product purchase with CASH
3. Single product purchase with CREDIT_CARD
4. Single product purchase with DEBIT_CARD
5. Multiple products purchase with CASH
6. Multiple products purchase with CREDIT_CARD
7. Multiple products purchase with DEBIT_CARD

**Endpoints Tested:**
- `GET /api/inventory/products` - Browse products
- `GET /api/inventory/availability/{id}` - Check availability
- `POST /api/transaction/purchase` - Make purchase

## Prerequisites

### System Requirements
- Python 3.7 or higher
- All microservices running (see main README.md)
- API Gateway accessible at `http://localhost:8080`

### Python Dependencies
Install required Python packages:

```bash
pip install -r requirements.txt
```

Or install manually:

```bash
pip install requests
```

### System Setup

Before running the tests, ensure:

1. All microservices are running:
   - Config Server (port 8888)
   - Eureka Server (port 8761)
   - API Gateway (port 8080)
   - Inventory Service (port 8081)
   - Payment Service (port 8082)
   - Transaction Service (port 8083)
   - Dispensing Service (port 8084)
   - Notification Service (port 8085)

2. MySQL databases are created and accessible

3. Kafka and Zookeeper are running

4. An admin user exists in the system (for `admin_test.py`)

## Usage

### Running the Admin Test Script

```bash
python3 admin_test.py
```

or make it executable:

```bash
chmod +x admin_test.py
./admin_test.py
```

**Note:** If the admin user doesn't exist, the script will provide instructions to create one.

### Running the Customer Flow Test Script

```bash
python3 customer_flow_test.py
```

or make it executable:

```bash
chmod +x customer_flow_test.py
./customer_flow_test.py
```

**Note:** Products must exist in the inventory. Run `admin_test.py` first to create test products if needed.

## Configuration

Both scripts use the following default configuration:

```python
BASE_URL = "http://localhost:8080"
ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin123"
```

To change the configuration, edit the values at the top of each script.

## Expected Output

### Admin Test Script Output

The script will display:
- Login status and JWT token acquisition
- User management operations (create, read, update, delete)
- Product management operations (create, update, delete)
- Stock updates and availability checks
- Payment transaction history
- Status codes and JSON responses for each operation
- Summary of test execution

### Customer Flow Test Script Output

The script will display:
- List of available products
- Availability checks for products
- Purchase attempts with different payment methods
- Transaction details (ID, amount, status)
- Status codes and JSON responses
- Summary of scenarios tested

## Troubleshooting

### Connection Error

If you see "Cannot connect to services":
1. Verify all microservices are running
2. Check API Gateway is accessible at http://localhost:8080
3. Ensure no firewall blocking localhost connections

### Login Failed (admin_test.py)

If admin login fails:
1. Create an admin user first using the command provided in the script output
2. Verify credentials match the configuration
3. Check API Gateway logs for authentication errors

### No Products Available (customer_flow_test.py)

If no products are found:
1. Run `admin_test.py` first to create test products
2. Verify Inventory Service is running and accessible
3. Check database contains product data

### Purchase Failed

If purchases fail:
1. Verify Transaction Service is running
2. Check Payment Service is accessible
3. Ensure products have sufficient stock
4. Review microservice logs for errors

## Test Results

Both scripts provide detailed output including:
- ✓ Successful operations
- ✗ Failed operations
- ⚠ Warnings and skipped operations
- HTTP status codes
- Full JSON responses
- Summary of all tests executed

## Integration with CI/CD

These scripts can be integrated into CI/CD pipelines:

```bash
# Run admin tests
python3 admin_test.py > admin_test_results.log 2>&1

# Run customer flow tests
python3 customer_flow_test.py > customer_flow_results.log 2>&1
```

## Contributing

When adding new tests:
1. Follow the existing code structure
2. Use descriptive method names
3. Add proper error handling
4. Include formatted output with status indicators
5. Update this README with new test descriptions

## License

Same as the main project.
