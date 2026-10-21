-- A deterministic local scenario. Existing Alice/Bob accounts are not modified.
-- Each new client starts with USD 25,000 and 20 shares of each of GOOGL/AMZN/SPY.
-- The normal durable relays deliver these exact source events to OLAP.
CREATE TEMP TABLE fixture_clients(id BIGINT, account_id BIGINT, full_name TEXT, email TEXT) ON COMMIT DROP;
INSERT INTO fixture_clients VALUES
 (9003001,9003101,'Cara Local','cara@example.test'),
 (9003002,9003102,'Dan Local','dan@example.test'),
 (9003003,9003103,'Emma Local','emma@example.test'),
 (9003004,9003104,'Finn Local','finn@example.test');
INSERT INTO public.users(id,full_name,email,password_hash)
SELECT c.id,c.full_name,c.email,'$2b$10$oYjqramDU/vVNANvKwm2N.VTbQCkVeHvlssLA2pam67S.ZShF9SlS' FROM fixture_clients c;
INSERT INTO public.accounts(id,account_type,currency,balance,owner_id)
SELECT account_id,'INVESTMENT','USD',25000,id FROM fixture_clients;
-- Use positive IDs so analytics filters and investigation routes accept the fixtures.
INSERT INTO public.assets(id,name,asset_type)
SELECT x.id,x.symbol,x.kind FROM (VALUES (9004001,'GOOGL','STOCK'),(9004002,'AMZN','STOCK'),(9004003,'SPY','ETF')) x(id,symbol,kind)
WHERE NOT EXISTS(SELECT 1 FROM public.assets a WHERE upper(a.name)=x.symbol AND a.id>0);
CREATE TEMP TABLE fixture_assets(symbol TEXT,asset_id BIGINT,initial_cost DOUBLE PRECISION) ON COMMIT DROP;
INSERT INTO fixture_assets
SELECT s.symbol,(SELECT id FROM public.assets WHERE upper(name)=s.symbol AND id>0 ORDER BY id LIMIT 1),s.cost
FROM (VALUES ('GOOGL',170.0),('AMZN',180.0),('SPY',540.0)) s(symbol,cost);
INSERT INTO public.holdings(id,account_id,asset_id,quantity,average_price)
SELECT 9003200 + row_number() OVER(ORDER BY c.id,a.symbol),c.account_id,a.asset_id,20,a.initial_cost
FROM fixture_clients c CROSS JOIN fixture_assets a;
CREATE TEMP TABLE fixture_trades(
 n INT,trade_id BIGINT,buyer_account BIGINT,seller_account BIGINT,symbol TEXT,
 quantity DOUBLE PRECISION,price DOUBLE PRECISION,executed_at TIMESTAMPTZ
) ON COMMIT DROP;
INSERT INTO fixture_trades VALUES
 (1,9007001,9003101,9003102,'GOOGL',5,180,'2026-10-12 14:00:00+00'),
 (2,9007002,9003103,9003104,'AMZN',3,190,'2026-10-12 15:00:00+00'),
 (3,9007003,9003102,9003103,'SPY',8,550,'2026-10-13 14:00:00+00'),
 (4,9007004,9003104,9003101,'GOOGL',4,182,'2026-10-13 15:00:00+00'),
 (5,9007005,9003101,9003103,'AMZN',2,192,'2026-10-14 14:00:00+00'),
 (6,9007006,9003103,9003102,'SPY',6,552,'2026-10-14 15:00:00+00');
CREATE TEMP SEQUENCE fixture_audit_sequence MINVALUE 90010001 MAXVALUE 90019999 START WITH 90010001;
CREATE FUNCTION pg_temp.fixture_order_event(order_id BIGINT,kind TEXT,at_time TIMESTAMPTZ,details JSONB)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE snapshot JSONB; event_id UUID; seq BIGINT;
BEGIN
 seq := nextval('pg_temp.fixture_audit_sequence');
 event_id := md5('shared-v3-audit-' || seq)::uuid;
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
  buy_id := 9005000 + (t.n * 2 - 1); sell_id := 9005000 + (t.n * 2);
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
  event_id := md5('shared-v3-settlement-' || t.trade_id)::uuid;
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
-- A non-crossing pending pair: neither is represented as an executed trade.
INSERT INTO public.orders(id,asset_id,account_id,status,side,quantity,remaining_quantity,price,submitted_at)
SELECT 9005101,asset_id,9003101,'PENDING','BUY',3,3,150,'2026-10-15 14:00:00' FROM fixture_assets WHERE symbol='GOOGL';
INSERT INTO public.orders(id,asset_id,account_id,status,side,quantity,remaining_quantity,price,submitted_at)
SELECT 9005102,asset_id,9003102,'PENDING','SELL',4,4,220,'2026-10-15 14:05:00' FROM fixture_assets WHERE symbol='GOOGL';
SELECT pg_temp.fixture_order_event(9005101,'ORDER_SUBMITTED','2026-10-15 14:00:00+00','{}');
SELECT pg_temp.fixture_order_event(9005102,'ORDER_SUBMITTED','2026-10-15 14:05:00+00','{}');
INSERT INTO public.orders(id,asset_id,account_id,status,side,quantity,remaining_quantity,price,submitted_at)
SELECT 9005103,asset_id,9003103,'PENDING','BUY',2,2,175,'2026-10-15 15:00:00' FROM fixture_assets WHERE symbol='AMZN';
SELECT pg_temp.fixture_order_event(9005103,'ORDER_SUBMITTED','2026-10-15 15:00:00+00','{}');
UPDATE public.orders SET status='CANCELLED' WHERE id=9005103;
SELECT pg_temp.fixture_order_event(9005103,'ORDER_CANCELLED','2026-10-15 15:10:00+00','{}');
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
