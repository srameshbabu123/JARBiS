-- Development-only OLTP seed aligned with the OLAP sample IDs.
-- This is the source data that a Debezium connector should capture.
-- It is idempotent and does not delete existing application data.

INSERT INTO users (id, full_name, email, password_hash)
VALUES
(100000001, 'Alice Johnson', 'alice.johnson@example.com', 'dev-seed-password'),
(100000002, 'Bob Smith', 'bob.smith@example.com', 'dev-seed-password'),
(100000003, 'Charlie Brown', 'charlie.brown@example.com', 'dev-seed-password'),
(100000004, 'Diana Williams', 'diana.williams@example.com', 'dev-seed-password'),
(100000005, 'Ethan Davis', 'ethan.davis@example.com', 'dev-seed-password'),
(100000006, 'Fiona Miller', 'fiona.miller@example.com', 'dev-seed-password')
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    email = EXCLUDED.email;

INSERT INTO accounts (id, account_type, currency, balance, owner_id)
VALUES
(101, 'INVESTMENT', 'USD', 100000.00, 100000001),
(102, 'SAVINGS', 'USD', 100000.00, 100000002),
(103, 'CHECKING', 'EUR', 100000.00, 100000003),
(104, 'CRYPTO', 'USD', 100000.00, 100000004),
(105, 'INVESTMENT', 'INR', 100000.00, 100000005)
ON CONFLICT (id) DO UPDATE SET
    account_type = EXCLUDED.account_type,
    currency = EXCLUDED.currency,
    balance = EXCLUDED.balance,
    owner_id = EXCLUDED.owner_id;

INSERT INTO assets (id, name, price, asset_type)
VALUES
(1, 'Apple Inc.', 233.45, 'STOCK'),
(2, 'Microsoft Corporation', 475.50, 'STOCK'),
(3, 'NVIDIA Corporation', 179.95, 'STOCK'),
(4, 'Tesla Inc.', 302.70, 'STOCK'),
(5, 'Amazon.com Inc.', 218.60, 'STOCK'),
(6, 'Bitcoin', 70125.00, 'CRYPTOCURRENCY'),
(7, 'Ethereum', 4100.00, 'CRYPTOCURRENCY')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    price = EXCLUDED.price,
    asset_type = EXCLUDED.asset_type;

INSERT INTO orders
(id, asset_id, account_id, status, side, quantity, remaining_quantity, price, submitted_at)
VALUES
(900001, 1, 101, 'COMPLETED', 'BUY', 25.00, 0.00, 178.25, '2024-01-15 09:30:00'),
(900002, 1, 102, 'COMPLETED', 'SELL', 10.00, 0.00, 182.60, '2024-03-22 14:15:00'),
(900003, 2, 103, 'COMPLETED', 'BUY', 40.00, 0.00, 405.75, '2024-06-10 10:05:00'),
(900004, 3, 104, 'COMPLETED', 'BUY', 12.00, 0.00, 121.40, '2024-09-18 15:45:00'),
(900018, 3, 103, 'COMPLETED', 'SELL', 20.00, 0.00, 168.25, '2026-04-28 13:20:00'),
(900009, 1, 104, 'PENDING', 'BUY', 60.00, 60.00, 191.75, '2025-05-21 13:40:00'),
(900010, 2, 105, 'COMPLETED', 'SELL', 15.00, 0.00, 452.10, '2025-07-11 09:15:00')
ON CONFLICT (id) DO UPDATE SET
    asset_id = EXCLUDED.asset_id,
    account_id = EXCLUDED.account_id,
    status = EXCLUDED.status,
    side = EXCLUDED.side,
    quantity = EXCLUDED.quantity,
    remaining_quantity = EXCLUDED.remaining_quantity,
    price = EXCLUDED.price,
    submitted_at = EXCLUDED.submitted_at;

INSERT INTO transactions
(id, asset_id, quantity, execution_price, buy_order_id, sell_order_id, status)
VALUES
(910001, 1, 10.00, 180.25, 900001, 900002, 'COMPLETED'),
(910002, 2, 15.00, 430.50, 900003, 900010, 'COMPLETED'),
(910003, 3, 12.00, 145.75, 900004, 900018, 'COMPLETED')
ON CONFLICT (id) DO UPDATE SET
    asset_id = EXCLUDED.asset_id,
    quantity = EXCLUDED.quantity,
    execution_price = EXCLUDED.execution_price,
    buy_order_id = EXCLUDED.buy_order_id,
    sell_order_id = EXCLUDED.sell_order_id,
    status = EXCLUDED.status;

SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST((SELECT MAX(id) FROM users), 1), true);
SELECT setval(pg_get_serial_sequence('accounts', 'id'), GREATEST((SELECT MAX(id) FROM accounts), 1), true);
SELECT setval(pg_get_serial_sequence('assets', 'id'), GREATEST((SELECT MAX(id) FROM assets), 1), true);
SELECT setval(pg_get_serial_sequence('orders', 'id'), GREATEST((SELECT MAX(id) FROM orders), 1), true);
SELECT setval(pg_get_serial_sequence('transactions', 'id'), GREATEST((SELECT MAX(id) FROM transactions), 1), true);
