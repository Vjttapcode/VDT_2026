import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import type { CompanyDto, DepartmentDto } from './admin.service';

/**
 * Nạp danh mục tổ chức (công ty / trung tâm) cho các màn ngoài trang Admin — hiện dùng ở
 * form tạo/sửa văn bản để chọn đơn vị đích. Chỉ gọi endpoint mà vai trò hiện tại có quyền:
 *   - GET /departments: ADMIN, MANAGER_COMPANY, MANAGER_CENTER
 *   - GET /companies  : chỉ ADMIN
 */
@Injectable({ providedIn: 'root' })
export class OrgService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);

  readonly companies = signal<CompanyDto[]>([]);
  readonly departments = signal<DepartmentDto[]>([]);

  load(): void {
    const role = this.auth.user()?.role;
    if (role === 'ADMIN' || role === 'MANAGER_COMPANY' || role === 'MANAGER_CENTER') {
      this.http.get<DepartmentDto[]>('/api/auth/departments').subscribe({
        next: d => this.departments.set(d),
        error: () => {}
      });
    }
    if (role === 'ADMIN') {
      this.http.get<CompanyDto[]>('/api/auth/companies').subscribe({
        next: c => this.companies.set(c),
        error: () => {}
      });
    }
  }

  deptsForCompany(companyId: number | null): DepartmentDto[] {
    const all = this.departments();
    return companyId == null ? all : all.filter(d => d.companyId === companyId);
  }
}
