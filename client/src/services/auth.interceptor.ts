import { inject } from "@angular/core";
import { HttpErrorResponse, HttpInterceptorFn } from "@angular/common/http";
import { Router } from "@angular/router";
import { catchError, throwError } from "rxjs";
import { SessionService } from "./session.service";

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(SessionService);
  const router = inject(Router);
  const isBackendRequest = request.url.startsWith("/api/");
  const publicAuthRequest =
    request.url === "/api/auth/login" || request.url === "/api/auth/register";
  const token = !publicAuthRequest && isBackendRequest ? session.token : "";
  const outbound = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(outbound).pipe(
    catchError((error: HttpErrorResponse) => {
      if (isBackendRequest && !publicAuthRequest && error.status === 401) {
        session.signOut();
        void router.navigateByUrl("/login");
      }
      return throwError(() => error);
    }),
  );
};
