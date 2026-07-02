import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { DocumentStore } from '../../core/document-store.service';

@Component({
  selector: 'app-doc-drawer',
  imports: [],
  templateUrl: './doc-drawer.html',
  styleUrl: './doc-drawer.scss'
})
export class DocDrawer {
  readonly store = inject(DocumentStore);
  readonly auth = inject(AuthService);

  readonly rejecting = signal(false);
  readonly rejectReason = signal('');
  readonly confirmingDelete = signal(false);

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

  close(): void {
    this.store.selectedId.set(null);
    this.rejecting.set(false);
    this.rejectReason.set('');
    this.confirmingDelete.set(false);
  }

  fileName(path: string): string {
    return path.split(/[\\/]/).pop() ?? path;
  }

  fileUrl(path: string): string {
    const name = this.fileName(path);
    return `/uploads/${encodeURIComponent(name)}`;
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const d = this.doc();
    if (file && d) this.store.upload(d.id, file);
    input.value = '';
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
