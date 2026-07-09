import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminAnalytics, AdminService, CompanyDto, RegisterRequest, UserDto } from '../../core/admin.service';
import { DepartmentDto } from '../../core/admin.service';
import { AlertConfig, NotificationService } from '../../core/notification.service';
import { DocumentStore } from '../../core/document-store.service';
import { AuthService } from '../../core/auth.service';
import { DEPT_VN, LEVEL_VN, Role, ROLE_THEME, ROLE_VN } from '../../core/models';

const EMPTY_FORM: RegisterRequest = {
  email: '', password: '', fullName: '', role: 'USER', departmentId: null, companyId: null
};

@Component({
  selector: 'app-admin',
  imports: [FormsModule],
  templateUrl: './admin.html',
  styleUrl: './admin.scss'
})
export class AdminPage implements OnInit {
  readonly admin = inject(AdminService);
  readonly noti = inject(NotificationService);
  readonly store = inject(DocumentStore);
  readonly auth = inject(AuthService);

  readonly tab = signal<'users' | 'configs' | 'org' | 'analytics'>('users');
  readonly configs = signal<AlertConfig[]>([]);
  readonly savingConfig = signal<number | null>(null);
  readonly running = signal(false);

  /* ===== Phân tích ===== */
  readonly analytics = signal<AdminAnalytics | null>(null);
  readonly alertStats = signal<{ total: number; sent: number; failed: number; warning: number; expired: number } | null>(null);
  readonly monthLabels = ['T1', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'T8', 'T9', 'T10', 'T11', 'T12'];

  readonly maxMonthly = computed(() => Math.max(1, ...(this.analytics()?.monthlyExpiry ?? [0])));

  loadAnalytics(): void {
    this.admin.analytics().subscribe({
      next: a => this.analytics.set(a),
      error: () => this.store.toast('err', 'Không tải được số liệu phân tích')
    });
    this.noti.stats().subscribe({
      next: s => this.alertStats.set(s),
      error: () => {}
    });
  }

  deptNameById(id: number): string {
    return this.admin.departments().find(d => d.id === id)?.name
      ?? this.depts().find(d => d.id === id)?.name ?? `Phòng ban #${id}`;
  }

  /* ===== Tổ chức (công ty + trung tâm) ===== */
  newCompany = { name: '', code: '' };
  /** modal thêm trung tâm: id công ty đang thêm (null = đóng) */
  readonly deptModalCompanyId = signal<number | null>(null);
  modalDept = { name: '', code: '' };
  readonly editingCompanyId = signal<number | null>(null);
  editCompany = { name: '', code: '' };
  readonly editingDeptId = signal<number | null>(null);
  editDept = { name: '', code: '', companyId: null as number | null };
  readonly confirmCompanyId = signal<number | null>(null);
  readonly confirmDeptId = signal<number | null>(null);

  deptsOf(companyId: number): DepartmentDto[] {
    return this.admin.departments().filter(d => d.companyId === companyId);
  }

  /** hàng đang sửa role: null = không sửa */
  readonly editingId = signal<number | null>(null);
  editRole: Role = 'USER';
  editDeptId: number | null = null;
  editCompanyId: number | null = null;

  /** hàng đang xác nhận xóa tài khoản: null = không xác nhận */
  readonly confirmUserId = signal<number | null>(null);

  form: RegisterRequest = { ...EMPTY_FORM };
  readonly creating = signal(false);

  readonly roleOptions = (Object.keys(ROLE_VN) as Role[]).map(r => ({ value: r, label: ROLE_VN[r] }));
  readonly roleVn = ROLE_VN;
  readonly roleTheme = ROLE_THEME;
  readonly levelVn = LEVEL_VN;

  /* ===== Bộ lọc + tìm kiếm danh sách user ===== */
  readonly search = signal('');
  readonly filterCompanyId = signal<number | null>(null);
  readonly filterDeptId = signal<number | null>(null);
  readonly filterRole = signal<Role | ''>('');
  readonly filterActive = signal<'' | 'active' | 'locked'>('');

  /** trung tâm hiển thị trong dropdown lọc, cascade theo công ty đã chọn */
  readonly filterDepts = computed(() => this.deptsForCompany(this.filterCompanyId()));

  readonly filteredUsers = computed(() => {
    const q = this.search().trim().toLowerCase();
    const fc = this.filterCompanyId();
    const fd = this.filterDeptId();
    const fr = this.filterRole();
    const fa = this.filterActive();
    return this.admin.users().filter(u => {
      if (q && !u.fullName.toLowerCase().includes(q) && !u.email.toLowerCase().includes(q)) return false;
      if (fc != null && this.companyIdOf(u) !== fc) return false;
      if (fd != null && u.departmentId !== fd) return false;
      if (fr && u.role !== fr) return false;
      if (fa === 'active' && u.isActive === false) return false;
      if (fa === 'locked' && u.isActive !== false) return false;
      return true;
    });
  });

  readonly hasFilter = computed(() =>
    !!this.search().trim() || this.filterCompanyId() != null || this.filterDeptId() != null
    || !!this.filterRole() || !!this.filterActive());

  clearFilters(): void {
    this.search.set('');
    this.filterCompanyId.set(null);
    this.filterDeptId.set(null);
    this.filterRole.set('');
    this.filterActive.set('');
  }

  onFilterCompanyChange(): void {
    // đổi công ty lọc -> bỏ chọn trung tâm nếu không còn thuộc công ty đó
    const fd = this.filterDeptId();
    if (fd != null && !this.filterDepts().some(d => d.id === fd)) this.filterDeptId.set(null);
  }

  readonly depts = computed(() => {
    const loaded = this.admin.departments();
    if (loaded.length) return loaded.map(d => ({ id: d.id, name: d.name }));
    return Object.entries(DEPT_VN).map(([id, name]) => ({ id: +id, name }));
  });

  readonly levelHints: Record<string, string> = {
    CENTER: 'Văn bản nội bộ Trung tâm',
    COMPANY: 'Văn bản cấp Công ty',
    GROUP: 'Văn bản cấp Tập đoàn'
  };

  constructor() {
    // Khi danh sách công ty tải xong, gán mặc định cho form tạo (nếu chưa chọn).
    effect(() => {
      const cos = this.admin.companies();
      if (cos.length && this.form.role !== 'ADMIN' && this.form.companyId == null) {
        this.form.companyId = cos[0].id;
        this.syncFormDept();
      }
    });
  }

  ngOnInit(): void {
    this.admin.load();
    this.loadConfigs();
    this.loadAnalytics();
  }

  /* ===== Helper tổ chức dùng chung ===== */

  /** Công ty hiệu lực của user: MANAGER_COMPANY dùng companyId; center-role suy từ trung tâm; ADMIN = null. */
  companyIdOf(u: UserDto): number | null {
    if (u.role === 'MANAGER_COMPANY') return u.companyId;
    if (u.departmentId != null) return this.admin.departments().find(d => d.id === u.departmentId)?.companyId ?? null;
    return null;
  }

  companyNameOf(u: UserDto): string {
    const id = this.companyIdOf(u);
    return id == null ? '—' : this.admin.companies().find(c => c.id === id)?.name ?? `Công ty #${id}`;
  }

  /** Trung tâm hiển thị: center-role -> tên; MANAGER_COMPANY -> "Toàn công ty"; ADMIN -> "—". */
  deptCellOf(u: UserDto): string {
    if (u.role === 'MANAGER_COMPANY') return 'Toàn công ty';
    if (u.role === 'ADMIN') return '—';
    return this.deptName(u.departmentId);
  }

  deptsForCompany(companyId: number | null): DepartmentDto[] {
    const all = this.admin.departments();
    return companyId == null ? all : all.filter(d => d.companyId === companyId);
  }

  deptName(id: number | null): string {
    if (id == null) return '—';
    return this.depts().find(d => d.id === id)?.name ?? `Phòng ban #${id}`;
  }

  needsDept(role: Role): boolean {
    return role === 'USER' || role === 'MANAGER_CENTER';
  }

  /* ===== đổi role (inline) ===== */

  startEdit(u: UserDto): void {
    this.editingId.set(u.id);
    this.editRole = u.role;
    this.editCompanyId = this.companyIdOf(u) ?? this.admin.companies()[0]?.id ?? null;
    this.editDeptId = u.departmentId ?? this.deptsForCompany(this.editCompanyId)[0]?.id ?? null;
  }

  onEditRoleChange(): void {
    if (this.needsDept(this.editRole)) {
      this.editCompanyId = this.editCompanyId ?? this.admin.companies()[0]?.id ?? null;
      this.syncEditDept();
    } else if (this.editRole === 'MANAGER_COMPANY') {
      this.editCompanyId = this.editCompanyId ?? this.admin.companies()[0]?.id ?? null;
    }
  }

  onEditCompanyChange(): void {
    if (this.needsDept(this.editRole)) this.syncEditDept();
  }

  /** Đảm bảo editDeptId là một trung tâm thuộc editCompanyId. */
  private syncEditDept(): void {
    const list = this.deptsForCompany(this.editCompanyId);
    if (!list.some(d => d.id === this.editDeptId)) this.editDeptId = list[0]?.id ?? null;
  }

  confirmEdit(u: UserDto): void {
    const role = this.editRole;
    const payload: UserDto = {
      ...u,
      role,
      departmentId: this.needsDept(role) ? this.editDeptId : null,
      companyId: role === 'MANAGER_COMPANY' ? this.editCompanyId : null
    };
    this.admin.update(payload).subscribe({
      next: () => {
        this.editingId.set(null);
        this.admin.load();
        this.store.toast('ok', `Đã đổi vai trò của ${u.fullName}`);
      },
      error: err => this.store.toast('err', err.error?.message ?? 'Đổi vai trò thất bại')
    });
  }

  toggleActive(u: UserDto): void {
    const target = !(u.isActive ?? true);
    this.admin.update({ ...u, isActive: target }).subscribe({
      next: () => {
        this.admin.load();
        this.store.toast('ok', target ? `Đã mở khóa ${u.fullName}` : `Đã khóa ${u.fullName}`);
      },
      error: err => this.store.toast('err', err.error?.message ?? 'Cập nhật thất bại')
    });
  }

  isSelf(u: UserDto): boolean {
    return u.id === this.auth.user()?.userId;
  }

  /** Xóa vĩnh viễn tài khoản (backend chặn tự xóa). */
  removeUser(u: UserDto): void {
    this.admin.deleteUser(u.id).subscribe({
      next: () => {
        this.confirmUserId.set(null);
        this.admin.load();
        this.store.toast('ok', `Đã xóa tài khoản ${u.email}`);
      },
      error: err => {
        this.confirmUserId.set(null);
        this.store.toast('err', err.error?.message ?? 'Xóa tài khoản thất bại');
      }
    });
  }

  /* ===== tạo user ===== */

  onFormRoleChange(): void {
    if (this.needsDept(this.form.role)) {
      this.form.companyId = this.form.companyId ?? this.admin.companies()[0]?.id ?? null;
      this.syncFormDept();
    } else if (this.form.role === 'MANAGER_COMPANY') {
      this.form.companyId = this.form.companyId ?? this.admin.companies()[0]?.id ?? null;
      this.form.departmentId = null;
    } else {
      this.form.departmentId = null;
      this.form.companyId = null;
    }
  }

  onFormCompanyChange(): void {
    if (this.needsDept(this.form.role)) this.syncFormDept();
  }

  /** Đảm bảo form.departmentId là một trung tâm thuộc form.companyId. */
  private syncFormDept(): void {
    const list = this.deptsForCompany(this.form.companyId);
    if (!list.some(d => d.id === this.form.departmentId)) this.form.departmentId = list[0]?.id ?? null;
  }

  formValid(): boolean {
    const base = !!this.form.email.trim() && this.form.password.length >= 6 && !!this.form.fullName.trim();
    if (!base) return false;
    if (this.needsDept(this.form.role)) return this.form.departmentId != null;
    if (this.form.role === 'MANAGER_COMPANY') return this.form.companyId != null;
    return true;
  }

  submitCreate(): void {
    if (!this.formValid() || this.creating()) return;
    const role = this.form.role;
    this.creating.set(true);
    this.admin.register({
      ...this.form,
      email: this.form.email.trim(),
      fullName: this.form.fullName.trim(),
      departmentId: this.needsDept(role) ? this.form.departmentId : null,
      companyId: role === 'MANAGER_COMPANY' ? this.form.companyId : null
    }).subscribe({
      next: u => {
        this.creating.set(false);
        this.form = { ...EMPTY_FORM };
        this.onFormRoleChange(); // gán lại công ty/trung tâm mặc định
        this.admin.load();
        this.store.toast('ok', `Đã tạo tài khoản ${u.email}`);
      },
      error: err => {
        this.creating.set(false);
        this.store.toast('err', err.error?.message ?? 'Tạo tài khoản thất bại');
      }
    });
  }

  /* ===== ngưỡng cảnh báo ===== */

  loadConfigs(): void {
    this.noti.configs().subscribe({
      next: c => this.configs.set(c),
      error: () => this.store.toast('err', 'Không tải được cấu hình ngưỡng')
    });
  }

  /* ===== Công ty ===== */
  submitCompany(): void {
    const name = this.newCompany.name.trim(), code = this.newCompany.code.trim();
    if (!name || !code) return;
    this.admin.createCompany({ name, code }).subscribe({
      next: () => { this.newCompany = { name: '', code: '' }; this.admin.load(); this.store.toast('ok', `Đã tạo công ty ${name}`); },
      error: err => this.store.toast('err', err.error?.message ?? 'Tạo công ty thất bại')
    });
  }
  startEditCompany(c: CompanyDto): void { this.editingCompanyId.set(c.id); this.editCompany = { name: c.name, code: c.code }; }
  saveCompany(c: CompanyDto): void {
    this.admin.updateCompany({ id: c.id, name: this.editCompany.name.trim(), code: this.editCompany.code.trim() }).subscribe({
      next: () => { this.editingCompanyId.set(null); this.admin.load(); this.store.toast('ok', 'Đã cập nhật công ty'); },
      error: err => this.store.toast('err', err.error?.message ?? 'Cập nhật công ty thất bại')
    });
  }
  removeCompany(c: CompanyDto): void {
    this.admin.deleteCompany(c.id).subscribe({
      next: () => { this.confirmCompanyId.set(null); this.admin.load(); this.store.toast('ok', `Đã xóa công ty ${c.name}`); },
      error: err => { this.confirmCompanyId.set(null); this.store.toast('err', err.error?.message ?? 'Xóa công ty thất bại'); }
    });
  }

  /* ===== Phân tích ===== */

  /* ===== Trung tâm ===== */
  companyName(id: number | null): string {
    return id == null ? '' : this.admin.companies().find(c => c.id === id)?.name ?? `Công ty #${id}`;
  }
  openDeptModal(companyId: number): void {
    this.modalDept = { name: '', code: '' };
    this.deptModalCompanyId.set(companyId);
  }
  closeDeptModal(): void { this.deptModalCompanyId.set(null); }
  submitDeptModal(): void {
    const companyId = this.deptModalCompanyId();
    const name = this.modalDept.name.trim(), code = this.modalDept.code.trim();
    if (companyId == null || !name || !code) return;
    this.admin.createDepartment({ name, code, companyId }).subscribe({
      next: () => { this.closeDeptModal(); this.admin.load(); this.store.toast('ok', `Đã tạo trung tâm ${name}`); },
      error: err => this.store.toast('err', err.error?.message ?? 'Tạo trung tâm thất bại')
    });
  }
  startEditDept(d: DepartmentDto): void { this.editingDeptId.set(d.id); this.editDept = { name: d.name, code: d.code, companyId: d.companyId }; }
  saveDept(d: DepartmentDto): void {
    this.admin.updateDepartment({ id: d.id, name: this.editDept.name.trim(), code: this.editDept.code.trim(), companyId: this.editDept.companyId! }).subscribe({
      next: () => { this.editingDeptId.set(null); this.admin.load(); this.store.toast('ok', 'Đã cập nhật trung tâm'); },
      error: err => this.store.toast('err', err.error?.message ?? 'Cập nhật trung tâm thất bại')
    });
  }
  removeDept(d: DepartmentDto): void {
    this.admin.deleteDepartment(d.id).subscribe({
      next: () => { this.confirmDeptId.set(null); this.admin.load(); this.store.toast('ok', `Đã xóa trung tâm ${d.name}`); },
      error: err => { this.confirmDeptId.set(null); this.store.toast('err', err.error?.message ?? 'Xóa trung tâm thất bại'); }
    });
  }

  /** Gửi email cảnh báo thử để kiểm tra cấu hình gửi mail. */
  sendTest(): void {
    this.noti.sendTest().subscribe({
      next: r => this.store.toast('ok', `Đã gửi email thử tới ${r.email}`),
      error: err => this.store.toast('err', err.error?.message ?? 'Gửi email thử thất bại')
    });
  }

  /** Admin bấm chạy quét cảnh báo ngay. */
  runCheck(): void {
    if (this.running()) return;
    this.running.set(true);
    this.noti.runCheck().subscribe({
      next: r => {
        this.running.set(false);
        this.store.toast('ok', `Đã quét xong — ${r.enqueued} cảnh báo được đưa vào hàng đợi`);
      },
      error: err => {
        this.running.set(false);
        this.store.toast('err', err.error?.message ?? 'Chạy quét thất bại');
      }
    });
  }

  saveConfig(c: AlertConfig): void {
    if (c.warningDays < 1 || c.warningDays > 30) {
      this.store.toast('err', 'Ngưỡng cảnh báo phải trong 1..30 ngày');
      return;
    }
    if (c.escalateDays < 1 || c.escalateDays >= c.warningDays) {
      this.store.toast('err', 'Ngưỡng leo thang phải nhỏ hơn ngưỡng cảnh báo');
      return;
    }
    this.savingConfig.set(c.id);
    this.noti.updateConfig(c).subscribe({
      next: () => {
        this.savingConfig.set(null);
        this.store.toast('ok', `Đã lưu ngưỡng cấp ${this.levelVn[c.documentLevel]}`);
      },
      error: err => {
        this.savingConfig.set(null);
        this.store.toast('err', err.error?.message ?? 'Lưu ngưỡng thất bại');
      }
    });
  }
}
