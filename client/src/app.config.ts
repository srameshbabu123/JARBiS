import { ApplicationConfig } from "@angular/core";
import { provideHttpClient, withInterceptors } from "@angular/common/http";
import { provideRouter, withHashLocation } from "@angular/router";
import { routes } from "./app.routes";
import { authInterceptor } from "./services/auth.interceptor";
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
