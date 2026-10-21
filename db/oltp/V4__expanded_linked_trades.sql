-- Add 94 coherent settlements to V3's six, for 100 shared completed trades.
-- Eight NEW clients start with USD 100,000 and 200 shares of GOOGL/AMZN/SPY.
-- Existing users, balances, holdings and immutable migration files are preserved.
-- The normal durable relays deliver these exact source events to OLAP.
CREATE TEMP TABLE fixture_clients(id BIGINT, account_id BIGINT, full_name TEXT, email TEXT) ON COMMIT DROP;
INSERT INTO fixture_clients VALUES
 (9008001,9008101,'Grace Local','grace@example.test'),
 (9008002,9008102,'Hari Local','hari@example.test'),
 (9008003,9008103,'Iris Local','iris@example.test'),
 (9008004,9008104,'Jon Local','jon@example.test'),
 (9008005,9008105,'Kai Local','kai@example.test'),
 (9008006,9008106,'Lena Local','lena@example.test'),
 (9008007,9008107,'Maya Local','maya@example.test'),
 (9008008,9008108,'Noah Local','noah@example.test');
INSERT INTO public.users(id,full_name,email,password_hash)
-- Reuse only the existing local fixture password hash; no credential is embedded here.
SELECT c.id,c.full_name,c.email,u.password_hash
FROM fixture_clients c CROSS JOIN public.users u WHERE u.id=9003001;
INSERT INTO public.accounts(id,account_type,currency,balance,owner_id)
SELECT account_id,'INVESTMENT','USD',100000,id FROM fixture_clients;
-- Use positive IDs so analytics filters and investigation routes accept the fixtures.
INSERT INTO public.assets(id,name,asset_type)
SELECT x.id,x.symbol,x.kind FROM (VALUES (9004001,'GOOGL','STOCK'),(9004002,'AMZN','STOCK'),(9004003,'SPY','ETF')) x(id,symbol,kind)
WHERE NOT EXISTS(SELECT 1 FROM public.assets a WHERE upper(a.name)=x.symbol AND a.id>0);
CREATE TEMP TABLE fixture_assets(symbol TEXT,asset_id BIGINT,initial_cost DOUBLE PRECISION) ON COMMIT DROP;
INSERT INTO fixture_assets
SELECT s.symbol,(SELECT id FROM public.assets WHERE upper(name)=s.symbol AND id>0 ORDER BY id LIMIT 1),s.cost
FROM (VALUES ('GOOGL',170.0),('AMZN',180.0),('SPY',540.0)) s(symbol,cost);
INSERT INTO public.holdings(id,account_id,asset_id,quantity,average_price)
SELECT 9008200 + row_number() OVER(ORDER BY c.id,a.symbol),c.account_id,a.asset_id,200,a.initial_cost
FROM fixture_clients c CROSS JOIN fixture_assets a;
CREATE TEMP TABLE fixture_trades(
 n INT,trade_id BIGINT,buyer_account BIGINT,seller_account BIGINT,symbol TEXT,
 quantity DOUBLE PRECISION,price DOUBLE PRECISION,executed_at TIMESTAMPTZ
) ON COMMIT DROP;
-- Repeatable round-robin counterparties, varying sizes, three instruments, and 19 dates.
-- Counterparties always differ. Each fill is checked for funds and inventory below.
INSERT INTO fixture_trades
SELECT n,9009000+n,
       9008101+(n-1)%8,
       9008101+((n-1)%8+1+((n-1)/8)%7)%8,
       (ARRAY['GOOGL','AMZN','SPY'])[1+(n-1)%3],
       1+(n-1)%10,
       (ARRAY[180.0,190.0,550.0])[1+(n-1)%3]+((n-1)/5)*0.5+((n-1)%5)*0.1,
       timestamptz '2026-10-01 14:00:00+00'+((n-1)/5)*interval '1 day'+((n-1)%5)*interval '30 minutes'
FROM generate_series(1,94) AS series(n);
CREATE TEMP SEQUENCE fixture_audit_sequence MINVALUE 90200001 MAXVALUE 90209999 START WITH 90200001;
CREATE FUNCTION pg_temp.fixture_order_event(order_id BIGINT,kind TEXT,at_time TIMESTAMPTZ,details JSONB)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE snapshot JSONB; event_id UUID; seq BIGINT;
BEGIN
 seq := nextval('pg_temp.fixture_audit_sequence');
 event_id := md5('shared-v4-audit-' || seq)::uuid;
 SELECT jsonb_build_object(
   'schemaVersion',1,'eventId',event_id,'type','OrderLifecycle','eventType',kind,
   'occurredAt',at_time,'actor','SYSTEM','orderId',o.id,'clientId',a.owner_id,
   'accountId',a.id,'assetId',o.asset_id,'instrument',s.name,'side',o.side,
   'status',o.status,'quantity',o.quantity,'remainingQuantity',o.remaining_quantity,
   'orderPrice',o.price,'cashBalance',a.balance,'currency',a.currency,
   'holdingQuantity',h.quantity,'details',details)
 INTO STRICT snapshot FROM public.orders o JOIN public.accounts a ON a.id=o.account_id
 JOIN public.assets s ON s.id=o.asset_id
 JOIN public.holdings h ON h.account_id=a.id AND h.asset_id=s.id WHERE o.id=order_id;
 INSERT INTO public.order_audit_outbox(sequence,event_id,order_id,payload,occurred_at)
 VALUES(seq,event_id,order_id,snapshot::text,at_time);
END $$;
DO $$
DECLARE t RECORD; asset BIGINT; buy_id BIGINT; sell_id BIGINT; event_id UUID; event JSONB;
BEGIN
 FOR t IN SELECT * FROM fixture_trades ORDER BY n LOOP
  SELECT asset_id INTO STRICT asset FROM fixture_assets WHERE symbol=t.symbol;
  buy_id := 9010000 + (t.n * 2 - 1); sell_id := 9010000 + (t.n * 2);
  -- The sell order rests first, so its limit is the engine's execution price.
  INSERT INTO public.orders(id,asset_id,account_id,status,side,quantity,remaining_quantity,price,submitted_at)
  VALUES(sell_id,asset,t.seller_account,'PENDING','SELL',t.quantity,t.quantity,t.price,
         (t.executed_at-interval '5 minutes') AT TIME ZONE 'UTC'),
        (buy_id,asset,t.buyer_account,'PENDING','BUY',t.quantity,t.quantity,t.price+1,
         (t.executed_at-interval '3 minutes') AT TIME ZONE 'UTC');
  PERFORM pg_temp.fixture_order_event(sell_id,'ORDER_SUBMITTED',t.executed_at-interval '5 minutes','{}');
  PERFORM pg_temp.fixture_order_event(buy_id,'ORDER_SUBMITTED',t.executed_at-interval '3 minutes','{}');
  IF NOT EXISTS(SELECT 1 FROM public.accounts WHERE id=t.buyer_account AND balance>=t.quantity*t.price)
     OR NOT EXISTS(SELECT 1 FROM public.holdings WHERE account_id=t.seller_account AND asset_id=asset AND quantity>=t.quantity)
  THEN RAISE EXCEPTION 'Unfunded fixture trade %',t.trade_id; END IF;
  UPDATE public.accounts SET balance=balance-t.quantity*t.price WHERE id=t.buyer_account;
  UPDATE public.accounts SET balance=balance+t.quantity*t.price WHERE id=t.seller_account;
  UPDATE public.holdings SET average_price=(quantity*average_price+t.quantity*t.price)/(quantity+t.quantity),
      quantity=quantity+t.quantity WHERE account_id=t.buyer_account AND asset_id=asset;
  UPDATE public.holdings SET quantity=quantity-t.quantity WHERE account_id=t.seller_account AND asset_id=asset;
  UPDATE public.orders SET status='COMPLETED',remaining_quantity=0 WHERE id IN(buy_id,sell_id);
  INSERT INTO public.transactions(id,asset_id,quantity,execution_price,buy_order_id,sell_order_id,status,executed_at)
  VALUES(t.trade_id,asset,t.quantity,t.price,buy_id,sell_id,'COMPLETED',t.executed_at);
  event_id := md5('shared-v4-settlement-' || t.trade_id)::uuid;
  SELECT jsonb_build_object('schemaVersion',1,'eventId',event_id,'type','TradeSettled',
    'occurredAt',t.executed_at,'transactionId',t.trade_id,'assetId',asset,'instrument',t.symbol,
    'assetType',(SELECT asset_type FROM public.assets WHERE id=asset),'quantity',t.quantity,'executionPrice',t.price,'currency','USD',
    'buyer',jsonb_build_object('orderId',buy_id,'accountId',b.id,'userId',bu.id,
      'accountType',b.account_type,'currency',b.currency,'fullName',bu.full_name,'email',bu.email),
    'seller',jsonb_build_object('orderId',sell_id,'accountId',s.id,'userId',su.id,
      'accountType',s.account_type,'currency',s.currency,'fullName',su.full_name,'email',su.email))
  INTO event FROM public.accounts b JOIN public.users bu ON bu.id=b.owner_id
  CROSS JOIN public.accounts s JOIN public.users su ON su.id=s.owner_id
  WHERE b.id=t.buyer_account AND s.id=t.seller_account;
  INSERT INTO public.analytics_outbox(id,transaction_id,payload,created_at)
  VALUES(event_id,t.trade_id,event::text,t.executed_at);
  PERFORM pg_temp.fixture_order_event(buy_id,'FILL_RECORDED',t.executed_at,
    jsonb_build_object('transactionId',t.trade_id,'executionPrice',t.price,'filledQuantity',t.quantity,
      'priceBasis','RESTING_ORDER_PRICE','marketQuoteRecorded',false));
  PERFORM pg_temp.fixture_order_event(sell_id,'FILL_RECORDED',t.executed_at,
    jsonb_build_object('transactionId',t.trade_id,'executionPrice',t.price,'filledQuantity',t.quantity,
      'priceBasis','RESTING_ORDER_PRICE','marketQuoteRecorded',false));
 END LOOP;
END $$;
DROP FUNCTION pg_temp.fixture_order_event(BIGINT,TEXT,TIMESTAMPTZ,JSONB);
DROP SEQUENCE pg_temp.fixture_audit_sequence;

-- Keep future generated IDs above the explicitly reserved shared fixture IDs.
DO $$
DECLARE tab TEXT; col TEXT; seq TEXT; maximum BIGINT;
BEGIN
 FOREACH tab IN ARRAY ARRAY['users','accounts','assets','holdings','orders','transactions','order_audit_outbox'] LOOP
  col := CASE WHEN tab='order_audit_outbox' THEN 'sequence' ELSE 'id' END;
  seq := pg_get_serial_sequence('public.'||tab,col);
  EXECUTE format('SELECT max(%I) FROM public.%I',col,tab) INTO maximum;
  PERFORM setval(seq,GREATEST(maximum,COALESCE(pg_sequence_last_value(seq::regclass),1)),true);
 END LOOP;
END $$;
