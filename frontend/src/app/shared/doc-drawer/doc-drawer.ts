import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { AuthService } from '../../core/auth.service';
import { DocumentStore } from '../../core/document-store.service';
import {
  AUDIT_ACTION_COLOR, AUDIT_ACTION_VN, AuditAction, DocRelation, DocStatus, FIELD_VN,
  REL_DIRECTION_VN, REL_TYPE_COLOR, REL_TYPE_VN, RelationType, fmtIso, toDate
} from '../../core/models';

/** Modal chi tiết văn bản (giữa màn hình), kèm xem trước PDF và gia hạn theo calendar. */
@Component({
  selector: 'app-doc-drawer',
  imports: [FormsModule],
  templateUrl: './doc-drawer.html',
  styleUrl: './doc-drawer.scss'
})
export class DocDrawer {
  readonly store = inject(DocumentStore);
  readonly auth = inject(AuthService);
  private sanitizer = inject(DomSanitizer);

  readonly rejecting = signal(false);
  readonly rejectReason = signal('');
  readonly confirmingDelete = signal(false);
  readonly renewOpen = signal(false);
  renewDate = '';

  readonly relateOpen = signal(false);
  relateTargetId: number | null = null;
  relateType: RelationType = 'REPLACE';

  readonly overrideOpen = signal(false);
  overrideStatus: DocStatus = 'ACTIVE';
  overrideExpiry = '';
  readonly statusOptions: DocStatus[] = ['DRAFT', 'PENDING', 'ACTIVE', 'WARNING', 'EXPIRED', 'REJECTED'];
  readonly isAdmin = computed(() => this.auth.user()?.role === 'ADMIN');

  readonly minDate = fmtIso(new Date(Date.now() + 86400000)); // backend validate @Future

  readonly doc = this.store.selected;

  /** nhãn hiển thị cho audit log */
  readonly actionVn = AUDIT_ACTION_VN;
  readonly actionColor = AUDIT_ACTION_COLOR;

  /** các loại quan hệ cho dropdown */
  readonly relationTypes: { value: RelationType; label: string }[] =
    (Object.keys(REL_TYPE_VN) as RelationType[]).map(t => ({ value: t, label: REL_TYPE_VN[t] }));

  constructor() {
    // mở văn bản nào thì tải lịch sử thay đổi + quan hệ của văn bản đó
    effect(() => {
      const id = this.store.selectedId();
      if (id != null) {
        this.store.loadHistory(id);
        this.store.loadRelations(id);
      } else {
        this.store.history.set([]);
        this.store.relations.set([]);
      }
    });
  }

  /** danh sách văn bản có thể chọn để tạo quan hệ (trừ chính nó) */
  readonly relateCandidates = computed(() =>
    this.store.all().filter(d => d.id !== this.doc()?.id));

  /** Chủ sở hữu hoặc quản lý mới được thao tác vòng đời */
  readonly isOwner = computed(() => this.doc()?.ownerId === this.auth.user()?.userId);
  readonly canApprove = computed(() => this.doc()?.dispStatus === 'PENDING' && this.auth.isManager());
  readonly canSubmit = computed(() => {
    const s = this.doc()?.dispStatus;
    return (s === 'DRAFT' || s === 'REJECTED') && this.isOwner();
  });
  readonly canRenew = computed(() => {
    const s = this.doc()?.dispStatus;
    return s === 'ACTIVE' || s === 'WARNING' || s === 'EXPIRED';
  });

  /** chủ sở hữu hoặc quản lý mới được tạo quan hệ (thay thế/bãi bỏ/sửa đổi) */
  readonly canRelate = computed(() => this.isOwner() || this.auth.isManager());

  readonly isPdf = computed(() => this.doc()?.filePath?.toLowerCase().endsWith('.pdf') ?? false);

  readonly previewUrl = computed<SafeResourceUrl | null>(() => {
    const d = this.doc();
    if (!d?.filePath || !this.isPdf()) return null;
    // #toolbar=0&navpanes=0: ẩn thanh công cụ chỉnh sửa của trình xem PDF trình duyệt
    return this.sanitizer.bypassSecurityTrustResourceUrl(this.fileUrl(d.filePath) + '#toolbar=0&navpanes=0');
  });

  close(): void {
    this.store.selectedId.set(null);
    this.rejecting.set(false);
    this.rejectReason.set('');
    this.confirmingDelete.set(false);
    this.renewOpen.set(false);
    this.relateOpen.set(false);
    this.relateTargetId = null;
    this.relateType = 'REPLACE';
    this.overrideOpen.set(false);
  }

  openRelate(): void {
    this.relateTargetId = null;
    this.relateType = 'REPLACE';
    this.relateOpen.set(true);
  }

  confirmRelate(): void {
    const d = this.doc();
    if (!d || this.relateTargetId == null) return;
    this.store.relate(d.id, +this.relateTargetId, this.relateType);
    this.relateOpen.set(false);
  }

  openOverride(): void {
    const d = this.doc();
    if (!d) return;
    this.overrideStatus = d.status;
    this.overrideExpiry = d.expiryDate;
    this.overrideOpen.set(true);
  }
  confirmOverride(): void {
    const d = this.doc();
    if (!d) return;
    this.store.adminOverride(d.id, { status: this.overrideStatus, expiryDate: this.overrideExpiry });
    this.overrideOpen.set(false);
  }

  /** nhãn quan hệ theo chiều, vd "Thay thế cho" / "Bị bãi bỏ bởi". */
  relDirLabel(r: DocRelation): string {
    return REL_DIRECTION_VN[r.type][r.direction];
  }

  relColor(type: RelationType): string {
    return REL_TYPE_COLOR[type];
  }

  /** parse JSON changes → danh sách {trường, trước, sau} để hiển thị. */
  parseChanges(json: string | null): { field: string; old: string; new: string }[] {
    if (!json) return [];
    try {
      const obj = JSON.parse(json) as Record<string, { old: unknown; new: unknown }>;
      return Object.entries(obj).map(([k, v]) => ({
        field: FIELD_VN[k] ?? k,
        old: v.old == null || v.old === '' ? '—' : String(v.old),
        new: v.new == null || v.new === '' ? '—' : String(v.new)
      }));
    } catch {
      return [];
    }
  }

  actionLabel(action: string): string {
    return this.actionVn[action as AuditAction] ?? action;
  }

  actionDot(action: string): string {
    return this.actionColor[action as AuditAction] ?? '#9A95A2';
  }

  fmtDateTime(iso: string): string {
    const d = new Date(iso);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  fileName(path: string): string {
    return path.split(/[\\/]/).pop() ?? path;
  }

  fileUrl(path: string): string {
    return `/uploads/${encodeURIComponent(this.fileName(path))}`;
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const d = this.doc();
    if (file && d) this.store.upload(d.id, file);
    input.value = '';
  }

  openRenew(): void {
    const d = this.doc();
    if (!d) return;
    // gợi ý mặc định: +6 tháng kể từ hạn hiện tại (tối thiểu từ hôm nay)
    const base = Math.max(toDate(d.expiryDate).getTime(), Date.now());
    this.renewDate = fmtIso(new Date(base + 180 * 86400000));
    this.renewOpen.set(true);
  }

  confirmRenew(): void {
    const d = this.doc();
    if (!d || !this.renewDate || this.renewDate < this.minDate) return;
    this.store.renewTo(d.id, this.renewDate);
    this.renewOpen.set(false);
  }

  sendReject(): void {
    const d = this.doc();
    const reason = this.rejectReason().trim();
    if (!d || !reason) return;
    this.store.reject(d.id, reason);
    this.rejecting.set(false);
    this.rejectReason.set('');
  }

  doDelete(): void {
    const d = this.doc();
    if (d) this.store.remove(d.id);
    this.confirmingDelete.set(false);
  }
}
