import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginResponse, ROLE_VN } from './models';

const TOKEN_KEY = 'vb_token';
const USER_KEY = 'vb_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly user = signal<LoginResponse | null>(this.restore());

  constructor() {
    // localStorage dùng chung mọi tab: tab khác đăng nhập/đăng xuất sẽ ghi đè token,
    // trong khi signal user của tab này vẫn là người cũ -> request mang danh tính mới
    // nhưng UI hiện người cũ (vd nhân viên tạo văn bản nhưng audit ghi giám đốc).
    // Sự kiện storage chỉ bắn ở các tab KHÁC tab vừa ghi -> reload để đồng bộ phiên.
    window.addEventListener('storage', e => {
      if (e.key !== TOKEN_KEY && e.key !== USER_KEY && e.key !== null) return;
      if (this.restore()?.userId !== this.user()?.userId) location.reload();
    });
  }

  readonly initials = computed(() => {
    const name = this.user()?.fullName?.trim();
    if (!name) return '?';
    const parts = name.split(/\s+/);
    return (parts.length > 1 ? parts[parts.length - 2][0] + parts[parts.length - 1][0] : name.slice(0, 2)).toUpperCase();
  });

  readonly roleVn = computed(() => {
    const u = this.user();
    return u ? ROLE_VN[u.role] ?? u.role : '';
  });

  readonly isManager = computed(() => {
    const r = this.user()?.role;
    return r === 'ADMIN' || r === 'MANAGER_COMPANY' || r === 'MANAGER_CENTER';
  });

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password }).pipe(
      tap(res => {
        localStorage.setItem(TOKEN_KEY, res.token);
        localStorage.setItem(USER_KEY, JSON.stringify(res));
        this.user.set(res);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.user.set(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }

  private restore(): LoginResponse | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as LoginResponse) : null;
    } catch {
      return null;
    }
  }
}
