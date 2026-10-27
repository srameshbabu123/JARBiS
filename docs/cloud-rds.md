# RDS cloud-test setup

Local development remains unchanged. Use [docker-compose.cloud.yml](../docker-compose.cloud.yml)
**alone**, not merged with the local file. This is a starting point for cloud
testing, not a fully hardened/highly available production deployment.

## 1. AWS networking and certificate

Use private RDS PostgreSQL with inbound TCP 5432 allowed from the EC2 application
security group only. EC2 needs network routing to RDS. Enable encryption, backups
and deletion protection. Use SSM or restricted management access for EC2.

Download the official AWS certificate bundle to `certs/global-bundle.pem`:
<https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem>.
It is mounted read-only into all database-client containers. URLs use the RDS
hostname and `sslmode=verify-full`; do not disable certificate validation.

## 2. Create databases and roles once

Connect to the instance's `postgres` database using your existing verified TLS
psql command as the RDS administrator. For a **fresh** instance, execute
[bootstrap-rds.psql](../docker/cloud/bootstrap-rds.psql) using psql's `\i` command.
It creates `jarbis_app` / `jarbis_analytics` login roles and their respective
`jarbis_db` / `jarbis_olap` databases. Passwords are prompted interactively by psql;
do not send passwords through chat. The script does not use or store passwords
in SQL. Run without a surrounding transaction and review before execution.

If any roles/databases already exist, inspect their ownership and privileges
first instead of blindly rerunning the script. It deliberately fails on duplicate
objects; a partial bootstrap must be inspected before resuming. Both databases
are writable and can live on this one instance. OLAP is not a read replica.

## 3. Separate environment

Copy [.env.cloud.example](../.env.cloud.example) to `.env.cloud`, restrict its
permissions to the deployment user, and replace password/hash/secret placeholders.
The RDS endpoint is already filled in. Use the role passwords created above,
not the RDS administrator credentials. Set a random JWT secret of at least 32
bytes, distinct analyst/operations BCrypt hashes, and the actual HTTPS admin
origin. Single-quote BCrypt hashes to preserve dollar signs. Never commit secrets.
For deployment, prefer fetching secrets using the EC2 role from Secrets Manager.
Use a clean checkout for image builds so ignored developer Spring properties
are not accidentally packaged into the backend image.

## 4. HTTPS routing

This template assumes HTTPS termination at an ALB with ACM:

| Host | EC2 target port |
| --- | --- |
| app.example.com | 8095 |
| admin.example.com | 8083 |

Redirect HTTP to HTTPS. Allow these target ports **only from the ALB security
group**, never directly from the Internet. Proxy configs mark requests as HTTPS
and fix the client refresh-cookie path. Secure cookies require HTTPS; direct
HTTP access is not supported. API, Redis and Kafka ports are not published.
Restrict admin access further using VPN/identity-aware access and rate limiting.

## 5. Start and verify

Run from the repository root using a distinct project name:

```bash
docker compose -p jarbis-cloud -f docker-compose.cloud.yml --env-file .env.cloud config --quiet
docker compose -p jarbis-cloud -f docker-compose.cloud.yml --env-file .env.cloud up -d --build
docker compose -p jarbis-cloud -f docker-compose.cloud.yml --env-file .env.cloud ps -a
docker compose -p jarbis-cloud -f docker-compose.cloud.yml --env-file .env.cloud logs --tail=100 olap-migrate jarbis-backend analytics
```

Do not print resolved Compose configuration into logs: it contains credentials.
The cloud stack uses the same UI ports as local, so stop the local UI services
first if testing on the same host. Do not delete local volumes to switch stacks.

Hibernate **update** creates OLTP mapped tables inside the existing database,
as requested for initial testing. It does NOT run the OLTP fixture SQL or create
the databases. OLAP schema is created by the existing Python migration job;
successful exit 0 gates API/backend startup. If RDS is unreachable, fix network,
certificate or credential issues and rerun startup. No local data is copied.

Expect no demo users/funded accounts/trades. Symbols are initialized by the
application. Synthetic liquidity is disabled and quote polling defaults off.
Enable polling only after configuring approved market-data credentials. Verify
HTTPS registration/login/refresh and both staff logins before testing trades.

## Before real production use

- Replace Hibernate update with reviewed schema migrations plus validate.
  Keep development seed users/balances/trades out of production migrations.
- Current schema initialization/audit triggers need DDL permissions. Database
  owner roles here are scoped to their respective databases but are not
  fully restricted runtime roles; separating those requires application changes.
- Move builds to CI/ECR with immutable image references; add bounded logs,
  monitoring, backup/restore drills, secret rotation and resource sizing.
- Single EC2, one Kafka broker and Redis are not HA. Evaluate ECS/MSK/ElastiCache
  and RDS Multi-AZ for availability requirements.
- Existing database adoption/data transfer needs a snapshot, rehearsal and
  coordinated cutover; do not point startup schema updates at valuable existing
  data without review. Audit financial controls before any real-money launch.