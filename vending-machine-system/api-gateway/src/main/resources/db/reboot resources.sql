-- Inventory Service Database
DROP DATABASE IF EXISTS vending_inventory;
CREATE DATABASE vending_inventory;
DROP USER IF EXISTS 'service_inventory'@'localhost';
CREATE USER 'service_inventory'@'localhost' IDENTIFIED BY 'securePassword123';
GRANT SELECT, INSERT, UPDATE, DELETE, ALTER, DROP, REFERENCES, CREATE ON vending_inventory.* TO 'service_inventory'@'localhost';

-- Payment Service Database
DROP DATABASE IF EXISTS vending_payment;
CREATE DATABASE vending_payment;
DROP USER IF EXISTS 'service_payment'@'localhost';
CREATE USER 'service_payment'@'localhost' IDENTIFIED BY 'securePassword123';
GRANT SELECT, INSERT, UPDATE, DELETE, ALTER, DROP, REFERENCES, CREATE ON vending_payment.* TO 'service_payment'@'localhost';

-- Transaction Service Database
DROP DATABASE IF EXISTS vending_transaction;
CREATE DATABASE vending_transaction;
DROP USER IF EXISTS 'service_transaction'@'localhost';
CREATE USER 'service_transaction'@'localhost' IDENTIFIED BY 'securePassword123';
GRANT SELECT, INSERT, UPDATE, DELETE, ALTER, DROP, REFERENCES, CREATE ON vending_transaction.* TO 'service_transaction'@'localhost';

-- Dispensing Service Database
DROP DATABASE IF EXISTS vending_dispensing;
CREATE DATABASE vending_dispensing;
DROP USER IF EXISTS 'service_dispensing'@'localhost';
CREATE USER 'service_dispensing'@'localhost' IDENTIFIED BY 'securePassword123';
GRANT SELECT, INSERT, UPDATE, DELETE, ALTER, DROP, REFERENCES, CREATE ON vending_dispensing.* TO 'service_dispensing'@'localhost';

-- Notification Service Database
DROP DATABASE IF EXISTS vending_notification;
CREATE DATABASE vending_notification;
DROP USER IF EXISTS 'service_notification'@'localhost';
CREATE USER 'service_notification'@'localhost' IDENTIFIED BY 'securePassword123';
GRANT SELECT, INSERT, UPDATE, DELETE, ALTER, DROP, REFERENCES, CREATE ON vending_notification.* TO 'service_notification'@'localhost';