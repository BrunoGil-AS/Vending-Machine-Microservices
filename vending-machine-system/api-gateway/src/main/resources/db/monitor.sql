use  vending_inventory;
-- show tables;
-- select * from  product;
-- select * from  stock;
SELECT 
    p.id AS product_id,
    p.name,
    p.price,
    p.category,
    p.description,
    s.quantity,
    s.min_threshold
FROM 
    product p
INNER JOIN 
    stock s ON p.id = s.product_id;

-- select * from  ;
-- select * from  ;

use  vending_transaction;
-- show tables;
select * from  transactions;

use  vending_payment;
-- show tables;
select * from  payment_transactions;

use  vending_dispensing;
-- show tables;
select * from  dispensing_operations;