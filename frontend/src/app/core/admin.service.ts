import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Role } from './models';

export interface UserDto {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  departmentId: number | null;
  companyId: number | null;
  isActive: boolean | null;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  role: Role;
  departmentId: number | null;
  companyId: number | null;
}

export interface DepartmentDto {
  id: number;
  name: string;
  code: string;
  companyId: number;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);

  readonly users = signal<UserDto[]>([]);
  readonly departments = signal<DepartmentDto[]>([]);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.http.get<UserDto[]>('/api/auth/users').subscribe({
      next: users => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.loadError.set(err.status === 403
          ? 'Chỉ ADMIN mới xem được danh sách người dùng'
          : err.error?.message ?? 'Không tải được danh sách người dùng');
      }
    });
    this.http.get<DepartmentDto[]>('/api/auth/departments').subscribe({
      next: depts => this.departments.set(depts),
      error: () => {} // fallback DEPT_VN tĩnh trong models.ts
    });
  }

  register(req: RegisterRequest): Observable<UserDto> {
    return this.http.post<UserDto>('/api/auth/register', req);
  }

  update(u: UserDto): Observable<UserDto> {
    return this.http.put<UserDto>(`/api/auth/users/${u.id}`, u);
  }
}
