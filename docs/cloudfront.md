# One CloudFront distribution

Serve the client at `/` and the admin UI at `/staff/` on the same HTTPS hostname.
Keep `/admin/*` for the existing analytics/investigations API, not the UI page.
The cloud admin Nginx config strips `/staff/` for static assets and redirects
`/staff` to `/staff/`. No CloudFront rewrite function is needed.

## Origins

Create two custom origins using the EC2 **public DNS hostname**, not its private
hostname. Leave Origin Path empty. Put the port in the port field, not the domain.

| Origin name | Domain | HTTP port | Origin protocol |
| --- | --- | --- | --- |
| jarbis-client | EC2 public DNS | 8095 | HTTP only (initial testing) |
| jarbis-admin | Same EC2 public DNS | 8083 | HTTP only (initial testing) |

For production, use HTTPS to the origin with a valid certificate, e.g. through
an HTTPS ALB. Viewer HTTPS alone does not encrypt the CloudFront-to-EC2 hop.
Confirm Docker publishes both ports, especially `8083:8080` for admin.
Use an Elastic IP/stable origin endpoint so EC2 stop/start does not break routing.

## Behaviors

Replace the old exact `/admin` behavior with these routes. All specific routes
must precede Default. They do not overlap one another.

| Path pattern | Origin | Allowed methods |
| --- | --- | --- |
| /staff | jarbis-admin | GET, HEAD |
| /staff/* | jarbis-admin | GET, HEAD |
| /admin/* | jarbis-admin | All seven |
| /session/* | jarbis-admin | All seven |
| /login | jarbis-admin | All seven |
| /logout | jarbis-admin | All seven |
| /api/* | jarbis-client | All seven |
| Default (*) | jarbis-client | GET, HEAD |

For **every behavior** initially select:

- Viewer protocol policy: **Redirect HTTP to HTTPS**.
- Cache policy: **Managed-CachingDisabled** (minimum TTL zero).
- Origin request policy: **Managed-AllViewerExceptHostHeader**.
- Compression: enabled.

Cookies, query strings, Authorization, Origin and X-CSRF-TOKEN must reach the
API origins. Do not cache authentication, CSRF, session or report responses.
Static caching can be introduced later with a separate versioned-asset policy.
Leave Default Root Object empty: each Nginx server already handles its index.
Do not configure distribution-wide 403/404-to-index.html error responses; these
would hide API errors and missing admin assets. Set error caching minimum TTL
to zero for 403/404 if adding custom error entries, without changing status/body.

## Application environment and rollout

Set `ANALYTICS_ALLOWED_ORIGINS` in the private cloud environment to the shared
HTTPS origin, for example `https://d123example.cloudfront.net`, or your custom
domain. Do not append `/staff/`. Keep secure cookies enabled. No change to the
existing root-relative admin API URLs or cookie paths is needed.

Push/pull the proxy change to EC2, then apply it and the origin setting:

```bash
docker compose -p jarbis-cloud -f docker-compose.cloud.yml --env-file .env.cloud up -d analytics admin-ui
docker compose -p jarbis-cloud -f docker-compose.cloud.yml --env-file .env.cloud exec -T admin-ui nginx -t
docker compose -p jarbis-cloud -f docker-compose.cloud.yml --env-file .env.cloud exec -T admin-ui nginx -s reload
```

The Nginx config is bind-mounted; no image rebuild is required for this change.
Wait for CloudFront deployment, then invalidate `/*` once to clear previously
cached HTML/API responses from the old CachingOptimized behaviors.

## Verify

- `https://DISTRIBUTION/`: client application.
- `https://DISTRIBUTION/staff`: relative redirect to `/staff/`.
- `https://DISTRIBUTION/staff/`: admin login, with JS/CSS under `/staff/`.
- `/session/me` before login: 401 JSON, not client HTML.
- Test staff CSRF/login/logout, report filters and downloads, client login/refresh.
- Missing `/staff/missing.js`: 404, never Angular HTML.

## Origin security

Restrict EC2 ingress on 8095/8083 to the AWS-managed CloudFront origin-facing
prefix list. Keep RDS restricted to the application security group. Add a secret
CloudFront origin header and Nginx validation to prevent another distribution
from reaching your origin; the prefix list alone is not distribution-specific.
The existing proxy assumes trusted HTTPS termination and is not for direct
public HTTP. Add WAF/rate limits and stronger access controls for staff routes.

Client and admin share an origin in this architecture, so browser security does
not isolate them as separate sites. Keep their authentication tokens distinct,
and prefer separate hostnames/distributions if stronger isolation is required.