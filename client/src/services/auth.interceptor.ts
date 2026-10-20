import { inject } from "@angular/core";
import { HttpInterceptorFn } from "@angular/common/http";
import { SessionService } from "./session.service";
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(SessionService).token;
  return next(
    token && request.url.startsWith("/api/")
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request,
  );
};
