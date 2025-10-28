# Admin Test Script - Usage Guide

## Overview

The `admin_test.py` script provides comprehensive testing for all admin endpoints in the Vending Machine microservices system. It now includes an **interactive menu** for selecting specific tests to run.

---

## Features

### Test Suites Available

1. **User Management Tests**

   - Create new admin user
   - Get all users
   - Update user information
   - Get user by ID
   - Delete user

2. **Product Management Tests**

   - Get all products (initial state)
   - Create 11 new products
   - Update product
   - Get product by ID
   - Delete product
   - Verify deletion
   - Optional cleanup of test data

3. **Payment Transaction Tests**
   - Get all payment transactions

---

## Usage Modes

### 🎯 Interactive Mode (NEW!)

Run the script without arguments to access the interactive menu:

```bash
python3 admin_test.py
```

**Menu Options:**

- `1` - Run User Management Tests only
- `2` - Run Product Management Tests only
- `3` - Run Payment Transaction Tests only
- `0` - Run ALL tests
- `q` - Quit without running tests

**Example Session:**

```
VENDING MACHINE ADMIN TEST MENU
================================================================

Available Test Suites:
  1. User Management Tests
  2. Product Management Tests
  3. Payment Transaction Tests
  0. Run ALL Tests
  q. Quit

Enter test numbers (comma-separated, e.g., 1,2) or 0 for all, q to quit: 1,3

Cleanup test products after execution? (y/n, default: y): n
```

### 🚀 Non-Interactive Mode

Run all tests automatically (legacy mode):

```bash
# Run all tests with cleanup
python3 admin_test.py --all

# Run all tests without cleanup
python3 admin_test.py --no-cleanup

# Run all tests (default behavior with any argument)
python3 admin_test.py --run
```

---

## Configuration

### Default Settings

Located at the top of `admin_test.py`:

```python
BASE_URL = "http://localhost:8080"
ADMIN_USERNAME = "hardcoded-admin"
ADMIN_PASSWORD = "password123"
```

### Modifying Configuration

1. **Change API Gateway URL:**

   ```python
   BASE_URL = "http://your-gateway:8080"
   ```

2. **Change Admin Credentials:**
   ```python
   ADMIN_USERNAME = "your-admin-username"
   ADMIN_PASSWORD = "your-admin-password"
   ```

---

## Prerequisites

### 1. System Running

Ensure all microservices are running:

```bash
cd vending-machine-system
./start-services.sh
```

### 2. Admin User Created

The admin user must exist before running tests. Create it using:

```bash
curl -X POST http://localhost:8080/api/auth/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "hardcoded-admin",
    "password": "password123",
    "role": "SUPER_ADMIN"
  }'
```

### 3. Python Dependencies

```bash
pip install requests
```

---

## Understanding Test Results

### Success Indicators

- ✅ `✓` - Successful operation
- 🟢 `Status Code: 200` or `201` - HTTP success
- 📊 Detailed JSON responses for verification

### Error Indicators

- ❌ `✗` - Failed operation
- 🔴 `Status Code: 400/500` - HTTP errors
- 📋 Error messages with stack traces

### Example Success Output

```
============================================================
  USER MANAGEMENT TESTS
============================================================

1. Creating new user...

Create User:
Status Code: 201
Response: {
  "data": {
    "id": 5,
    "username": "testuser_1761675147",
    "role": "ADMIN",
    "active": true
  },
  "success": true,
  "message": "User created successfully"
}

✓ User created with ID: 5
```

---

## Common Use Cases

### Test Only User Management

**Interactive:**

```bash
python3 admin_test.py
# Select: 1
```

**Use Case:** When working on authentication or user management features

---

### Test Product Management Without Cleanup

**Interactive:**

```bash
python3 admin_test.py
# Select: 2
# Cleanup: n
```

**Use Case:** When you want to inspect created products after the test

---

### Test Everything

**Interactive:**

```bash
python3 admin_test.py
# Select: 0
# Cleanup: y
```

**Non-Interactive:**

```bash
python3 admin_test.py --all
```

**Use Case:** Full system validation before deployment

---

### Test Specific Combinations

**Interactive:**

```bash
python3 admin_test.py
# Select: 1,3
```

**Use Case:** Testing user authentication and payment retrieval without touching inventory

---

## Troubleshooting

### Connection Refused

**Error:**

```
✗ Cannot connect to services: Connection refused
```

**Solution:**

1. Check if services are running: `./start-services.sh`
2. Verify API Gateway is accessible: `curl http://localhost:8080/actuator/health`

---

### Login Failed

**Error:**

```
✗ Login failed!
```

**Solution:**

1. Create admin user (see Prerequisites section)
2. Verify credentials match in script configuration
3. Check API Gateway logs: `tail -f api-gateway/logs/api-gateway.log`

---

### Product Creation Fails

**Error:**

```
Status Code: 500
Message: "Column 'min_threshold' cannot be null"
```

**Solution:**
This should be fixed in the latest version. If you still see this:

1. Ensure you're using the latest version of the script
2. Check that `minThreshold` field is included in product creation

---

### User Creation Fails

**Error:**

```
Failed to convert from type [io.asyncer.r2dbc.mysql.MySqlDataRow] to type [java.lang.Boolean]
```

**Solution:**

1. Check API Gateway logs with improved logging:
   ```bash
   tail -f vending-machine-system/api-gateway/logs/api-gateway.log | grep "\[CREATE USER\]"
   ```
2. Verify database schema is correct
3. Restart API Gateway service

---

## Advanced Features

### Custom Test Scenarios

You can modify the script to add custom test scenarios:

1. Add a new test method to the `VendingMachineAdminTester` class
2. Update the menu display in `show_menu()`
3. Add the test to `run_selected_tests()` method

### Automated CI/CD Integration

For automated testing in CI/CD pipelines:

```bash
# Run all tests non-interactively
python3 admin_test.py --all > test_results.log 2>&1

# Check exit code
if [ $? -eq 0 ]; then
  echo "Tests passed"
else
  echo "Tests failed"
  cat test_results.log
fi
```

---

## Logging and Debugging

### View Detailed Logs

**API Gateway (User Creation):**

```bash
tail -f vending-machine-system/api-gateway/logs/api-gateway.log | grep "\[CREATE USER\]"
```

**Inventory Service (Product Management):**

```bash
tail -f vending-machine-system/inventory-service/logs/inventory-service.log
```

**All Services:**

```bash
tail -f vending-machine-system/logs/*.log
```

### Enhanced Logging

The script now includes enhanced logging in the AuthService for user creation:

- `[CREATE USER]` prefix for all user creation log entries
- Detailed step-by-step logging
- Error stack traces for debugging
- Database operation logging

---

## Best Practices

1. **Always run cleanup** (default) in development to keep database clean
2. **Use interactive mode** for focused testing during development
3. **Use non-interactive mode** for automated testing and CI/CD
4. **Check logs** when tests fail to understand root causes
5. **Test incrementally** - run specific test suites when working on features

---

## Version History

### v2.0 (2025-10-28)

- ✅ Added interactive menu for test selection
- ✅ Added cleanup preference option
- ✅ Fixed user creation with ADMIN role
- ✅ Fixed user update with valid role
- ✅ Added minThreshold field to product creation
- ✅ Enhanced logging in AuthService

### v1.0 (Initial)

- Basic admin endpoint testing
- Non-interactive execution only

---

## Support

For issues or questions:

1. Check logs with `[CREATE USER]` filter for user management issues
2. Review `reports/admin_test_fixes.md` for known issues and solutions
3. Consult `Documentation/development_plan.md` for system architecture

---

**Happy Testing! 🚀**
