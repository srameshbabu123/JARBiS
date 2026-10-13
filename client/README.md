# Velocity Angular 22 client

The client is based on `development` (backend commit `f068d2c`), with the existing UI imported from `feature/angular-client-ui`. Angular framework, router, CLI, and build tooling are pinned to **22.2.2**. TypeScript is 6.0, and the app uses Angular's default zoneless rendering.

## Run

Use Node 24.15+ (or another version accepted by `package.json` engines).

```sh
cd client
npm ci
npm start
```

Open http://localhost:4200/#/overview or http://localhost:4200/#/trade. The existing hash URLs also resolve. The dev proxy forwards `/api` to http://localhost:8080 and strips `/api`. Production needs an equivalent same-origin reverse proxy. `npm run build` writes `dist/client/browser`.

## Architecture

| Location | Responsibility |
| --- | --- |
| `src/main.ts` | Bootstrap only |
| `src/app.config.ts` | HTTP/interceptor and router providers |
| `src/app.routes.ts` | Lazy standalone route definitions and unknown-route fallback |
| `src/pages/` | Routed overview, login, trade, charts, watchlist, screener, news, history, and profile pages |
| `src/components/` | Reusable portfolio, market, order, profile, trade, shell and branding components, with colocated templates/styles |
| `src/services/` | Signal-backed workspace/market state, session, API transport and Bearer interceptor |
| `src/models/` | Backend DTOs |
| `src/data/` | Illustrative market data |
| `src/styles/` | Shared theme tokens, base typography/controls, and consistent page/layout classes |
| `src/assets/branding/` | Existing Velocity dark/light logos and icons copied from the repository |

Components use `OnPush`; shared writable state uses signals behind getters/setters to retain template-driven form bindings. Login owns its transient form fields, and its HTTP subscription ends when the page is destroyed. Trade subscriptions also end on destruction and explicitly notify Angular on asynchronous results. No manual hashchange listener or Zone.js dependency remains. Templates and feature styles are colocated; global styles load one shared theme for all pages. The shared brand component renders the existing Velocity logo in the sidebar, login and footer; the supplied icon is the favicon.

## Behavior and API integration

Demo mode uses illustrative quotes, charts, balances and orders. Confirming a demo order creates a local PENDING order without HTTP requests or settlement. Sign in/register, open Profile to discover your brokerage accounts from your signed-in session. A single account loads automatically; multiple accounts use a selector. No user/account ID entry is required. Trade can use holdings or a manually entered existing asset ID; the backend has no asset search/list endpoint yet.

Orders POST exactly `{accountId, assetId, side, quantity, price}` to `/api/orders`. The UI estimates cash/shares after pending reservations, freezes review details, blocks duplicate clicks, and treats network/5xx failures as ambiguous without automatic retry. Refresh history or reload the account to see settlement results. Tokens remain in memory and are cleared on sign-out/refresh. Authorization and final balance checks remain backend responsibilities.

News opens publisher sources; it is not an in-app article feed. Market data is illustrative. Watchlists are browser-local. Cash, holdings, order history, and portfolio summaries use the selected account currency. Illustrative market quotes remain USD. Live quotes, account creation, and persisted watchlists require further integration.

## Deposits and withdrawals

Manage cash on Overview or Profile. Enter an amount, review it, and explicitly confirm. Demo mode changes demo cash locally. Connected mode uses POST `/api/accounts/{accountId}/deposit` or `/withdraw` with `{amount}` and the in-memory Bearer session. The server identifies the owner from the JWT principal and uses a locked account lookup. Amounts must be positive, finite, and have at most two decimal places. Withdrawals exclude cash reserved for open BUY orders. Success updates the selected account balance; no automatic retry occurs after a network/5xx response. Use Refresh cash balance to check an uncertain result before a new request.

These operations update brokerage cash balances only. No bank transfer, external payment processing, reconciliation or transfer ledger is connected. The UI states this explicitly. Account discovery uses GET `/api/accounts/mine`; account loading uses GET `/api/accounts/mine/{accountId}`, both scoped to the authenticated principal.

## Validation

```sh
npm run format:check
npm run build
npx playwright install chromium
npm run test:ui
```

`CHROMIUM_PATH=/path/to/chromium npm run test:ui` can use an existing browser. Browser tests serve the production build with mocked API responses and never submit real trades. They cover demo buy/sell, validation, review locks, history, lazy routes, browser back/reload, watchlist persistence, authentication headers, exact order payloads, duplicate submission, ambiguous failures, sign-out, and mobile overflow. The cash suite also checks demo/live deposits and withdrawals, single/multiple account discovery, currency, amount validation, duplicate prevention and ambiguous failures. Screenshots are written to ignored `test-results/`. Live backend integration is not exercised by these tests.
