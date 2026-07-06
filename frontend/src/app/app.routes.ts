import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { roleGuard } from './core/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then(m => m.LoginPage)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell').then(m => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then(m => m.DashboardPage)
      },
      {
        path: 'documents/new',
        loadComponent: () => import('./features/doc-form/doc-form').then(m => m.DocFormPage)
      },
      {
        path: 'documents/:id/edit',
        loadComponent: () => import('./features/doc-form/doc-form').then(m => m.DocFormPage)
      },
      {
        path: 'documents',
        loadComponent: () => import('./features/documents/documents').then(m => m.DocumentsPage)
      },
      {
        path: 'alerts',
        loadComponent: () => import('./features/alerts/alerts').then(m => m.AlertsPage)
      },
      {
        path: 'calendar',
        loadComponent: () => import('./features/calendar/calendar').then(m => m.CalendarPage)
      },
      {
        path: 'reports',
        loadComponent: () => import('./features/alert-log/alert-log').then(m => m.AlertLogPage)
      },
      {
        path: 'profile',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/admin/admin').then(m => m.AdminPage)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
