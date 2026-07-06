import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DocumentStore, StatusFilter } from '../../core/document-store.service';
import { AuthService } from '../../core/auth.service';
import { DocType, TYPE_VN, fmtIso } from '../../core/models';

@Component({
  selector: 'app-documents',
  imports: [],
  templateUrl: './documents.html',
  styleUrl: './documents.scss'
})
export class DocumentsPage {
  readonly store = inject(DocumentStore);
  readonly auth = inject(AuthService);
  private router = inject(Router);

  readonly typeVn = TYPE_VN;
  /** mở/đóng panel lọc nâng cao */
  readonly showFilters = signal(false);

  /** ngày gia hạn hàng loạt — mặc định +6 tháng */
  bulkDate = fmtIso(new Date(Date.now() + 180 * 86400000));
  readonly minDate = fmtIso(new Date(Date.now() + 86400000));

  readonly selectedCount = computed(() => this.store.selectedIds().size);
  readonly allSelected = computed(() => {
    const f = this.store.filtered();
    return f.length > 0 && f.every(d => this.store.isSelected(d.id));
  });
  /** văn bản đã chọn đang chờ duyệt — chỉ những văn bản này mới phê duyệt hàng loạt được */
  readonly pendingSelected = computed(() =>
    this.store.all().filter(d => this.store.isSelected(d.id) && d.dispStatus === 'PENDING'));

  goNew(): void {
    this.router.navigate(['/documents/new']);
  }

  /* ===== chọn nhiều / hàng loạt / export ===== */
  toggleAll(): void {
    this.allSelected() ? this.store.clearSelection() : this.store.selectAllFiltered();
  }
  onRowCheck(id: number, event: Event): void {
    event.stopPropagation();
    this.store.toggleSelect(id);
  }
  doBulkRenew(): void {
    const ids = [...this.store.selectedIds()];
    if (ids.length && this.bulkDate >= this.minDate) this.store.bulkRenew(ids, this.bulkDate);
  }
  doBulkApprove(): void {
    const ids = this.pendingSelected().map(d => d.id);
    if (ids.length) this.store.bulkApprove(ids);
  }
  exportCsv(): void { this.store.exportCsv(); }

  readonly chips: { key: StatusFilter; label: string }[] = [
    { key: 'all', label: 'Tất cả' },
    { key: 'WARNING', label: 'Sắp hết hạn' },
    { key: 'EXPIRED', label: 'Đã hết hạn' },
    { key: 'ACTIVE', label: 'Còn hiệu lực' },
    { key: 'PENDING', label: 'Chờ xử lý' }
  ];

  readonly sortLabels: Record<string, string> = { urgency: 'Mức khẩn', name: 'Tên A→Z', type: 'Loại' };

  cycleSort(): void {
    const order = ['urgency', 'name', 'type'] as const;
    const cur = this.store.sortBy();
    this.store.sortBy.set(order[(order.indexOf(cur) + 1) % order.length]);
  }

  toggleFilters(): void { this.showFilters.update(v => !v); }

  setType(v: string): void { this.store.typeFilter.set(v === 'all' ? 'all' : (v as DocType)); }
  setDept(v: string): void { this.store.deptFilter.set(v === 'all' ? 'all' : +v); }
}
