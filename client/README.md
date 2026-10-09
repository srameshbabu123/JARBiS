# JARBiS Angular client

Angular frontend on `feature/angular-client-ui`. The trading/auth integration targets the controllers in development commit `f068d2c` (inspected 2026-10-09). Run the current development backend separately: the frontend branch's original backend predates auth and the asset lookup fix. No backend files were changed by the trading UI update.

## Run

```sh
cd client
npm ci
npm start
```

Open http://localhost:4200/#trade. Node 20.19+ or supported Node 22/24 is required. `npm run build` produces `dist/client/browser`. The dev proxy forwards `/api` to http://localhost:8080 and strips `/api`; production needs an equivalent same-origin reverse proxy.

## Buy/sell page

- `src/trade.ts`: standalone component, available cash/share estimates, validation, frozen order review, API submission and error handling.
- `src/trade.html`: Buy/Sell selector, assets from holdings, quantity, price, estimate, review, receipt and account summary.
- `src/trade.css`: responsive black/orange/pink/purple styling.
- `src/api.ts`: typed HTTP requests and in-memory Bearer session.
- `src/main.ts` / `src/app.html`: navigation, login, account loading and order-history integration.

Start in demo mode. Reviewing and confirming creates a local PENDING order visible in order history, without HTTP requests, cash changes or settlement. Demo orders are lost on refresh. Estimates subtract PENDING and PARTIALLY_FILLED orders using remainingQuantity when supplied.

To submit to your running development API:
1. Sign in (or register) through Login.
2. On Profile, enter an existing account ID and choose Load account. The user ID comes from the sign-in response.
3. Open Trade. Select an asset from loaded holdings. For a new BUY position, enter an existing database asset ID and its price manually; no asset search/list controller is available.
4. Enter quantity and maximum buy/minimum sell price, review the account/asset/side/total, then explicitly submit.
5. Use Refresh status on Order history; reload the account on Profile to update holdings and cash after settlement.

Submission uses exactly `{accountId, assetId, side, quantity, price}` at POST /orders. It does not call execute/complete or manufacture a completed fill. Actual matching and settlement belong to the backend. The client blocks duplicate clicks during submission. A network/5xx response is treated as ambiguous: no automatic retry; inspect order history before placing another order.

Authentication uses POST /auth/login and /auth/register, with `{user, token, tokenType}` responses. Tokens are held in memory only and cleared on sign-out/refresh. Authenticated requests attach Authorization: Bearer. Backend authorization and final balance/holding checks remain essential; UI checks are not authorization controls.

## Other pages

- Overview/Profile: balances, holdings and unrealized returns; connected profile updates use PUT /users/{userId}.
- Charts: explicitly illustrative history and sample quotes; not live market data.
- Watchlist: browser-local symbols.
- Screener: sample universe with search, sector and gainers filters.
- News: current publisher links, not an in-app live article feed.
- Order history: account orders and status filtering, including PARTIALLY_FILLED.

Trade uses the loaded account currency. The original overview/profile cards still format in USD. Market reference values may be stale; holdings-derived prices are not live quotes. Account creation, account discovery, live market/news data and persisted watchlists need further integration.

## Preview and tests

`preview/trade.html` opens the self-contained Angular demo on the Trade page. `preview/index.html` opens Overview. Screenshots include Trade desktop and mobile. API integration requires serving via the proxy, not opening a local HTML file.

```sh
npx playwright install chromium
npm run test:trade
```

Optional `CHROMIUM_PATH=/path/to/chromium npm run test:trade` uses an existing browser. Tests launch the production build with mocked API responses; they never submit real trades. Covered: BUY/SELL, quantity/cash/holdings validation, fractional units, review lock, demo/history state, auth header and exact order payload, duplicate-click handling, ambiguous errors, sign-out and mobile overflow. Live backend end-to-end behavior was not tested.
