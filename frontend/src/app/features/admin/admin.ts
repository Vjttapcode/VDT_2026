import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService, RegisterRequest, UserDto } from '../../core/admin.service';
import { AlertConfig, NotificationService } from '../../core/notification.service';
import { DocumentStore } from '../../core/document-store.service';
import { AuthService } from '../../core/auth.service';
import { DEPT_VN, LEVEL_VN, Role, ROLE_VN } from '../../core/models';

const EMPTY_FORM: RegisterRequest = {
  email: '', password: '', fullName: '', role: 'USER', departmentId: 1, companyId: null
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

  readonly tab = signal<'users' | 'configs'>('users');
  readonly configs = signal<AlertConfig[]>([]);
  readonly savingConfig = signal<number | null>(null);

  /** hàng đang sửa role: null = không sửa */
  readonly editingId = signal<number | null>(null);
  editRole: Role = 'USER';
  editDeptId: number | null = 1;

  form: RegisterRequest = { ...EMPTY_FORM };
  readonly creating = signal(false);

  readonly roleOptions = (Object.keys(ROLE_VN) as Role[]).map(r => ({ value: r, label: ROLE_VN[r] }));
  readonly roleVn = ROLE_VN;
  readonly levelVn = LEVEL_VN;

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

  ngOnInit(): void {
    this.admin.load();
    this.loadConfigs();
  }

  deptName(id: number | null): string {
    if (id == null) return '-';
    return this.depts().find(d => d.id === id)?.name ?? `Phòng ban #${id}`;
  }

  needsDept(role: Role): boolean {
    return role === 'USER' || role === 'MANAGER_CENTER';
  }

  /* ===== đổi role ===== */

  startEdit(u: UserDto): void {
    this.editingId.set(u.id);
    this.editRole = u.role;
    this.editDeptId = u.departmentId ?? 1;
  }

  confirmEdit(u: UserDto): void {
    const role = this.editRole;
    const payload: UserDto = {
      ...u,
      role,
      departmentId: this.needsDept(role) ? this.editDeptId : null,
      companyId: role === 'MANAGER_COMPANY' ? (u.companyId ?? 1) : null
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

  /* ===== tạo user ===== */

  onFormRoleChange(): void {
    if (this.needsDept(this.form.role)) {
      this.form.departmentId = this.form.departmentId ?? 1;
      this.form.companyId = null;
    } else if (this.form.role === 'MANAGER_COMPANY') {
      this.form.departmentId = null;
      this.form.companyId = 1;
    } else {
      this.form.departmentId = null;
      this.form.companyId = null;
    }
  }

  formValid(): boolean {
    return !!this.form.email.trim() && this.form.password.length >= 6 && !!this.form.fullName.trim()
      && (!this.needsDept(this.form.role) || this.form.departmentId != null);
  }

  submitCreate(): void {
    if (!this.formValid() || this.creating()) return;
    this.creating.set(true);
    this.admin.register({
      ...this.form,
      email: this.form.email.trim(),
      fullName: this.form.fullName.trim()
    }).subscribe({
      next: u => {
        this.creating.set(false);
        this.form = { ...EMPTY_FORM };
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
