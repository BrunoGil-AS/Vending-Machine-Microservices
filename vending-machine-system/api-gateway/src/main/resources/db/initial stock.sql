-- Insert sample products into inventory service database
USE vending_inventory;

-- Clear existing data (optional - comment out if you want to keep existing data)
-- DELETE FROM stock;
-- DELETE FROM product;
-- RESET auto-increment
-- ALTER TABLE product AUTO_INCREMENT = 1;

-- Insert 10 sample products with their stock
-- Product 1
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Coca Cola', 1.50, 'Refreshing cola beverage 330ml', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 15, NOW(), NOW());

-- Product 2
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Pepsi', 1.50, 'Classic cola drink 330ml', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 12, NOW(), NOW());

-- Product 3
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Sprite', 1.50, 'Lemon-lime flavored soda 330ml', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 18, NOW(), NOW());

-- Product 4
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Water', 1.00, 'Pure mineral water 500ml', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 20, NOW(), NOW());

-- Product 5
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Orange Juice', 2.00, 'Fresh orange juice 250ml', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 10, NOW(), NOW());

-- Product 6
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Coffee', 2.50, 'Hot brewed coffee 200ml', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 8, NOW(), NOW());

-- Product 7
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Green Tea', 1.75, 'Unsweetened green tea 330ml', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 14, NOW(), NOW());

-- Product 8
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Chocolate Bar', 1.25, 'Milk chocolate bar 50g', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 25, NOW(), NOW());

-- Product 9
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Chips', 1.50, 'Crispy potato chips 100g', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 16, NOW(), NOW());

-- Product 10
INSERT INTO product (name, price, description, created_at, updated_at) 
VALUES ('Energy Drink', 2.75, 'Energy boost drink 250ml', NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at) 
VALUES (LAST_INSERT_ID(), 11, NOW(), NOW());

-- Verify insertions
SELECT p.id, p.name, p.price, p.description, s.quantity 
FROM product p 
LEFT JOIN stock s ON p.id = s.product_id 
ORDER BY p.id;

-- Display summary
SELECT 
    'Products inserted:' AS summary,
    COUNT(*) AS count 
FROM product;

SELECT 
    'Stock records created:' AS summary,
    COUNT(*) AS count 
FROM stock;