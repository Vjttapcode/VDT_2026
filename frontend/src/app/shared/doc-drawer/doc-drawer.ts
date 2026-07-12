import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DocumentStore } from '../../core/document-store.service';
import {
  AUDIT_ACTION_COLOR, AUDIT_ACTION_VN, AuditAction, DocRelation, FIELD_VN,
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
  private router = inject(Router);

  /** popup xác nhận cho gia hạn / phê duyệt / từ chối / mở lại sửa đổi — chỉ 1 mở tại 1 thời điểm */
  readonly actionModal = signal<'approve' | 'reject' | 'renew' | 'reopen' | null>(null);
  readonly rejectReason = signal('');
  readonly confirmingDelete = signal(false);
  renewDate = '';

  readonly relateOpen = signal(false);
  readonly relateTargetId = signal<number | null>(null);
  readonly relateQuery = signal('');
  relateType: RelationType = 'REPLACE';

  readonly effectiveOpen = signal(false);
  effectiveDate = '';

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
        this.store.loadVersions(id);
      } else {
        this.store.history.set([]);
        this.store.relations.set([]);
        this.store.versions.set([]);
      }
    });
  }

  /** danh sách văn bản có thể chọn để tạo quan hệ (trừ chính nó) */
  readonly relateCandidates = computed(() =>
    this.store.all().filter(d => d.id !== this.doc()?.id));

  /** kết quả lọc theo từ khóa (mã / tên / loại) cho combobox tìm kiếm — tối đa 20 dòng */
  readonly relateFiltered = computed(() => {
    const q = this.relateQuery().trim().toLowerCase();
    const list = this.relateCandidates();
    return (q ? list.filter(c => `${c.code} ${c.title} ${c.typeVn}`.toLowerCase().includes(q)) : list).slice(0, 20);
  });

  /** văn bản đối tác đang chọn (để hiển thị chip đã chọn) */
  readonly relateSelected = computed(() =>
    this.relateCandidates().find(c => c.id === this.relateTargetId()) ?? null);

  /** Chủ sở hữu hoặc quản lý mới được thao tác vòng đời */
  readonly isOwner = computed(() => this.doc()?.ownerId === this.auth.user()?.userId);
  readonly canApprove = computed(() => this.doc()?.dispStatus === 'PENDING' && this.auth.isManager());
  readonly canSubmit = computed(() => {
    const s = this.doc()?.dispStatus;
    return (s === 'DRAFT' || s === 'REJECTED') && this.isOwner();
  });
  /** DRAFT/REJECTED: chủ sở hữu hoặc admin/manager (backend còn kiểm tra đúng phạm vi tổ chức). */
  readonly canEdit = computed(() => {
    const s = this.doc()?.dispStatus;
    return (s === 'DRAFT' || s === 'REJECTED') && (this.isOwner() || this.auth.isManager());
  });
  readonly canRenew = computed(() => {
    const s = this.doc()?.dispStatus;
    return s === 'ACTIVE' || s === 'WARNING' || s === 'EXPIRED';
  });
  /** mở lại văn bản đã ban hành để sửa đổi/tái ban hành — chủ sở hữu hoặc quản lý */
  readonly canReopen = computed(() => this.canRenew() && (this.isOwner() || this.auth.isManager()));
  /** văn bản đã duyệt chờ hiệu lực: chủ sở hữu/quản lý được kích hoạt ngay hoặc dời ngày */
  readonly canSetEffective = computed(() =>
    this.doc()?.dispStatus === 'APPROVED' && (this.isOwner() || this.auth.isManager()));

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
    this.actionModal.set(null);
    this.rejectReason.set('');
    this.confirmingDelete.set(false);
    this.relateOpen.set(false);
    this.relateTargetId.set(null);
    this.relateQuery.set('');
    this.relateType = 'REPLACE';
    this.effectiveOpen.set(false);
  }

  goEdit(): void {
    const d = this.doc();
    if (!d) return;
    this.router.navigate(['/documents', d.id, 'edit']);
    this.close();   // drawer render toàn cục ở Shell, không tự đóng khi router điều hướng
  }

  openEffective(): void {
    const d = this.doc();
    if (!d) return;
    this.effectiveDate = d.effectiveDate ?? fmtIso(new Date());
    this.effectiveOpen.set(true);
  }

  /** đổi ngày hiệu lực; đặt <= hôm nay = kích hoạt ngay */
  confirmEffective(): void {
    const d = this.doc();
    if (!d || !this.effectiveDate || this.effectiveDate >= d.expiryDate) return;
    this.store.setEffective(d.id, this.effectiveDate);
    this.effectiveOpen.set(false);
  }

  /** kích hoạt hiệu lực ngay hôm nay */
  activateNow(): void {
    const d = this.doc();
    if (!d) return;
    this.store.setEffective(d.id, fmtIso(new Date()));
    this.effectiveOpen.set(false);
  }

  openRelate(): void {
    this.relateTargetId.set(null);
    this.relateQuery.set('');
    this.relateType = 'REPLACE';
    this.relateOpen.set(true);
  }

  /** chọn văn bản đối tác từ danh sách gợi ý */
  pickRelate(id: number): void {
    this.relateTargetId.set(id);
    this.relateQuery.set('');
  }

  /** bỏ chọn để tìm lại */
  clearRelatePick(): void {
    this.relateTargetId.set(null);
    this.relateQuery.set('');
  }

  confirmRelate(): void {
    const d = this.doc();
    const target = this.relateTargetId();
    if (!d || target == null) return;
    this.store.relate(d.id, target, this.relateType);
    this.relateOpen.set(false);
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

  /** dd/mm/yyyy cho ngày ISO (không kèm giờ) — dùng cho danh sách phiên bản. */
  fmtDateOnly(iso: string): string {
    const d = toDate(iso);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
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

  openApprove(): void {
    this.actionModal.set('approve');
  }

  openReject(): void {
    this.rejectReason.set('');
    this.actionModal.set('reject');
  }

  openRenew(): void {
    const d = this.doc();
    if (!d) return;
    // gợi ý mặc định: +6 tháng kể từ hạn hiện tại (tối thiểu từ hôm nay)
    const base = Math.max(toDate(d.expiryDate).getTime(), Date.now());
    this.renewDate = fmtIso(new Date(base + 180 * 86400000));
    this.actionModal.set('renew');
  }

  closeActionModal(): void {
    this.actionModal.set(null);
  }

  confirmApprove(): void {
    const d = this.doc();
    if (!d) return;
    this.store.approve(d.id);
    this.actionModal.set(null);
  }

  confirmReject(): void {
    const d = this.doc();
    const reason = this.rejectReason().trim();
    if (!d || !reason) return;
    this.store.reject(d.id, reason);
    this.actionModal.set(null);
    this.rejectReason.set('');
  }

  confirmRenew(): void {
    const d = this.doc();
    if (!d || !this.renewDate || this.renewDate < this.minDate) return;
    this.store.renewTo(d.id, this.renewDate);
    this.actionModal.set(null);
  }

  openReopen(): void {
    this.actionModal.set('reopen');
  }

  /** mở lại về DRAFT — sửa xong nộp duyệt lại sẽ tái ban hành với phiên bản +0.1 */
  confirmReopen(): void {
    const d = this.doc();
    if (!d) return;
    this.store.reopen(d.id);
    this.actionModal.set(null);
  }

  doDelete(): void {
    const d = this.doc();
    if (d) this.store.remove(d.id);
    this.confirmingDelete.set(false);
  }
}
