from datetime import datetime, timedelta, timezone
import pytest
from test_postgres import pool, clear
from analytics.demo import seed
from analytics.reports import Reports
from analytics.models import ReportFilter

pytestmark=pytest.mark.postgres


def test_demo_is_atomic_idempotent_and_supports_all_segments_and_cases(pool):
    at=datetime(2026,10,16,14,0,tzinfo=timezone.utc)
    # Existing shared OLAP installations may have a different physical column order.
    with pool.connection() as conn:
        conn.execute('ALTER TABLE analytics.order_facts DROP COLUMN quantity CASCADE')
        conn.execute('ALTER TABLE analytics.order_facts ADD COLUMN quantity NUMERIC(18,8) NOT NULL')
        conn.execute('ALTER TABLE analytics.dim_assets ADD COLUMN optional_external_metadata TEXT')
    with pytest.raises(RuntimeError):
        with pool.connection() as conn:
            seed(conn,now=at,assignee='david')
            raise RuntimeError('Simulate interrupted load')
    with pool.connection() as conn:
        assert conn.execute('SELECT COUNT(*) AS n FROM analytics.transaction_facts').fetchone()['n']==0
        first=seed(conn,now=at,assignee='david')
    with pool.connection() as conn:
        again=seed(conn,now=at+timedelta(minutes=1),assignee='david')
        assert again['alreadyLoaded'] is True
        assert conn.execute('SELECT COUNT(*) AS n FROM analytics.transaction_facts').fetchone()['n']==216
        assert conn.execute("SELECT COUNT(*) AS n FROM analytics.transaction_facts WHERE data_source='LIVE'").fetchone()['n']==0
        assert conn.execute('SELECT COUNT(*) AS n FROM analytics.processed_events').fetchone()['n']==0
        assert conn.execute('SELECT COUNT(*) AS n FROM analytics.order_events').fetchone()['n']==first['orderEvents']
        states=conn.execute('SELECT status,COUNT(*) AS n FROM operations.disputes GROUP BY status').fetchall()
        assert {r['status']:r['n'] for r in states}=={'OPEN':2,'IN_REVIEW':2,'RESOLVED':2,'CLOSED':2}
        case=conn.execute('SELECT evidence FROM operations.disputes LIMIT 1').fetchone()['evidence']
        assert case['eventCount']==3
        assert case['timeline'][0]['payload']['details']['synthetic'] is True
    reports=Reports(pool)
    for currency in ['USD','EUR','INR']:
        for band in ['UNDER_10K','BETWEEN_10K_100K','OVER_100K']:
            for activity in ['ACTIVE','OCCASIONAL','DORMANT']:
                f=ReportFilter(**{'from':'2026-04-19','to':'2026-10-16','dataset':'LEGACY','currency':currency,'portfolioSize':band,'activityLevel':activity})
                result=reports.dashboard(f)
                assert result['summary']['clients']==2
                assert result['summary']['trades']==8
                assert result['summary']['value']>0
