import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DocumentStore } from '../../core/document-store.service';
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

  readonly minDate = fmtIso(new Date(Date.now() + 86400000)); // backend validate @Future
  readonly saving = signal(false);
  readonly loadingDoc = signal(false);

  readonly file = signal<File | null>(null);
  /** object URL đã sanitize cho iframe preview PDF */
  readonly previewUrl = signal<SafeResourceUrl | null>(null);
  private objectUrl: string | null = null;

  readonly typeOptions = (Object.keys(TYPE_VN) as DocType[]).map(k => ({ value: k, label: TYPE_VN[k] }));
  readonly levelOptions = (Object.keys(LEVEL_VN) as DocLevel[]).map(k => ({ value: k, label: LEVEL_VN[k] }));

  readonly isPdf = computed(() => this.file()?.name.toLowerCase().endsWith('.pdf') ?? false);

  constructor() {
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
    return !!this.title.trim() && !!this.expiryDate && this.expiryDate >= this.minDate
      && (!this.effectiveDate || this.effectiveDate < this.expiryDate);
  }

  async save(): Promise<void> {
    if (!this.valid() || this.saving()) return;
    this.saving.set(true);

    if (this.editId != null) {
      const ok = await this.store.update(this.editId, {
        title: this.title.trim(),
        description: this.description.trim(),
        type: this.type,
        level: this.level,
        expiryDate: this.expiryDate,
        effectiveDate: this.effectiveDate || null
      });
      this.saving.set(false);
      if (ok) this.router.navigate(['/documents']);
      return;
    }

    const id = await this.store.createFull(
      {
        title: this.title.trim(),
        description: this.description.trim(),
        type: this.type,
        level: this.level,
        expiryDate: this.expiryDate,
        effectiveDate: this.effectiveDate || null
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
