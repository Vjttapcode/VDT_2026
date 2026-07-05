import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DocumentStore, StatusFilter } from '../../core/document-store.service';

@Component({
  selector: 'app-dashboard',
  imports: [],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class DashboardPage {
  readonly store = inject(DocumentStore);
  private router = inject(Router);

  readonly year = new Date().getFullYear();

  goNew(): void {
    this.router.navigate(['/documents/new']);
  }

  readonly chips: { key: StatusFilter; label: string }[] = [
    { key: 'all', label: 'Tất cả' },
    { key: 'WARNING', label: 'Sắp hết hạn' },
    { key: 'EXPIRED', label: 'Đã hết hạn' },
    { key: 'ACTIVE', label: 'Còn hiệu lực' },
    { key: 'PENDING', label: 'Chờ xử lý' }
  ];

  readonly sortLabels: Record<string, string> = { urgency: 'Mức khẩn', name: 'Tên A→Z', type: 'Loại' };

  /** % đúng hạn / cần chú ý cho dải chỉ số trong card biểu đồ */
  readonly donut = computed(() => {
    const c = this.store.counts();
    const total = c.total || 1;
    return {
      riskPct: Math.round(((c.expired + c.warning) / total) * 100) + '%',
      onTime: Math.round((c.active / total) * 100) + '%'
    };
  });

  readonly tableDocs = computed(() => this.store.filtered().slice(0, 7));

  cycleSort(): void {
    const order = ['urgency', 'name', 'type'] as const;
    const cur = this.store.sortBy();
    this.store.sortBy.set(order[(order.indexOf(cur) + 1) % order.length]);
  }
}
