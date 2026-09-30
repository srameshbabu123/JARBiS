# JARBiS Angular client

Built against development commit `9c6f8d196be99618591e2d7fe57ba89e62b4ff0c`.

## Run

Node 20.19+ (or supported Node 22/24):

```sh
cd client
npm ci
npm start
```

Open http://localhost:4200. `npm run build` produces `dist/client/browser`.
The dev proxy forwards `/api` to http://localhost:8080 and strips `/api`.
For production configure an equivalent same-origin reverse proxy.

## Pages and behavior

- Overview: cash, invested balance, current holdings and unrealized return.
- Login/signup: validated forms; signup calls POST /users. Login explicitly reports that server authentication is not implemented. No password is persisted.
- Profile: editable name/email and holdings, with development-only account loading by user/account IDs. This is NOT authentication. Do not expose this connection publicly with the current permissive backend.
- Charts: selectable symbols and six time ranges with explicitly illustrative chart data.
- Watchlist: browser-local persistence, add/remove from screener.
- Screener: company/symbol search, sector and positive-movement filters on a sample universe.
- News: current publisher coverage links; an in-app current-article feed is pending a news provider endpoint.
- Order history: account-scoped history with status filtering.

All market quotes and charts are sample data, including while real account data is loaded. Currency formatting currently assumes USD; use USD accounts for this first UI iteration. Real API failures show errors and never silently substitute demo data. The default demo makes no backend writes and places no trades.

## Controller integration

| UI | Existing endpoint |
| --- | --- |
| Signup | POST /users with fullName, email, password |
| Profile | GET/PUT /users/{userId} |
| Account cash | GET /accounts/{accountId} |
| Aggregate cash service | GET /users/{userId}/balance |
| Holdings | GET /holdings/account/{accountId} |
| Order history | GET /orders/account/{accountId} |

The order controller returns Order entities rather than OrderResponseDto; client types reflect nested asset data. New-order submission is not enabled because the controller currently passes a null asset. Additional integration work: authenticated sessions and account discovery, market prices/history, server watchlists, screener data, and news feed. Backend authorization must be implemented before production use; client checks are not security controls.

## Preview

`preview/index.html` is a standalone, bundled Angular preview. Open it in a browser; the demo works without the Spring server. Relative API requests require serving with the development proxy, so use npm start for backend integration. Screenshots are included for desktop, mobile and login.

## Verification

Production Angular compilation with strict templates. Browser smoke coverage: all navigation pages, symbol search, watchlist persistence, order-status filtering, login preview, mobile document width, and browser exceptions.
