import { Routes } from "@angular/router";
import { authenticatedGuard } from "./services/auth.guard";
export const routes: Routes = [
  {
    path: "login",
    loadComponent: () =>
      import("./pages/login/login-page").then((m) => m.LoginPageComponent),
  },
  { path: "", pathMatch: "full", redirectTo: "login" },
  {
    path: "",
    canActivateChild: [authenticatedGuard],
    loadComponent: () =>
      import("./components/layout/workspace-shell/workspace-shell").then(
        (m) => m.WorkspaceShellComponent,
      ),
    children: [
      {
        path: "overview",
        loadComponent: () =>
          import("./pages/overview/overview-page").then(
            (m) => m.OverviewPageComponent,
          ),
      },
      {
        path: "charts",
        loadComponent: () =>
          import("./pages/charts/charts-page").then(
            (m) => m.ChartsPageComponent,
          ),
      },
      {
        path: "watchlist",
        loadComponent: () =>
          import("./pages/watchlist/watchlist-page").then(
            (m) => m.WatchlistPageComponent,
          ),
      },
      {
        path: "screener",
        loadComponent: () =>
          import("./pages/screener/screener-page").then(
            (m) => m.ScreenerPageComponent,
          ),
      },
      {
        path: "news",
        loadComponent: () =>
          import("./pages/news/news-page").then((m) => m.NewsPageComponent),
      },
      {
        path: "order-history",
        loadComponent: () =>
          import("./pages/order-history/order-history-page").then(
            (m) => m.OrderHistoryPageComponent,
          ),
      },
      {
        path: "profile",
        loadComponent: () =>
          import("./pages/profile/profile-page").then(
            (m) => m.ProfilePageComponent,
          ),
      },
      {
        path: "trade",
        loadComponent: () =>
          import("./pages/trade/trade-page").then((m) => m.TradePageComponent),
      },
    ],
  },
  { path: "**", redirectTo: "login" },
];
