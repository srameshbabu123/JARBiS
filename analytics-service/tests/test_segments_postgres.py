from copy import deepcopy
from decimal import Decimal
from uuid import uuid4
import pytest
from test_postgres import pool, clear
from analytics.models import ReportFilter, SettlementEvent
from analytics.reports import Reports
from projection_fixtures import project

pytestmark=pytest.mark.postgres

def f(**kw):
    return ReportFilter(**{'from':'2026-07-01','to':'2026-10-16',**kw})

def test_portfolio_boundaries_activity_and_same_client_matching(pool,event_data):
    for trade,at,buyer,seller in [(1,'2026-10-12T12:00:00Z',1,2),(2,'2026-09-12T12:00:00Z',3,4),(3,'2026-07-01T12:00:00Z',5,6)]:
        data=deepcopy(event_data)
        data.update(eventId=str(uuid4()),transactionId=trade,occurredAt=at)
        for name,client in [('buyer',buyer),('seller',seller)]:
            data[name].update(userId=client,accountId=client*10,orderId=client*100)
        project(pool,SettlementEvent.model_validate(data))
    with pool.connection() as conn:
        for user,cost in [(1,'9999.99'),(2,'10000'),(3,'100000'),(4,'100000.01'),(6,'0')]:
            conn.execute("INSERT INTO analytics.client_portfolio_snapshots VALUES (%s,'USD','2026-07-01T00:00:00Z',%s)",(user,Decimal(cost)))
        # A future snapshot must not alter this report's cohort.
        conn.execute("INSERT INTO analytics.client_portfolio_snapshots VALUES (1,'USD','2026-10-17T00:00:00Z',999999)")
    reports=Reports(pool)
    assert reports.dashboard(f())['summary']['value']==Decimal('6')
    assert reports.dashboard(f())['summary']['notional']==Decimal('60')
    assert reports.dashboard(f(activityLevel='ACTIVE'))['summary']['clients']==2
    assert reports.dashboard(f(activityLevel='OCCASIONAL'))['summary']['clients']==2
    assert reports.dashboard(f(activityLevel='DORMANT'))['summary']['clients']==2
    assert reports.dashboard(f(portfolioSize='BETWEEN_10K_100K'))['summary']['clients']==2
    assert reports.dashboard(f(portfolioSize='OVER_100K'))['summary']['clients']==1
    assert reports.dashboard(f(portfolioSize='UNDER_10K'))['summary']['clients']==2  # missing user 5 excluded
    match=reports.dashboard(f(portfolioSize='UNDER_10K',activityLevel='ACTIVE'))
    assert match['summary']['trades']==1 and match['summary']['clients']==1
    assert sum(r['clients'] for r in match['clients'])==1
    # BUY participant is small, SELL participant is CHECKING. Different sides cannot satisfy separate filters.
    assert reports.dashboard(f(portfolioSize='UNDER_10K',segment='CHECKING',activityLevel='ACTIVE'))['summary']['trades']==0
    assert reports.dashboard(f(portfolioSize='UNDER_10K',dataset='LEGACY'))['portfolioCoverage']['clients']==0
    assert reports.trades(f(activityLevel='OCCASIONAL'),0)['total']==1


def test_combined_history_counts_both_sources_and_classifies_clients_together(pool,event_data):
    first=deepcopy(event_data)
    first.update(occurredAt='2026-09-12T12:00:00Z')
    project(pool,SettlementEvent.model_validate(first))
    second=deepcopy(event_data)
    second.update(eventId=str(uuid4()),transactionId=2,occurredAt='2026-10-12T12:00:00Z',quantity='3')
    project(pool,SettlementEvent.model_validate(second))
    with pool.connection() as conn:
        conn.execute("UPDATE analytics.transaction_facts SET data_source='LEGACY' WHERE transaction_id=2")
        conn.execute("INSERT INTO analytics.client_portfolio_snapshots VALUES (1,'USD','2026-09-01',9999,'LIVE'),(1,'USD','2026-10-01',250000,'LEGACY')")
    filters={'from':'2026-09-01','to':'2026-10-16'}
    reports=Reports(pool)
    report=reports.dashboard(ReportFilter(**filters))
    assert report['summary']['trades']==2
    assert report['summary']['value']==Decimal('5')
    assert report['summary']['clients']==2
    assert reports.dashboard(ReportFilter(**filters,activityLevel='ACTIVE'))['summary']['trades']==2
    assert reports.dashboard(ReportFilter(**filters,portfolioSize='OVER_100K'))['summary']['clients']==1
    assert reports.trades(ReportFilter(**filters),0)['total']==2
    # Optional explicit source query stays backward compatible; UI never sends it.
    assert reports.dashboard(ReportFilter(**filters,dataset='LIVE',activityLevel='ACTIVE'))['summary']['trades']==0
