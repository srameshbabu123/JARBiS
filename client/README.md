# Velocity client: connect to Java

The browser calls `/api/...` on the Angular dev server. `proxy.conf.js` removes `/api` and forwards the request, including its JWT, to Spring Boot. The client never opens a database connection or calls Fauxnance directly.

## Start the backend

On the machine hosting Java, pull this branch and rebuild the backend service:

```bash
git switch feature/client-backend-integration
git pull --ff-only
docker compose --env-file .env.analytics up -d --build jarbis-backend
curl -i http://127.0.0.1:8081/actuator/health
```

Use `docker-compose` instead if that is your installed command. Keep the existing database configuration in your backend environment. The frontend proxy target is the **Java HTTP address**, not PostgreSQL's address or port.

## Start the frontend

Use a Node version supported by `package.json` (Node 24 recommended):

```bash
cd client
npm ci
```

If Java is on the same machine:

```bash
npm start
```

If the frontend runs on Windows and Java runs on EC2, first check that Windows can reach Java. For the backend host verified in this setup:

```bash
curl -i http://10.18.78.59:8081/actuator/health
```

Git Bash / Linux:

```bash
BACKEND_URL=http://10.18.78.59:8081 npm start
```

PowerShell:

```powershell
$env:BACKEND_URL = "http://10.18.78.59:8081"
npm start
```

Replace that IP if the backend moves. Restart `npm start` after changing the target. `BACKEND_URL` is read by Node, not from `.env.analytics`; the default is `http://127.0.0.1:8081`. No database password or market API key belongs in `client/`.

Keep the frontend terminal open. In another terminal on the **frontend machine**:

```bash
curl -i http://127.0.0.1:4200/api/actuator/health
```

Both health checks should return HTTP 200. Open `http://localhost:4200` on that machine. If either check fails, resolve it before testing login: a connection refusal means nothing is listening at that address; a proxy 502 means the dev server cannot reach Java. Same-origin proxy requests do not require browser CORS exceptions.

## Route contracts

| Frontend request | Java route | Use |
| --- | --- | --- |
| POST `/api/auth/register` | POST `/auth/register` | Register and receive JWT |
| POST `/api/auth/login` | POST `/auth/login` | Sign in and receive JWT |
| GET `/api/auth/me` | GET `/auth/me` | Validate restored session / navigation |
| GET `/api/accounts/mine` | GET `/accounts/mine` | Discover owned accounts |
| POST `/api/accounts` | POST `/accounts` | Create an additional owned account |
| POST `/api/accounts/mine/initialize` | POST `/accounts/mine/initialize` | Repair a missing account; repeated calls reuse it |
| GET `/api/accounts/mine/{id}` | GET `/accounts/mine/{id}` | Load owned account |
| GET/PUT `/api/users/{id}` | GET/PUT `/users/{id}` | Load/save own profile |
| GET `/api/users/{id}/balance` | GET `/users/{id}/balance` | Own total balance |
| POST `/api/accounts/{id}/deposit` or `/withdraw` | Same path without `/api` | Update brokerage cash |
| GET `/api/holdings/account/{id}` | GET `/holdings/account/{id}` | Owned account holdings |
| GET `/api/orders/account/{id}` | GET `/orders/account/{id}` | Owned account order history |
| POST `/api/orders` | POST `/orders` | Submit order for an owned account |
| GET `/api/assets` | GET `/assets` | Tradable asset catalog |
| GET `/api/assets/by-symbol/{symbol}` | GET `/assets/by-symbol/{symbol}` | Resolve existing asset ID |
| GET `/api/market-data/{symbol}/latest` | Same path without `/api` | Collected quote |
| GET `/api/market-data/{symbol}/history?from=...&to=...` | Same path without `/api` | Collected history |

The proxy does not insert unknown assets. Add assets through your team's database/data-management process. Negative IDs in the shared sample data are supported.

## Authentication and market data

Login and registration return `{user, token, tokenType}`. The client restores an unexpired session from browser storage, sends `Authorization: Bearer ...` only to `/api/` routes, and verifies `/auth/me` before entering workspace pages. A backend 401 clears the session and returns to login; 403 reports denied access. Profile updates refresh the stored user. Sign-out removes local session/account selection. This branch has stateless access JWTs and no refresh-token or server token-revocation endpoint; expired sessions require signing in again. Use HTTPS for deployed environments.

Set `BROKERAGE_MARKET_DATA_API_KEY` on **the backend**, and run its market-data poller with Redis available. Quotes/history come from Java's collected data; missing quotes return 503 and are never replaced with a browser-to-provider call. History contains only retained observations, not invented long-range prices. The asset catalog drives the initial market list. Watchlists remain browser-local and scoped to the signed-in user; News opens external reporting links rather than an in-app feed.

For a production build, `ng serve` and its proxy are absent. Your web server must serve `dist/client/browser` and forward `/api/` to Java, removing the prefix. This change configures development connectivity; it does not deploy Nginx.

## Verification

```bash
npm run format:check
npm run test:proxy
npx playwright install chromium
npm run test:ui
```

`test:proxy` starts a real Angular dev server and a temporary HTTP upstream, checking nested paths, query strings, request bodies and authorization headers. `test:ui` uses API fixtures to exercise protected page navigation, signup/account initialization, login, session restoration, profile edits, cash changes, quotes/history, order submission, sign-out and 401 handling. `test:trade` and `test:cash` also run this consolidated suite. They do not connect to your live database.

From the repository root, `mvn test` includes `ClientRoutesIntegrationTest`: real Spring MVC routes, JWT validation, method authorization and JSON serialization with mocked business dependencies. On your EC2 deployment, complete a final UI check with your own account: register/login, verify profile/holdings, change brokerage cash, submit an order, and refresh order history. An accepted order can stay pending until the matching engine finds a counterparty.

## Fresh accounts and older frontend processes

Registration saves the user and a zero-balance USD investment account in one transaction. Fresh accounts have no holdings or orders. When an older user has no account, the client calls `/accounts/mine/initialize`; the server locks that user and creates an empty account only if needed. Errors remain visible and can be retried with Refresh accounts. Account cards show a dash until real account data loads. Funding amount and order quantity fields start blank.

The default URL opens `/#/login`. Workspace routes require a verified session. If the UI still shows sample balances or an Explore demo option, stop the old `npm start` process with Ctrl+C, pull `feature/client-backend-integration`, run `npm ci`, restart Angular, and hard-refresh the browser (Ctrl+Shift+R). Rebuild the Java container too, so the initialization endpoint is present. Do not reset database volumes or insert sample data to repair onboarding.
