import { Component, OnInit, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../core/notification.service';
import { DocumentStore } from '../../core/document-store.service';
import { DEPT_VN, STATUS_THEME, fmtDate, toDate } from '../../core/models';

@Component({
  selector: 'app-alert-log',
  imports: [FormsModule],
  templateUrl: './alert-log.html',
  styleUrl: './alert-log.scss'
})
export class AlertLogPage implements OnInit {
  readonly noti = inject(NotificationService);
  readonly store = inject(DocumentStore);

  readonly depts = Object.entries(DEPT_VN).map(([id, name]) => ({ id: +id, name }));
  readonly warnTheme = STATUS_THEME['WARNING'];
  readonly expTheme = STATUS_THEME['EXPIRED'];

  deptId: number | null = null;
  from = '';
  to = '';

  /** map documentId -> tiêu đề văn bản (nếu user có quyền thấy) */
  readonly docTitles = computed(() => {
    const map = new Map<number, string>();
    for (const d of this.store.all()) map.set(d.id, d.title);
    return map;
  });

  /** áp ô tìm kiếm chung trên header (tên văn bản / email người nhận) */
  readonly filteredLogs = computed(() => {
    const q = this.store.query().trim().toLowerCase();
    if (!q) return this.noti.logs();
    return this.noti.logs().filter(l =>
      this.docTitle(l.documentId).toLowerCase().includes(q) ||
      l.recipientEmail.toLowerCase().includes(q));
  });

  readonly sentCount = computed(() => this.filteredLogs().filter(l => l.status === 'SENT').length);
  readonly failedCount = computed(() => this.filteredLogs().filter(l => l.status === 'FAILED').length);

  ngOnInit(): void {
    this.reload();
    if (this.store.docs().length === 0) this.store.load();
  }

  reload(): void {
    this.noti.loadLogs(this.deptId, this.from, this.to);
  }

  clearFilter(): void {
    this.deptId = null;
    this.from = '';
    this.to = '';
    this.reload();
  }

  deptName(id: number | null): string {
    return id != null ? DEPT_VN[id] ?? `Phòng ban #${id}` : 'Toàn hệ thống';
  }

  docTitle(id: number): string {
    return this.docTitles().get(id) ?? `Văn bản #${id}`;
  }

  fmtVn(iso: string): string {
    return fmtDate(toDate(iso));
  }

  openDoc(id: number): void {
    if (this.docTitles().has(id)) this.store.selectedId.set(id);
  }
}
