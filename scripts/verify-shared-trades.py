"""Check the untouched shared scenario and its actual OLAP projection via Docker."""
import argparse
import json
import math
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--env-file', default='.env.analytics')
args = parser.parse_args()


def detect_compose():
    errors = []
    for command in (['docker', 'compose'], ['docker-compose']):
        try:
            result = subprocess.run(command + ['version'], text=True,
                                    capture_output=True, timeout=15)
        except (OSError, subprocess.TimeoutExpired) as error:
            errors.append(' '.join(command) + ': ' + str(error))
            continue
        if result.returncode == 0:
            return command
        errors.append(' '.join(command) + ': ' + (result.stderr or result.stdout).strip())
    raise SystemExit("No working Docker Compose command found:\n" + "\n".join(errors))


compose_command = detect_compose()


def query(service, sql):
    user, database = ('jarbis_user', 'jarbis_db') if service == 'postgres' else ('jarbis_olap_user', 'jarbis_olap')
    wrapped = "SELECT COALESCE(json_agg(row_to_json(r)), '[]'::json)::text FROM (" + sql + ") r"
    try:
        result = subprocess.run(
            compose_command + ['--env-file', args.env_file, 'exec', '-T', service,
             'psql', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database, '-Atc', wrapped],
            check=True, text=True, capture_output=True, timeout=30)
    except subprocess.CalledProcessError as error:
        detail = (error.stderr or error.stdout or str(error)).strip()
        raise SystemExit(f"{service} query failed (exit {error.returncode}):\n{detail}") from None
    except (OSError, subprocess.TimeoutExpired) as error:
        raise SystemExit(f"{service} query could not run: {error}") from None
    return json.loads(result.stdout)


source = query('postgres', """
SELECT t.id,t.asset_id,t.quantity,t.execution_price,t.buy_order_id,t.sell_order_id,t.status,
       extract(epoch FROM t.executed_at) AS executed_at,
       b.account_id AS buyer_account_id,s.account_id AS seller_account_id,
       ba.owner_id AS buyer_user_id,sa.owner_id AS seller_user_id,ba.currency
FROM transactions t JOIN orders b ON b.id=t.buy_order_id JOIN orders s ON s.id=t.sell_order_id
JOIN accounts ba ON ba.id=b.account_id JOIN accounts sa ON sa.id=s.account_id
WHERE (t.id BETWEEN 9007001 AND 9007006 OR t.id BETWEEN 9009001 AND 9009094) ORDER BY t.id
""")
assert len(source) == 100, 'Expected 100 source trades'
# Wait for the same asynchronous delivery path used for application-created trades.
deadline = time.monotonic() + 90
while True:
    pending = query('postgres', """
SELECT (SELECT count(*) FROM analytics_outbox WHERE (transaction_id BETWEEN 9007001 AND 9007006 OR transaction_id BETWEEN 9009001 AND 9009094) AND published_at IS NULL)
     + (SELECT count(*) FROM order_audit_outbox WHERE (order_id BETWEEN 9005001 AND 9005012 OR order_id BETWEEN 9005101 AND 9005103 OR order_id BETWEEN 9010001 AND 9010188) AND published_at IS NULL) AS n
""")[0]['n']
    if pending == 0:
        break
    assert time.monotonic() < deadline, 'Fixture events did not reach OLAP; check analytics enabled and relay logs'
    time.sleep(1)
projected = query('postgres-olap', """
SELECT transaction_id AS id,asset_id,quantity,execution_price,buy_order_id,sell_order_id,status,
       extract(epoch FROM transaction_timestamp) AS executed_at,
       buyer_account_id,seller_account_id,buyer_user_id,seller_user_id,currency
FROM analytics.transaction_facts WHERE (transaction_id BETWEEN 9007001 AND 9007006 OR transaction_id BETWEEN 9009001 AND 9009094) ORDER BY transaction_id
""")
assert source == projected, 'OLTP and OLAP trades differ'
orders = query('postgres', "SELECT * FROM orders WHERE id BETWEEN 9005001 AND 9005012 OR id BETWEEN 9005101 AND 9005103 OR id BETWEEN 9010001 AND 9010188")
assert len(orders) == 203
assert sorted(o['status'] for o in orders).count('COMPLETED') == 200
by_id = {o['id']: o for o in orders}
for trade in source:
    buy, sell = by_id[trade['buy_order_id']], by_id[trade['sell_order_id']]
    assert buy['side'] == 'BUY' and sell['side'] == 'SELL'
    assert buy['asset_id'] == sell['asset_id'] == trade['asset_id']
    assert buy['account_id'] != sell['account_id']
    assert buy['quantity'] == sell['quantity'] == trade['quantity']
    assert buy['remaining_quantity'] == sell['remaining_quantity'] == 0
    assert sell['submitted_at'] < buy['submitted_at']
    assert buy['price'] >= trade['execution_price'] == sell['price']
assert by_id[9005101]['status'] == by_id[9005102]['status'] == 'PENDING'
assert by_id[9005101]['price'] < by_id[9005102]['price']
assert by_id[9005103]['status'] == 'CANCELLED'
assert not query('postgres-olap', 'SELECT transaction_id FROM analytics.transaction_facts WHERE buy_order_id BETWEEN 9005101 AND 9005103 OR sell_order_id BETWEEN 9005101 AND 9005103')

# Recompute cash, shares, and weighted-average acquisition costs from the opening positions.
cash = {i: 25000.0 for i in [9003101, 9003102, 9003103, 9003104]}
cash.update({i: 100000.0 for i in range(9008101, 9008109)})
assets = query('postgres', "SELECT DISTINCT s.id,s.name FROM assets s JOIN orders o ON o.asset_id=s.id WHERE o.id BETWEEN 9005001 AND 9005012")
initial_cost = {'GOOGL': 170, 'AMZN': 180, 'SPY': 540}
positions = {(account, asset['id']): [20.0 if account < 9008100 else 200.0, float(initial_cost[asset['name']])] for account in cash for asset in assets}
for trade in sorted(source, key=lambda t: t['executed_at']):
    buyer, seller = trade['buyer_account_id'], trade['seller_account_id']
    quantity, price = trade['quantity'], trade['execution_price']
    assert cash[buyer] >= quantity * price
    cash[buyer] -= quantity * price
    cash[seller] += quantity * price
    buy = positions[buyer, trade['asset_id']]
    sell = positions[seller, trade['asset_id']]
    assert sell[0] >= quantity
    buy[1] = (buy[0] * buy[1] + quantity * price) / (buy[0] + quantity)
    buy[0] += quantity
    sell[0] -= quantity
for account in query('postgres', 'SELECT id,balance FROM accounts WHERE (id BETWEEN 9003101 AND 9003104 OR id BETWEEN 9008101 AND 9008108)'):
    assert math.isclose(account['balance'], cash[account['id']], abs_tol=1e-8)
for holding in query('postgres', 'SELECT account_id,asset_id,quantity,average_price FROM holdings WHERE (account_id BETWEEN 9003101 AND 9003104 OR account_id BETWEEN 9008101 AND 9008108)'):
    expected = positions[holding['account_id'], holding['asset_id']]
    assert math.isclose(holding['quantity'], expected[0], abs_tol=1e-8)
    assert math.isclose(holding['average_price'], expected[1], abs_tol=1e-8)
assert math.isclose(sum(cash.values()), 900000, abs_tol=1e-7)

# Every order-history event must arrive unaltered, including pending/cancelled orders.
events = query('postgres', "SELECT event_id,payload::jsonb AS payload FROM order_audit_outbox WHERE order_id BETWEEN 9005001 AND 9005012 OR order_id BETWEEN 9005101 AND 9005103 OR order_id BETWEEN 9010001 AND 9010188 ORDER BY event_id")
projected_events = query('postgres-olap', "SELECT event_id,payload-'auditSequence' AS payload FROM analytics.order_events WHERE order_id BETWEEN 9005001 AND 9005012 OR order_id BETWEEN 9005101 AND 9005103 OR order_id BETWEEN 9010001 AND 9010188 ORDER BY event_id")
assert len(events) == 404 and events == projected_events
names = query('postgres-olap', 'SELECT full_name,email FROM analytics.dim_users WHERE (user_id BETWEEN 9003001 AND 9003004 OR user_id BETWEEN 9008001 AND 9008008) ORDER BY user_id')
assert names == query('postgres', 'SELECT full_name,email FROM users WHERE (id BETWEEN 9003001 AND 9003004 OR id BETWEEN 9008001 AND 9008008) ORDER BY id')
assert query('postgres-olap', 'SELECT count(*) AS n FROM analytics.trade_participants WHERE (transaction_id BETWEEN 9007001 AND 9007006 OR transaction_id BETWEEN 9009001 AND 9009094)')[0]['n'] == 200
print('PASS: 100 identical completed trades, 203 coherent orders, 404 matching audit events, cash/share/cost-basis reconciliation, and 200 OLAP participants')
