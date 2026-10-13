# JARBiS Angular 22 client

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
| `src/app/app.config.ts` | HTTP/interceptor and router providers |
| `src/app/app.routes.ts` | Lazy standalone route definitions and unknown-route fallback |
| `src/app/pages/` | Routed overview, login, trade, charts, watchlist, screener, news, history, and profile pages |
| `src/app/workspace/` | Shell/navigation and signal-backed account/demo state shared across routes |
| `src/app/auth/` | In-memory session and scoped Bearer interceptor |
| `src/app/brokerage/` | Typed API transport and backend DTOs |
| `src/app/portfolio/` | Reusable balance and holdings components |
| `src/app/market/` | Market components, sample quotes, filters, chart selection, and browser-local watchlist |
| `src/app/orders/` | History component with local status filter |
| `src/app/profile/` | Profile editing and account connection forms |
| `src/app/trade/` | Focused order ticket, review, validation, receipt, and submission |

Components use `OnPush`; shared writable state uses signals behind getters/setters to retain template-driven form bindings. Login owns its transient form fields, and its HTTP subscription ends when the page is destroyed. Trade subscriptions also end on destruction and explicitly notify Angular on asynchronous results. No manual hashchange listener or Zone.js dependency remains. Templates and feature styles are colocated; global styles contain the existing visual theme.

## Behavior and API integration

Demo mode uses illustrative quotes, charts, balances and orders. Confirming a demo order creates a local PENDING order without HTTP requests or settlement. Sign in/register, open Profile, and load an existing account ID to use backend data. The authenticated user ID comes from the login response. Trade can use holdings or a manually entered existing asset ID; the backend has no asset search/list endpoint yet.

Orders POST exactly `{accountId, assetId, side, quantity, price}` to `/api/orders`. The UI estimates cash/shares after pending reservations, freezes review details, blocks duplicate clicks, and treats network/5xx failures as ambiguous without automatic retry. Refresh history or reload the account to see settlement results. Tokens remain in memory and are cleared on sign-out/refresh. Authorization and final balance checks remain backend responsibilities.

News opens publisher sources; it is not an in-app article feed. Market data is illustrative. Watchlists are browser-local. Trade uses account currency; the inherited overview/profile formatting is USD. Live quotes, account discovery/creation, and persisted watchlists require further integration.

## Validation

```sh
npm run format:check
npm run build
npx playwright install chromium
npm run test:trade
```

`CHROMIUM_PATH=/path/to/chromium npm run test:trade` can use an existing browser. Browser tests serve the production build with mocked API responses and never submit real trades. They cover demo buy/sell, validation, review locks, history, lazy routes, browser back/reload, watchlist persistence, authentication headers, exact order payloads, duplicate submission, ambiguous failures, sign-out, and mobile overflow. Screenshots are written to ignored `test-results/`. Live backend integration is not exercised by these tests.
