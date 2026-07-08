import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DocumentStore, StatusFilter } from '../../core/document-store.service';
import { DocType, STATUS_THEME, TYPE_THEME } from '../../core/models';

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

  /** chu vi vòng donut (r = 52) */
  readonly RING_C = 2 * Math.PI * 52;

  goNew(): void {
    this.router.navigate(['/documents/new']);
  }

  /** Các cung của donut "Tình trạng hiệu lực" — mỗi trạng thái một màu. */
  readonly statusSegments = computed(() => {
    const c = this.store.counts();
    const order = [
      { key: 'ACTIVE',   label: 'Còn hiệu lực', value: c.active,   color: STATUS_THEME.ACTIVE.stripe },
      { key: 'WARNING',  label: 'Sắp hết hạn',  value: c.warning,  color: STATUS_THEME.WARNING.stripe },
      { key: 'EXPIRED',  label: 'Đã hết hạn',   value: c.expired,  color: STATUS_THEME.EXPIRED.stripe },
      { key: 'APPROVED', label: 'Chờ hiệu lực', value: c.approved, color: STATUS_THEME.APPROVED.stripe },
      { key: 'PENDING',  label: 'Chờ xử lý',    value: c.pending,  color: STATUS_THEME.PENDING.stripe }
    ];
    const total = order.reduce((s, o) => s + o.value, 0) || 1;
    let acc = 0;
    return order.map(o => {
      const frac = o.value / total;
      const seg = { ...o, pct: Math.round(frac * 100), arc: frac * this.RING_C, rot: acc * 360 - 90 };
      acc += frac;
      return seg;
    });
  });

  /** Phân bố văn bản theo loại — thanh ngang. */
  readonly byType = computed(() => {
    const all = this.store.all();
    const max = Math.max(1, ...(['CONTRACT', 'LICENSE', 'CERTIFICATE', 'SR'] as DocType[])
      .map(t => all.filter(d => d.type === t).length));
    const meta: { key: DocType; label: string; color: string }[] = [
      { key: 'CONTRACT',    label: 'Hợp đồng',  color: TYPE_THEME.CONTRACT[1] },
      { key: 'LICENSE',     label: 'Giấy phép', color: TYPE_THEME.LICENSE[1] },
      { key: 'CERTIFICATE', label: 'Chứng chỉ', color: TYPE_THEME.CERTIFICATE[1] },
      { key: 'SR',          label: 'SR nội bộ', color: TYPE_THEME.SR[1] }
    ];
    return meta.map(m => {
      const value = all.filter(d => d.type === m.key).length;
      return { ...m, value, pct: Math.round((value / max) * 100) };
    });
  });

  readonly chips: { key: StatusFilter; label: string }[] = [
    { key: 'all', label: 'Tất cả' },
    { key: 'WARNING', label: 'Sắp hết hạn' },
    { key: 'EXPIRED', label: 'Đã hết hạn' },
    { key: 'ACTIVE', label: 'Còn hiệu lực' },
    { key: 'APPROVED', label: 'Chờ hiệu lực' },
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
