import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Chặn route theo role — khai báo qua route data: { roles: ['ADMIN', ...] } */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const allowed = route.data['roles'] as string[] | undefined;
  const role = auth.user()?.role;
  return !allowed || (role != null && allowed.includes(role))
    ? true
    : router.createUrlTree(['/dashboard']);
};
