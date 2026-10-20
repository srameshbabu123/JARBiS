import { inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { CanActivateChildFn, Router } from "@angular/router";
import { catchError, map, of } from "rxjs";
import { SessionService } from "./session.service";
import { User } from "../models/brokerage.models";

/** Verify JWT with the backend before allowing access to workspace pages. */
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
      session.updateUser(user);
      return true;
    }),
    catchError(() => of(router.createUrlTree(["/login"]))),
  );
};
