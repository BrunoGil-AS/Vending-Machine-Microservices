# Inventory Service - Auto Data Initialization

## Overview

The inventory service now automatically loads initial product data when it starts up, **only if the database is empty**. This ensures a consistent starting state for development and testing without manual SQL script execution.

## How It Works

### DataInitializer Component

The `DataInitializer` class implements `CommandLineRunner`, which runs automatically after the Spring Boot application context is loaded.

**Location:** `src/main/java/com/vendingmachine/inventory/config/DataInitializer.java`

### Logic Flow

```
1. Service starts up
2. DataInitializer.run() executes
3. Check: Does product table have any records?
   ├─ YES → Skip initialization (log message and exit)
   └─ NO  → Load initial 10 products with stock
4. Continue normal service operations
```

### Initial Products Loaded

When the database is empty, the following products are automatically created:

| ID  | Product Name  | Price | Description                    | Initial Stock |
| --- | ------------- | ----- | ------------------------------ | ------------- |
| 1   | Coca Cola     | $1.50 | Refreshing cola beverage 330ml | 15            |
| 2   | Pepsi         | $1.50 | Classic cola drink 330ml       | 12            |
| 3   | Sprite        | $1.50 | Lemon-lime flavored soda 330ml | 18            |
| 4   | Water         | $1.00 | Pure mineral water 500ml       | 20            |
| 5   | Orange Juice  | $2.00 | Fresh orange juice 250ml       | 10            |
| 6   | Coffee        | $2.50 | Hot brewed coffee 200ml        | 8             |
| 7   | Green Tea     | $1.75 | Unsweetened green tea 330ml    | 14            |
| 8   | Chocolate Bar | $1.25 | Milk chocolate bar 50g         | 25            |
| 9   | Chips         | $1.50 | Crispy potato chips 100g       | 16            |
| 10  | Energy Drink  | $2.75 | Energy boost drink 250ml       | 11            |

## Behavior

### First Startup (Empty Database)

```
2025-10-17 19:00:00 [main] INFO  DataInitializer - Checking if initial data needs to be loaded...
2025-10-17 19:00:00 [main] INFO  DataInitializer - Database is empty. Loading initial product data...
2025-10-17 19:00:01 [main] INFO  DataInitializer - Successfully loaded 10 products with initial stock
```

### Subsequent Startups (Database Has Data)

```
2025-10-17 19:05:00 [main] INFO  DataInitializer - Checking if initial data needs to be loaded...
2025-10-17 19:05:00 [main] INFO  DataInitializer - Database already contains 10 products. Skipping initial data load.
```

## Database Reset

If you need to reload the initial data:

### Option 1: Clear Database (Recommended for Development)

```sql
USE vending_inventory;
DELETE FROM stock;
DELETE FROM product;
-- Restart inventory-service - data will auto-load
```

### Option 2: Drop and Recreate Database

```sql
DROP DATABASE vending_inventory;
CREATE DATABASE vending_inventory;
-- Restart inventory-service - Hibernate creates tables, then data auto-loads
```

### Option 3: Manual SQL Script (No Longer Needed)

The SQL script at `api-gateway/src/main/resources/db/initial stock.sql` is now **optional** and only needed for manual database setup outside the application.

## Configuration

### Disable Auto-Initialization (If Needed)

If you want to disable this feature, you can exclude the bean:

**Method 1: Exclude in application.properties**

```properties
spring.autoconfigure.exclude=com.vendingmachine.inventory.config.DataInitializer
```

**Method 2: Conditional Loading**
Add this to `application.properties`:

```properties
inventory.data.initialize=false
```

Then update `DataInitializer.java`:

```java
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "inventory.data.initialize", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {
    // ... existing code ...
}
```

## Benefits

1. **No Manual Steps**: Developers don't need to run SQL scripts manually
2. **Idempotent**: Safe to restart the service multiple times
3. **Consistent State**: All developers and environments start with the same data
4. **Fast Testing**: Quick reset by clearing the database and restarting
5. **Production Safe**: Won't overwrite existing production data

## Testing

### Verify Auto-Load

```bash
# 1. Clear database
mysql -u root -p -e "USE vending_inventory; DELETE FROM stock; DELETE FROM product;"

# 2. Restart inventory-service
# (or run from IDE)

# 3. Check logs for "Successfully loaded 10 products"

# 4. Verify in database
mysql -u root -p -e "USE vending_inventory; SELECT COUNT(*) FROM product;"
# Expected: 10
```

### Verify Idempotency

```bash
# 1. Restart inventory-service again (with data already loaded)

# 2. Check logs for "Database already contains X products. Skipping"

# 3. Verify count hasn't changed
mysql -u root -p -e "USE vending_inventory; SELECT COUNT(*) FROM product;"
# Expected: Still 10
```

## Implementation Details

- **Transaction**: Wrapped in `@Transactional` to ensure atomicity
- **Error Handling**: Throws `RuntimeException` if initialization fails
- **Logging**: INFO level for high-level status, DEBUG for individual products
- **Performance**: Uses batch operations, completes in < 1 second

## Related Files

- **DataInitializer**: `inventory-service/src/main/java/com/vendingmachine/inventory/config/DataInitializer.java`
- **Legacy SQL**: `api-gateway/src/main/resources/db/initial stock.sql` (optional backup)

---

**Last Updated:** October 17, 2025
