import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { AuthService } from '../../core/auth.service';
import { DocumentStore } from '../../core/document-store.service';
import { fmtIso, toDate } from '../../core/models';

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

  readonly minDate = fmtIso(new Date(Date.now() + 86400000)); // backend validate @Future

  readonly doc = this.store.selected;

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

  readonly isPdf = computed(() => this.doc()?.filePath?.toLowerCase().endsWith('.pdf') ?? false);

  readonly previewUrl = computed<SafeResourceUrl | null>(() => {
    const d = this.doc();
    if (!d?.filePath || !this.isPdf()) return null;
    return this.sanitizer.bypassSecurityTrustResourceUrl(this.fileUrl(d.filePath));
  });

  close(): void {
    this.store.selectedId.set(null);
    this.rejecting.set(false);
    this.rejectReason.set('');
    this.confirmingDelete.set(false);
    this.renewOpen.set(false);
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
