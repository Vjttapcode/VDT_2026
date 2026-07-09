import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DocumentStore } from '../../core/document-store.service';
import { AuthService } from '../../core/auth.service';
import { OrgService } from '../../core/org.service';
import { DocLevel, DocType, fmtIso, LEVEL_VN, TYPE_VN } from '../../core/models';

const MAX_FILE_MB = 10;

@Component({
  selector: 'app-doc-form',
  imports: [FormsModule, RouterLink],
  templateUrl: './doc-form.html',
  styleUrl: './doc-form.scss'
})
export class DocFormPage {
  readonly store = inject(DocumentStore);
  readonly auth = inject(AuthService);
  readonly org = inject(OrgService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private sanitizer = inject(DomSanitizer);
  private titleService = inject(Title);

  /** id văn bản đang sửa; null = đang tạo văn bản mới */
  editId: number | null = null;

  title = '';
  description = '';
  type: DocType = 'CONTRACT';
  level: DocLevel = 'CENTER';
  expiryDate = fmtIso(new Date(Date.now() + 90 * 86400000));
  effectiveDate = '';   // để trống = hiệu lực ngay khi được duyệt
  submitNow = true;

  /** Đơn vị đích khi người tạo có phạm vi rộng hơn cấp đã chọn (Admin/Trưởng Công ty). */
  targetCompanyId: number | null = null;
  targetDeptId: number | null = null;

  /** Đường dẫn tệp hiện tại của văn bản đang sửa (để hiển thị + cho thay tệp khác). */
  existingFilePath: string | null = null;

  readonly minDate = fmtIso(new Date(Date.now() + 86400000)); // backend validate @Future
  readonly saving = signal(false);
  readonly loadingDoc = signal(false);

  readonly file = signal<File | null>(null);
  /** object URL đã sanitize cho iframe preview PDF */
  readonly previewUrl = signal<SafeResourceUrl | null>(null);
  private objectUrl: string | null = null;

  readonly typeOptions = (Object.keys(TYPE_VN) as DocType[]).map(k => ({ value: k, label: TYPE_VN[k] }));

  readonly isPdf = computed(() => this.file()?.name.toLowerCase().endsWith('.pdf') ?? false);

  /* ===== vai trò & phạm vi người tạo ===== */
  readonly role = computed(() => this.auth.user()?.role ?? 'USER');
  readonly ownCompanyId = computed(() => this.auth.user()?.companyId ?? null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isCompanyManager = computed(() => this.role() === 'MANAGER_COMPANY');
  /** Nhân viên / Trưởng Trung tâm: thuộc đúng một trung tâm → khóa ở cấp Trung tâm. */
  readonly isCenterRole = computed(() => this.role() === 'USER' || this.role() === 'MANAGER_CENTER');

  /** Cấp áp dụng được chọn theo vai trò. */
  readonly levelOptions = computed<{ value: DocLevel; label: string }[]>(() => {
    const all = (Object.keys(LEVEL_VN) as DocLevel[]).map(k => ({ value: k, label: LEVEL_VN[k] }));
    if (this.isAdmin()) return all;                                   // Trung tâm / Công ty / Tập đoàn
    if (this.isCompanyManager()) return all.filter(o => o.value !== 'GROUP'); // Trung tâm / Công ty
    return all.filter(o => o.value === 'CENTER');                     // chỉ Trung tâm
  });

  /** Tên trung tâm của chính người tạo (nếu tải được danh mục). */
  readonly ownDeptName = computed(() => {
    const id = this.auth.user()?.departmentId;
    if (id == null) return null;
    return this.org.departments().find(d => d.id === id)?.name ?? null;
  });

  /**
   * Danh sách trung tâm để chọn (Admin: theo công ty đã chọn; Trưởng Cty: trong công ty mình).
   * Là method (không phải computed) vì phụ thuộc targetCompanyId — một field thường, không signal.
   */
  targetDepts() {
    if (this.isAdmin()) return this.org.deptsForCompany(this.targetCompanyId);
    if (this.isCompanyManager()) return this.org.deptsForCompany(this.ownCompanyId());
    return [];
  }

  /* ===== khi nào cần dropdown chọn đích ===== */
  needsDeptPicker(): boolean { return this.level === 'CENTER' && (this.isAdmin() || this.isCompanyManager()); }
  needsCompanyPicker(): boolean { return this.level === 'COMPANY' && this.isAdmin(); }

  constructor() {
    this.org.load();
    // Gán mặc định công ty/trung tâm đích khi danh mục tải xong (chỉ khi chưa chọn).
    effect(() => {
      this.org.companies(); this.org.departments();
      if (this.isAdmin() && this.level !== 'GROUP' && this.targetCompanyId == null)
        this.targetCompanyId = this.org.companies()[0]?.id ?? null;
      if (this.needsDeptPicker() && this.targetDeptId == null)
        this.targetDeptId = this.targetDepts()[0]?.id ?? null;
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editId = +idParam;
      this.titleService.setTitle('Sửa văn bản · VB Quản lý văn bản');
      this.loadForEdit(this.editId);
    } else {
      this.titleService.setTitle('Thêm văn bản mới · VB Quản lý văn bản');
    }
  }

  private async loadForEdit(id: number): Promise<void> {
    this.loadingDoc.set(true);
    const doc = await this.store.getOne(id);
    this.loadingDoc.set(false);
    if (!doc) {
      this.router.navigate(['/documents']);
      return;
    }
    this.title = doc.title;
    this.description = doc.description ?? '';
    this.type = doc.type;
    this.level = doc.level;
    this.expiryDate = doc.expiryDate;
    this.effectiveDate = doc.effectiveDate ?? '';
    this.targetCompanyId = doc.companyId ?? null;
    this.targetDeptId = doc.departmentId ?? null;
    this.existingFilePath = doc.filePath ?? null;
  }

  /* ===== tệp hiện tại (khi sửa) ===== */
  existingFileName(): string {
    const p = this.existingFilePath;
    return p ? (p.split(/[\\/]/).pop() ?? p) : '';
  }
  existingFileUrl(): string {
    return this.existingFilePath ? `/uploads/${encodeURIComponent(this.existingFileName())}` : '';
  }

  /* ===== cấp áp dụng & đơn vị đích ===== */

  onLevelChange(): void {
    if (this.level === 'CENTER') {
      if (this.isAdmin() && this.targetCompanyId == null) this.targetCompanyId = this.org.companies()[0]?.id ?? null;
      this.syncTargetDept();
    } else if (this.level === 'COMPANY') {
      if (this.isAdmin() && this.targetCompanyId == null) this.targetCompanyId = this.org.companies()[0]?.id ?? null;
    }
  }

  onCompanyChange(): void {
    if (this.level === 'CENTER') this.syncTargetDept();
  }

  /** Đảm bảo targetDeptId là một trung tâm thuộc phạm vi đang chọn. */
  private syncTargetDept(): void {
    const list = this.targetDepts();
    if (!list.some(d => d.id === this.targetDeptId)) this.targetDeptId = list[0]?.id ?? null;
  }

  private submitDept(): number | null {
    return this.level === 'CENTER' && this.needsDeptPicker() ? this.targetDeptId : null;
  }
  private submitCompany(): number | null {
    return this.level === 'COMPANY' && this.needsCompanyPicker() ? this.targetCompanyId : null;
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files?.[0];
    input.value = '';
    if (!f) return;
    const name = f.name.toLowerCase();
    if (!name.endsWith('.pdf') && !name.endsWith('.doc') && !name.endsWith('.docx')) {
      this.store.toast('err', 'Chỉ chấp nhận tệp PDF hoặc Word (.doc/.docx)');
      return;
    }
    if (f.size > MAX_FILE_MB * 1024 * 1024) {
      this.store.toast('err', `Tệp vượt quá ${MAX_FILE_MB}MB`);
      return;
    }
    this.setFile(f);
  }

  removeFile(): void {
    this.setFile(null);
  }

  fileSize(f: File): string {
    return f.size < 1024 * 1024 ? `${Math.round(f.size / 1024)} KB` : `${(f.size / 1024 / 1024).toFixed(1)} MB`;
  }

  valid(): boolean {
    const base = !!this.title.trim() && !!this.expiryDate && this.expiryDate >= this.minDate
      && (!this.effectiveDate || this.effectiveDate < this.expiryDate);
    if (!base) return false;
    if (this.needsDeptPicker() && this.targetDeptId == null) return false;
    if (this.needsCompanyPicker() && this.targetCompanyId == null) return false;
    return true;
  }

  async save(): Promise<void> {
    if (!this.valid() || this.saving()) return;
    this.saving.set(true);

    const orgFields = { departmentId: this.submitDept(), companyId: this.submitCompany() };

    if (this.editId != null) {
      const ok = await this.store.update(this.editId, {
        title: this.title.trim(),
        description: this.description.trim(),
        type: this.type,
        level: this.level,
        expiryDate: this.expiryDate,
        effectiveDate: this.effectiveDate || null,
        ...orgFields
      });
      if (!ok) { this.saving.set(false); return; }
      // nếu người dùng chọn tệp mới -> thay tệp đính kèm
      const f = this.file();
      if (f && !(await this.store.replaceFile(this.editId, f))) {
        this.saving.set(false); // lỗi upload đã toast — giữ lại form để thử lại
        return;
      }
      this.saving.set(false);
      this.setFile(null);
      this.router.navigate(['/documents']);
      return;
    }

    const id = await this.store.createFull(
      {
        title: this.title.trim(),
        description: this.description.trim(),
        type: this.type,
        level: this.level,
        expiryDate: this.expiryDate,
        effectiveDate: this.effectiveDate || null,
        ...orgFields
      },
      this.file(),
      this.submitNow
    );
    this.saving.set(false);
    if (id != null) {
      this.setFile(null);
      this.router.navigate(['/documents']); // selectedId đã set -> modal chi tiết tự mở
    }
  }

  private setFile(f: File | null): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
    this.file.set(f);
    if (f && f.name.toLowerCase().endsWith('.pdf')) {
      this.objectUrl = URL.createObjectURL(f);
      this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl));
    } else {
      this.previewUrl.set(null);
    }
  }
}
