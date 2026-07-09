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

export interface CompanyDto {
  id: number;
  name: string;
  code: string;
}

export interface AdminAnalytics {
  total: number;
  byStatus: Record<string, number>;
  byType: Record<string, number>;
  byDepartment: { departmentId: number; total: number; expiringSoon: number; expired: number }[];
  monthlyExpiry: number[];
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);

  readonly users = signal<UserDto[]>([]);
  readonly departments = signal<DepartmentDto[]>([]);
  readonly companies = signal<CompanyDto[]>([]);
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
    this.http.get<CompanyDto[]>('/api/auth/companies').subscribe({
      next: cs => this.companies.set(cs),
      error: () => {}
    });
  }

  register(req: RegisterRequest): Observable<UserDto> {
    return this.http.post<UserDto>('/api/auth/register', req);
  }

  update(u: UserDto): Observable<UserDto> {
    return this.http.put<UserDto>(`/api/auth/users/${u.id}`, u);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`/api/auth/users/${id}`);
  }

  /* ===== Công ty ===== */
  createCompany(c: Omit<CompanyDto, 'id'>): Observable<CompanyDto> {
    return this.http.post<CompanyDto>('/api/auth/companies', c);
  }
  updateCompany(c: CompanyDto): Observable<CompanyDto> {
    return this.http.put<CompanyDto>(`/api/auth/companies/${c.id}`, c);
  }
  deleteCompany(id: number): Observable<void> {
    return this.http.delete<void>(`/api/auth/companies/${id}`);
  }

  /* ===== Phân tích ===== */
  analytics(): Observable<AdminAnalytics> {
    return this.http.get<AdminAnalytics>('/api/documents/admin/analytics');
  }

  /* ===== Trung tâm ===== */
  createDepartment(d: Omit<DepartmentDto, 'id'>): Observable<DepartmentDto> {
    return this.http.post<DepartmentDto>('/api/auth/departments', d);
  }
  updateDepartment(d: DepartmentDto): Observable<DepartmentDto> {
    return this.http.put<DepartmentDto>(`/api/auth/departments/${d.id}`, d);
  }
  deleteDepartment(id: number): Observable<void> {
    return this.http.delete<void>(`/api/auth/departments/${id}`);
  }
}
