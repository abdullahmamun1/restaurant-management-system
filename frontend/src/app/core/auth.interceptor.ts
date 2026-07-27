import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

/**
 * Attaches the JWT bearer token to every API request and reacts to auth failures. On a 401
 * from a protected endpoint (e.g. expired token), it clears the session and bounces to login.
 * Server-side RBAC remains authoritative — this is convenience, not enforcement.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token;

  const authorized = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorized).pipe(
    catchError((err: HttpErrorResponse) => {
      const isLogin = req.url.endsWith('/auth/login');
      if (err.status === 401 && !isLogin) {
        auth.logout();
      }
      return throwError(() => err);
    })
  );
};
