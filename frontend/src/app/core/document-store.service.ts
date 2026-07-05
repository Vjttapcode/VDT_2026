import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  DashboardStats, DEPT_VN, DocStatus, DocType, DocLevel, DocumentDto, DocView,
  daysFromToday, daysText, fmtDate, fmtIso, STATUS_THEME, toDate, TYPE_CODE, TYPE_THEME, TYPE_VN, LEVEL_VN
} from './models';

const API = '/api/documents';

export type StatusFilter = 'all' | 'ACTIVE' | 'WARNING' | 'EXPIRED' | 'PENDING';
export type SortKey = 'urgency' | 'name' | 'type';
export type TypeFilter = DocType | 'all';
export type DeptFilter = number | 'all';

export interface Toast { id: number; kind: 'ok' | 'err'; text: string; }

/** Ngưỡng cảnh báo "sắp hết hạn" — đồng bộ alert_configs.remind_days max = 30. */
export const ALERT_WINDOW = 30;

@Injectable({ providedIn: 'root' })
export class DocumentStore {
  private http = inject(HttpClient);
  private toastSeq = 0;

  /* ===== server state ===== */
  readonly docs = signal<DocumentDto[]>([]);
  readonly stats = signal<DashboardStats | null>(null);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);

  /* ===== ui state ===== */
  readonly query = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly sortBy = signal<SortKey>('urgency');
  readonly selectedId = signal<number | null>(null);
  readonly showNotif = signal(false);
  readonly toasts = signal<Toast[]>([]);

  /* ===== bộ lọc nâng cao (tra cứu) ===== */
  readonly typeFilter = signal<TypeFilter>('all');       // loại văn bản
  readonly deptFilter = signal<DeptFilter>('all');       // đơn vị ban hành
  readonly ownerQuery = signal('');                      // người phụ trách
  readonly expiryFrom = signal('');                      // hết hạn từ (yyyy-mm-dd)
  readonly expiryTo = signal('');                        // hết hạn đến (yyyy-mm-dd)

  /* ===== derived ===== */
  readonly all = computed<DocView[]>(() => this.docs().map(d => this.present(d)));

  readonly filtered = computed<DocView[]>(() => {
    const q = this.query().trim().toLowerCase();
    const st = this.statusFilter();
    const tp = this.typeFilter();
    const dp = this.deptFilter();
    const oq = this.ownerQuery().trim().toLowerCase();
    const from = this.expiryFrom();
    const to = this.expiryTo();
    const match = (d: DocView) =>
      // ô tìm toàn cục: số VB, tên, đơn vị, loại, cấp, người phụ trách, nội dung mô tả
      (!q || `${d.title} ${d.code} ${d.deptName} ${d.typeVn} ${d.levelVn} ${d.ownerVn} ${d.description ?? ''}`.toLowerCase().includes(q)) &&
      (st === 'all' || d.dispStatus === st || (st === 'PENDING' && (d.dispStatus === 'DRAFT' || d.dispStatus === 'REJECTED'))) &&
      (tp === 'all' || d.type === tp) &&                                   // loại văn bản
      (dp === 'all' || d.departmentId === dp) &&                          // đơn vị ban hành
      (!oq || d.ownerVn.toLowerCase().includes(oq)) &&                    // người phụ trách
      (!from || d.expiryDate >= from) &&                                  // hết hạn từ (so sánh chuỗi ISO)
      (!to || d.expiryDate <= to);                                        // hết hạn đến
    const sorters: Record<SortKey, (a: DocView, b: DocView) => number> = {
      urgency: (a, b) => a.daysLeft - b.daysLeft,
      name: (a, b) => a.title.localeCompare(b.title, 'vi'),
      type: (a, b) => a.typeVn.localeCompare(b.typeVn, 'vi')
    };
    return this.all().filter(match).sort(sorters[this.sortBy()]);
  });

  /* ===== tuỳ chọn cho dropdown lọc — chỉ hiện giá trị đang có trong phạm vi role ===== */
  readonly typeOptions = computed<DocType[]>(() => {
    const present = new Set(this.all().map(d => d.type));
    return (['CONTRACT', 'LICENSE', 'CERTIFICATE', 'SR'] as DocType[]).filter(t => present.has(t));
  });

  readonly deptOptions = computed<{ id: number; name: string }[]>(() => {
    const ids = [...new Set(this.all().map(d => d.departmentId).filter((x): x is number => x != null))];
    return ids.map(id => ({ id, name: DEPT_VN[id] ?? `Phòng ban #${id}` })).sort((a, b) => a.id - b.id);
  });

  /** Có đang bật ít nhất một bộ lọc nâng cao không (để hiện nút Xóa lọc). */
  readonly hasAdvancedFilter = computed(() =>
    this.typeFilter() !== 'all' || this.deptFilter() !== 'all' ||
    !!this.ownerQuery().trim() || !!this.expiryFrom() || !!this.expiryTo());

  readonly selected = computed<DocView | null>(() => {
    const id = this.selectedId();
    return id == null ? null : this.all().find(d => d.id === id) ?? null;
  });

  readonly alertList = computed<DocView[]>(() =>
    this.all()
      .filter(d => d.dispStatus === 'EXPIRED' || d.dispStatus === 'WARNING')
      .sort((a, b) => a.daysLeft - b.daysLeft)
  );

  readonly recent = computed<DocView[]>(() => {
    const q = this.query().trim().toLowerCase();
    return [...this.all()]
      .filter(d => !q || `${d.title} ${d.code}`.toLowerCase().includes(q))
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      .slice(0, 8);
  });

  readonly counts = computed(() => {
    const all = this.all();
    const by = (s: DocStatus) => all.filter(d => d.dispStatus === s).length;
    return {
      total: all.length,
      active: by('ACTIVE'),
      warning: by('WARNING'),
      expired: by('EXPIRED'),
      pending: by('PENDING') + by('DRAFT') + by('REJECTED')
    };
  });

  readonly notifCount = computed(() => this.counts().warning + this.counts().expired);

  /** Số văn bản hết hạn theo tháng của năm hiện tại — cho bar chart. */
  readonly monthlyExpiry = computed(() => {
    const year = new Date().getFullYear();
    const counts = Array.from({ length: 12 }, () => 0);
    for (const d of this.all()) {
      const dt = toDate(d.expiryDate);
      if (dt.getFullYear() === year) counts[dt.getMonth()]++;
    }
    const max = Math.max(...counts, 1);
    return counts.map((v, i) => ({ m: 'T' + (i + 1), v, h: Math.round((v / max) * 128) + 4 }));
  });

  /* ===== api ===== */

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    forkJoin({
      docs: this.http.get<DocumentDto[]>(API),
      stats: this.http.get<DashboardStats>(`${API}/dashboard/stats`).pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ docs, stats }) => {
        this.docs.set(docs);
        this.stats.set(stats);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(this.errText(err, 'Không tải được dữ liệu văn bản'));
      }
    });
  }

  /** Tạo văn bản, kèm upload tệp và gửi duyệt tùy chọn — dùng cho trang /documents/new. */
  async createFull(
    req: { title: string; description: string; type: DocType; level: DocLevel; expiryDate: string },
    file: File | null,
    submitNow: boolean
  ): Promise<number | null> {
    let doc: DocumentDto;
    try {
      doc = await firstValueFrom(this.http.post<DocumentDto>(API, req));
    } catch (err) {
      this.toast('err', this.errText(err as HttpErrorResponse, 'Không tạo được văn bản'));
      return null;
    }
    try {
      if (file) {
        const form = new FormData();
        form.append('file', file);
        await firstValueFrom(this.http.post<DocumentDto>(`${API}/${doc.id}/upload`, form));
      }
      if (submitNow) {
        await firstValueFrom(this.http.post<DocumentDto>(`${API}/${doc.id}/submit`, {}));
      }
      this.afterMutation(doc.id, submitNow ? 'Đã tạo và gửi duyệt văn bản' : 'Đã lưu văn bản nháp');
    } catch (err) {
      // văn bản đã tạo — báo phần lỗi còn lại, vẫn điều hướng để user xử lý tiếp
      this.afterMutation(doc.id, undefined, this.errText(err as HttpErrorResponse, 'Đã tạo văn bản nhưng bước sau thất bại'));
    }
    return doc.id;
  }

  submit(id: number): void { this.action(id, 'submit', 'Đã gửi duyệt'); }
  approve(id: number): void { this.action(id, 'approve', 'Đã phê duyệt văn bản'); }

  reject(id: number, reason: string): void {
    this.http.post<DocumentDto>(`${API}/${id}/reject`, { reason }).subscribe({
      next: () => this.afterMutation(id, 'Đã từ chối văn bản'),
      error: err => this.toast('err', this.errText(err, 'Không từ chối được'))
    });
  }

  /** Gia hạn tới ngày cụ thể do người dùng chọn từ calendar. */
  renewTo(id: number, isoDate: string): void {
    this.http.post<DocumentDto>(`${API}/${id}/renew`, { newExpiryDate: isoDate }).subscribe({
      next: () => this.afterMutation(id, `Đã gia hạn tới ${fmtDate(toDate(isoDate))}`),
      error: err => this.toast('err', this.errText(err, 'Không gia hạn được'))
    });
  }

  remove(id: number): void {
    this.http.delete(`${API}/${id}`).subscribe({
      next: () => {
        this.selectedId.set(null);
        this.load();
        this.toast('ok', 'Đã xóa văn bản');
      },
      error: err => this.toast('err', this.errText(err, 'Không xóa được'))
    });
  }

  upload(id: number, file: File): void {
    const form = new FormData();
    form.append('file', file);
    this.http.post<DocumentDto>(`${API}/${id}/upload`, form).subscribe({
      next: () => this.afterMutation(id, 'Đã tải tệp lên'),
      error: err => this.toast('err', this.errText(err, 'Không tải được tệp lên'))
    });
  }

  /** Xóa toàn bộ bộ lọc (ô tìm, trạng thái, và các bộ lọc nâng cao). */
  clearAllFilters(): void {
    this.query.set('');
    this.statusFilter.set('all');
    this.typeFilter.set('all');
    this.deptFilter.set('all');
    this.ownerQuery.set('');
    this.expiryFrom.set('');
    this.expiryTo.set('');
  }

  toast(kind: 'ok' | 'err', text: string): void {
    const id = ++this.toastSeq;
    this.toasts.update(list => [...list, { id, kind, text }]);
    setTimeout(() => this.toasts.update(list => list.filter(t => t.id !== id)), 3800);
  }

  /* ===== helpers ===== */

  private action(id: number, path: string, okText: string): void {
    this.http.post<DocumentDto>(`${API}/${id}/${path}`, {}).subscribe({
      next: () => this.afterMutation(id, okText),
      error: err => this.toast('err', this.errText(err, 'Thao tác thất bại'))
    });
  }

  private afterMutation(id: number, okText?: string, errText?: string): void {
    this.load();
    this.selectedId.set(id);
    if (okText) this.toast('ok', okText);
    if (errText) this.toast('err', errText);
  }

  private errText(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 0) return 'Không kết nối được máy chủ, kiểm tra backend đang chạy';
    const body = err.error;
    if (typeof body === 'string' && body) return body;
    return body?.message ?? body?.error ?? fallback;
  }

  private present(d: DocumentDto): DocView {
    const daysLeft = daysFromToday(d.expiryDate);
    // ACTIVE/WARNING suy lại theo daysLeft để UI realtime, không chờ cron backend
    let dispStatus: DocStatus = d.status;
    if (d.status === 'ACTIVE' || d.status === 'WARNING') {
      dispStatus = daysLeft < 0 ? 'EXPIRED' : daysLeft <= ALERT_WINDOW ? 'WARNING' : 'ACTIVE';
    }
    const st = STATUS_THEME[dispStatus];
    const [iconBg, iconFg] = TYPE_THEME[d.type];
    const created = toDate(d.createdAt);
    return {
      ...d,
      code: `${String(d.id).padStart(2, '0')}/${TYPE_CODE[d.type]}-${created.getFullYear()}`,
      typeVn: TYPE_VN[d.type],
      levelVn: LEVEL_VN[d.level],
      deptName: d.departmentId != null ? DEPT_VN[d.departmentId] ?? `Phòng ban #${d.departmentId}` : LEVEL_VN[d.level],
      ownerVn: d.ownerName?.trim() || `Người dùng #${d.ownerId}`,
      daysLeft,
      dispStatus,
      statusVn: st.vn,
      badgeBg: st.bg,
      badgeColor: st.color,
      stripe: st.stripe,
      daysVn: dispStatus === 'PENDING' || dispStatus === 'DRAFT' || dispStatus === 'REJECTED'
        ? st.vn
        : daysText(daysLeft),
      iconBg,
      iconFg,
      expiryVn: fmtDate(toDate(d.expiryDate)),
      updatedVn: fmtDate(toDate(d.updatedAt)),
      issuedVn: fmtDate(created)
    };
  }
}
