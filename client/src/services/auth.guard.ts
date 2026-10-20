import { inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { CanActivateChildFn, Router } from "@angular/router";
import { catchError, map, of } from "rxjs";
import { SessionService } from "./session.service";
import { User } from "../models/brokerage.models";

/** Require a valid server-verified session before any workspace page can load. */
export const authenticatedGuard: CanActivateChildFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const http = inject(HttpClient);
  if (!session.token || !session.currentUser) {
    return router.createUrlTree(["/login"]);
  }
  return http.get<User>("/api/auth/me").pipe(
    map((user) => {
      if (user.id !== session.currentUser?.id) {
        session.signOut();
        return router.createUrlTree(["/login"]);
      }
      return true;
    }),
    catchError(() => {
      session.signOut();
      return of(router.createUrlTree(["/login"]));
    }),
  );
};
