CREATE TABLE users (
    user_id INT PRIMARY KEY CHECK (user_id BETWEEN 100000000 AND 999999999),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL
);

CREATE TABLE account (
    account_id INT PRIMARY KEY,
    account_type VARCHAR(50) NOT NULL
);

CREATE TABLE user_account (
    user_id INT NOT NULL,
    account_id INT NOT NULL,
    PRIMARY KEY (user_id, account_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
);

CREATE TABLE assets (
    asset_id VARCHAR(100) PRIMARY KEY,
    asset_name VARCHAR(100) NOT NULL,
    asset_type VARCHAR(50) NOT NULL
);

CREATE TABLE holdings (
    holding_id INT PRIMARY KEY,
    asset_id VARCHAR(100) NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL,
    execution_price DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (asset_id) REFERENCES assets(asset_id)
);

CREATE TABLE transactions (
    transaction_id INT PRIMARY KEY,
    asset_id VARCHAR(100) NOT NULL,
    transaction_quantity DECIMAL(10, 2) NOT NULL,
    transaction_price DECIMAL(10, 2) NOT NULL,
    transaction_status VARCHAR(50) NOT NULL,
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (asset_id) REFERENCES assets(asset_id)
);

CREATE TABLE transaction_participants (
    user_id INT NOT NULL,
    transaction_id INT NOT NULL,
    transaction_side VARCHAR(50) NOT NULL CHECK (transaction_side IN ('BUY', 'SELL')),
    PRIMARY KEY (user_id, transaction_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id)
);



INSERT INTO users (user_id, first_name, last_name, email) VALUES
(100000001, 'Alice', 'Johnson', 'alice.johnson@gmail.com'),
(100000002, 'Bob', 'Smith', 'bob.smith@gmail.com'),
(100000003, 'Charlie', 'Brown', 'charlie.brown@gmail.com'),
(100000004, 'Diana', 'Williams', 'diana.williams@gmail.com'),
(100000005, 'Ethan', 'Davis', 'ethan.davis@gmail.com'),
(100000006, 'Fiona', 'Miller', 'fiona.miller@gmail.com');

INSERT INTO account (account_id, account_type) VALUES
(101, 'Brokerage'),
(102, 'Retirement'),
(103, 'Savings'),
(104, 'Investment'),
(105, 'Trading');


INSERT INTO user_account (user_id, account_id) VALUES
(100000001, 101),
(100000001, 102),
(100000002, 103),
(100000002, 104),
(100000003, 101),
(100000004, 105),
(100000005, 104),
(100000006, 102);

INSERT INTO assets (asset_id, asset_name, asset_type) VALUES
('AAPL', 'Apple Inc.', 'STOCK'),
('MSFT', 'Microsoft Corporation', 'STOCK'),
('GOOGL', 'Alphabet Inc.', 'STOCK'),
('AMZN', 'Amazon.com Inc.', 'STOCK'),
('TSLA', 'Tesla Inc.', 'STOCK'),
('BTC', 'Bitcoin', 'CRYPTO'),
('ETH', 'Ethereum', 'CRYPTO'),
('SPY', 'SPDR S&P 500 ETF', 'ETF'),
('QQQ', 'Invesco QQQ ETF', 'ETF'),
('NVDA', 'NVIDIA Corporation', 'STOCK');

INSERT INTO holdings
(holding_id, asset_id, quantity, execution_price) VALUES
(1, 'AAPL', 50.00, 175.25),
(2, 'MSFT', 30.00, 410.50),
(3, 'GOOGL', 20.00, 165.75),
(4, 'AMZN', 40.00, 182.30),
(5, 'TSLA', 15.00, 245.60),
(6, 'BTC', 0.50, 62500.00),
(7, 'ETH', 5.00, 3200.00),
(8, 'SPY', 25.00, 540.25),
(9, 'QQQ', 18.00, 465.80),
(10, 'NVDA', 35.00, 125.40);

INSERT INTO transactions
(transaction_id, asset_id, transaction_quantity, transaction_price, transaction_status, transaction_date)
VALUES
(1001, 'AAPL', 10.00, 178.50, 'COMPLETED', '2026-09-01 09:30:00'),
(1002, 'MSFT', 5.00, 415.20, 'COMPLETED', '2026-09-02 10:15:00'),
(1003, 'GOOGL', 8.00, 168.75, 'COMPLETED', '2026-09-03 11:20:00'),
(1004, 'AMZN', 12.00, 185.40, 'COMPLETED', '2026-09-04 14:10:00'),
(1005, 'TSLA', 5.00, 248.90, 'PENDING', '2026-09-05 15:45:00'),
(1006, 'BTC', 0.10, 63500.00, 'COMPLETED', '2026-09-06 16:30:00'),
(1007, 'ETH', 2.00, 3250.00, 'COMPLETED', '2026-09-07 09:45:00'),
(1008, 'SPY', 10.00, 545.30, 'COMPLETED', '2026-09-08 10:30:00'),
(1009, 'QQQ', 5.00, 470.15, 'CANCELLED', '2026-09-09 12:00:00'),
(1010, 'NVDA', 15.00, 128.75, 'COMPLETED', '2026-09-10 13:25:00'),
(1011, 'AAPL', 20.00, 180.10, 'COMPLETED', '2026-09-11 14:40:00'),
(1012, 'MSFT', 10.00, 418.60, 'PENDING', '2026-09-12 15:10:00'),
(1013, 'BTC', 0.05, 64200.00, 'COMPLETED', '2026-09-13 16:00:00'),
(1014, 'TSLA', 3.00, 251.25, 'COMPLETED', '2026-09-14 09:20:00'),
(1015, 'NVDA', 10.00, 130.50, 'COMPLETED', '2026-09-15 11:35:00');


INSERT INTO transaction_participants
(user_id, transaction_id, transaction_side) VALUES
(100000001, 1001, 'BUY'),
(100000002, 1002, 'BUY'),
(100000003, 1003, 'BUY'),
(100000004, 1004, 'BUY'),
(100000005, 1005, 'BUY'),
(100000006, 1006, 'BUY'),
(100000001, 1007, 'BUY'),
(100000002, 1008, 'BUY'),
(100000003, 1009, 'SELL'),
(100000004, 1010, 'BUY'),
(100000005, 1011, 'SELL'),
(100000006, 1012, 'BUY'),
(100000001, 1013, 'BUY'),
(100000002, 1014, 'SELL'),
(100000003, 1015, 'BUY');