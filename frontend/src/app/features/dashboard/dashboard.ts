import { Component, computed, inject } from '@angular/core';
import { DocumentStore, StatusFilter } from '../../core/document-store.service';

@Component({
  selector: 'app-dashboard',
  imports: [],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class DashboardPage {
  readonly store = inject(DocumentStore);

  readonly year = new Date().getFullYear();

  readonly chips: { key: StatusFilter; label: string }[] = [
    { key: 'all', label: 'Tất cả' },
    { key: 'WARNING', label: 'Sắp hết hạn' },
    { key: 'EXPIRED', label: 'Đã hết hạn' },
    { key: 'ACTIVE', label: 'Còn hiệu lực' },
    { key: 'PENDING', label: 'Chờ xử lý' }
  ];

  readonly sortLabels: Record<string, string> = { urgency: 'Mức khẩn', name: 'Tên A→Z', type: 'Loại' };

  /** % văn bản cần chú ý (hết hạn + sắp hết hạn) cho donut */
  readonly donut = computed(() => {
    const c = this.store.counts();
    const total = c.total || 1;
    const eP = (c.expired / total) * 100;
    const sP = eP + (c.warning / total) * 100;
    return {
      riskPct: Math.round(((c.expired + c.warning) / total) * 100) + '%',
      onTime: Math.round((c.active / total) * 100) + '%',
      gradient: `conic-gradient(#FFFFFF 0 ${eP.toFixed(1)}%, #FFD27A ${eP.toFixed(1)}% ${sP.toFixed(1)}%, rgba(255,255,255,.22) ${sP.toFixed(1)}% 100%)`
    };
  });

  readonly tableDocs = computed(() => this.store.filtered().slice(0, 7));

  cycleSort(): void {
    const order = ['urgency', 'name', 'type'] as const;
    const cur = this.store.sortBy();
    this.store.sortBy.set(order[(order.indexOf(cur) + 1) % order.length]);
  }
}
