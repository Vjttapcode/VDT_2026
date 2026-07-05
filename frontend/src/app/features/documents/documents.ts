import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DocumentStore, StatusFilter } from '../../core/document-store.service';

@Component({
  selector: 'app-documents',
  imports: [],
  templateUrl: './documents.html',
  styleUrl: './documents.scss'
})
export class DocumentsPage {
  readonly store = inject(DocumentStore);
  private router = inject(Router);

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

  cycleSort(): void {
    const order = ['urgency', 'name', 'type'] as const;
    const cur = this.store.sortBy();
    this.store.sortBy.set(order[(order.indexOf(cur) + 1) % order.length]);
  }
}
