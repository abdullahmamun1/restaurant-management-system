import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

/** Blocks a route unless a user is authenticated; otherwise redirects to /login. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url }
  });
};

/**
 * Restricts a route to the given roles (foundation for role-specific screens in M2+).
 * Authenticated users lacking the role are sent home; anonymous users to login.
 */
export const roleGuard =
  (...allowed: Role[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    const role = auth.role();
    return role && allowed.includes(role) ? true : router.createUrlTree(['/']);
  };
