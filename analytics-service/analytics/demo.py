"""Explicit, append-only synthetic OLAP fixtures. Never invoked by application startup."""
import argparse
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from uuid import NAMESPACE_URL, uuid5
from psycopg.types.json import Jsonb
from .config import Settings
from .database import make_pool
from .operations import Operations, serializable


def identity(label):
    return uuid5(NAMESPACE_URL, 'jarbis-admin-demo/' + label)


def seed(conn, *, now=None, assignee=None):
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        raise ValueError('Demo timestamps must include a timezone')
    now = now.astimezone(timezone.utc)
    day = now.date()
    batch = day.isoformat()
    base = 900_000_000_000_000 + day.toordinal() * 10000
    conn.execute("SELECT pg_advisory_xact_lock(hashtextextended('jarbis-admin-demo',0))")
    required = ('analytics.transaction_facts','analytics.trade_participants','analytics.order_events',
                'analytics.client_portfolio_snapshots','operations.disputes')
    for table in required:
        if conn.execute('SELECT to_regclass(%s) AS name',(table,)).fetchone()['name'] is None:
            raise ValueError('Start the analytics API to apply migrations before seeding')
    conn.execute('CREATE TABLE IF NOT EXISTS reporting_migrations.demo_batches (batch_date DATE PRIMARY KEY, loaded_at TIMESTAMPTZ NOT NULL DEFAULT now(), summary JSONB NOT NULL)')
    previous = conn.execute('SELECT summary FROM reporting_migrations.demo_batches WHERE batch_date=%s',(day,)).fetchone()
    if previous:
        return {**previous['summary'], 'alreadyLoaded': True}
    # Fail instead of overwriting or silently adopting an unrelated record with the same ID.
    for table, column in [('analytics.transaction_facts','transaction_id'),('analytics.dim_users','user_id'),
                          ('analytics.dim_accounts','account_id'),('analytics.dim_assets','asset_id'),
                          ('analytics.order_facts','order_id'),('analytics.order_events','order_id'),
                          ('analytics.order_events','audit_sequence'),('analytics.client_portfolio_snapshots','user_id'),
                          ('operations.disputes','order_id')]:
        if conn.execute(f'SELECT 1 FROM {table} WHERE {column}>=%s AND {column}<%s LIMIT 1',(base,base+10000)).fetchone():
            raise ValueError('Demo ID range is occupied; no data was changed')
    instruments=[('AAPL','Apple','STOCK','190'),('MSFT','Microsoft','STOCK','420'),
                 ('NVDA','Nvidia','STOCK','130'),('TSLA','Tesla','STOCK','240'),
                 ('SPY','S&P 500 ETF','ETF','560'),('BTC','Bitcoin','CRYPTO','62000')]
    for i,(ticker,name,kind,price) in enumerate(instruments):
        conn.execute('INSERT INTO analytics.dim_assets(asset_id,ticker,asset_name,asset_type) VALUES (%s,%s,%s,%s)',
                     (base+100+i,'DEMO-'+ticker,'DEMO · '+name,kind))
    for client in range(54):
        uid=base+200+client; account=base+300+client
        currency=('USD','EUR','INR')[(client//2)%3]
        conn.execute("INSERT INTO analytics.dim_users(user_id,full_name,email) VALUES (%s,%s,'')",(uid,'DEMO Client '+str(client+1)))
        conn.execute('INSERT INTO analytics.dim_accounts(account_id,user_id,account_type,currency) VALUES (%s,%s,%s,%s)',(account,uid,'INVESTMENT',currency))
        band=((client//2)//3)%3
        cost=(Decimal('7500'),Decimal('45000'),Decimal('250000'))[band]
        if band==1 and client%2==0: cost=Decimal('10000')
        if band==1 and client%2==1: cost=Decimal('100000')
        conn.execute("INSERT INTO analytics.client_portfolio_snapshots(user_id,currency,captured_at,holdings_cost,data_source) VALUES (%s,%s,%s,%s,'LEGACY')",
                     (uid,currency,now-timedelta(days=180),cost))
    sequence=base+5000
    orders=[]
    def lifecycle(order,client,asset,side,quantity,price,currency,at,kind,status,details):
        nonlocal sequence
        sequence+=1
        uid=base+200+client; account=base+300+client
        payload={'schemaVersion':1,'eventId':str(identity(f'{batch}/event/{sequence}')),'type':'OrderLifecycle',
                 'auditSequence':sequence,'eventType':kind,'occurredAt':at.isoformat(),'orderId':order,
                 'clientId':uid,'accountId':account,'assetId':asset,'instrument':'DEMO-'+instruments[asset-base-100][0],
                 'side':side,'status':status,'quantity':str(quantity),'remainingQuantity':str(0 if status=='COMPLETED' else quantity),
                 'orderPrice':str(price),'currency':currency,'cashBalance':details.get('after',{}).get('cashBalance','1000000'),'holdingQuantity':details.get('after',{}).get('holdingQuantity','100'),
                 'actor':'DEMO_SEED','details':{'synthetic':True,'batch':batch,**details}}
        conn.execute('INSERT INTO analytics.order_events(event_id,audit_sequence,order_id,client_id,account_id,asset_id,event_type,occurred_at,payload) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)',
                     (identity(f'{batch}/event/{sequence}'),sequence,order,uid,account,asset,kind,at,Jsonb(payload)))
    for pair in range(27):
        cohort=pair//9
        currency=('USD','EUR','INR')[pair%3]
        for n in range(8):
            index=pair*8+n; trade=base+1000+index; buy=base+2000+index*2; sell=buy+1
            buyer=pair*2; seller=buyer+1; asset=base+100+(index%6)
            price=Decimal(instruments[index%6][3])
            quantity=Decimal((n%4)+1) / (Decimal('100') if index%6==5 else Decimal('1'))
            days=(n%day.day) if cohort==0 else (day.day+n*3 if cohort==1 else 100+n*5)
            at=now-timedelta(days=days,minutes=5)
            conn.execute("""INSERT INTO analytics.transaction_facts(transaction_id,asset_id,ticker,buy_order_id,sell_order_id,
                buyer_account_id,seller_account_id,buyer_user_id,seller_user_id,status,quantity,execution_price,
                transaction_timestamp,event_date,currency,data_source,ingested_at)
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,'COMPLETED',%s,%s,%s,%s,%s,'LEGACY',now())""",
                         (trade,asset,'DEMO-'+instruments[index%6][0],buy,sell,base+300+buyer,base+300+seller,
                          base+200+buyer,base+200+seller,quantity,price,at,at.date(),currency))
            for order,client,side in [(buy,buyer,'BUY'),(sell,seller,'SELL')]:
                conn.execute('INSERT INTO analytics.trade_participants(transaction_id,user_id,account_id,account_type,side) VALUES (%s,%s,%s,%s,%s)',(trade,base+200+client,base+300+client,'INVESTMENT',side))
                conn.execute('INSERT INTO analytics.order_facts(order_id,asset_id,ticker,account_id,user_id,side,status,quantity,price,order_timestamp,event_date) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)',
                             (order,asset,'DEMO-'+instruments[index%6][0],base+300+client,base+200+client,side,'COMPLETED',quantity,price,at-timedelta(seconds=30),at.date()))
                lifecycle(order,client,asset,side,quantity,price,currency,at-timedelta(seconds=30),'ORDER_SUBMITTED','PENDING',{'fullTradingRuleValidation':False})
                lifecycle(order,client,asset,side,quantity,price,currency,at-timedelta(seconds=5),'EXECUTION_PRICED','PENDING',
                          {'transactionId':trade,'executionPrice':str(price),'priceBasis':'RESTING_ORDER_PRICE','marketQuoteRecorded':False})
                sign=1 if side=='BUY' else -1
                lifecycle(order,client,asset,side,quantity,price,currency,at,'FILL_RECORDED','COMPLETED',
                          {'transactionId':trade,'executionPrice':str(price),'filledQuantity':str(quantity),
                           'before':{'cashBalance':'1000000','holdingQuantity':'100'},
                           'after':{'cashBalance':str(Decimal('1000000')-sign*quantity*price),'holdingQuantity':str(Decimal('100')+sign*quantity)}})
            if n==0 and cohort==0: orders.append(buy)
    for i in range(12):
        order=base+3000+i;at=now-timedelta(minutes=3);asset=base+100;currency=('USD','EUR','INR')[i%3]
        # These are observed attempts, not completed fills or fabricated FAILED order states.
        client=(i%3)*2
        lifecycle(order,client,asset,'BUY',Decimal('2'),Decimal('190'),currency,at,'ORDER_SUBMITTED','PENDING',{'fullTradingRuleValidation':False})
        if i%2:
            lifecycle(order,client,asset,'BUY',Decimal('2'),Decimal('190'),currency,at+timedelta(seconds=2),'SETTLEMENT_FAILED','PENDING',
                      {'errorCode':'DEMO_INSUFFICIENT_CASH','financialChangesCommitted':False})
    for i,order in enumerate(orders[:8]):
        evidence=Operations.evidence(conn,order)
        id=identity(f'{batch}/case/{i}');opened=now-timedelta(minutes=1)
        states=['OPEN','IN_REVIEW','RESOLVED','CLOSED'][:i%4+1]
        reason='DEMO: customer requests investigation of the recorded execution price.'
        resolution='DEMO: recorded order-book pricing reviewed; no real customer or refund.' if len(states)>=3 else None
        conn.execute('''INSERT INTO operations.disputes(id,request_id,order_id,client_id,category,reason,status,assigned_to,
                     opened_by,opened_at,updated_at,resolution,version,evidence) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)''',
                     (id,identity(f'{batch}/request/{i}'),order,evidence['clientId'],'EXECUTION_PRICE',reason,states[-1],assignee,
                      'DEMO_SEED',opened,opened+timedelta(seconds=len(states)-1),resolution,len(states),Jsonb(serializable(evidence))))
        for step,state in enumerate(states):
            details={'synthetic':True,'batch':batch,'to':state,'note':'DEMO workflow example'}
            if step: details['from']=states[step-1]
            if state=='RESOLVED':details['resolution']=resolution
            conn.execute('INSERT INTO operations.dispute_events(dispute_id,actor,kind,occurred_at,details) VALUES (%s,%s,%s,%s,%s)',
                         (id,'DEMO_SEED','OPENED' if step==0 else 'UPDATED',opened+timedelta(seconds=step),Jsonb(details)))
    summary={'batch':batch,'dataset':'LEGACY','clients':54,'instruments':6,'completedTrades':216,'portfolioSnapshots':54,
             'orderEvents':sequence-(base+5000),'disputes':8,'exampleTradeId':base+1000,'exampleOrderId':orders[0],
             'alreadyLoaded':False}
    conn.execute('INSERT INTO reporting_migrations.demo_batches(batch_date,summary) VALUES (%s,%s)',(day,Jsonb(summary)))
    return summary


def main():
    parser=argparse.ArgumentParser(description='Append clearly marked synthetic LEGACY data to the configured OLAP database.')
    parser.add_argument('--allow-demo-data',action='store_true',help='Explicitly enable fixture insertion; never used by app startup')
    args=parser.parse_args()
    if not args.allow_demo_data: parser.error('Pass --allow-demo-data to insert synthetic records')
    settings=Settings.load()
    pool=make_pool(settings)
    try:
        pool.open(wait=True)
        with pool.connection() as conn:
            result=seed(conn,assignee=settings.operations_username or None)
        import json
        print(json.dumps(result,indent=2))
        print('Select LEGACY in the dashboard. Recent charts show active/occasional clients; use 180 days for dormant history.')
    finally:
        pool.close()


if __name__=='__main__':
    main()
