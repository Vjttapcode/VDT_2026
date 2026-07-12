import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  AuditLog, DashboardStats, DEPT_VN, DocRelation, DocStatus, DocType, DocLevel, DocumentDto, DocVersion, DocView,
  RelationType, daysFromToday, daysText, fmtDate, fmtIso, STATUS_THEME, toDate, TYPE_CODE, TYPE_THEME, TYPE_VN, LEVEL_VN
} from './models';
import { OrgService } from './org.service';

const API = '/api/documents';

export type StatusFilter = 'all' | 'ACTIVE' | 'WARNING' | 'EXPIRED' | 'PENDING' | 'APPROVED';
export type SortKey = 'urgency' | 'name' | 'type';
export type TypeFilter = DocType | 'all';
export type DeptFilter = number | 'all';
export type CompanyFilter = number | 'all';

export interface Toast { id: number; kind: 'ok' | 'err'; text: string; }

/** Ngưỡng cảnh báo "sắp hết hạn" — đồng bộ alert_configs.remind_days max = 30. */
export const ALERT_WINDOW = 30;

@Injectable({ providedIn: 'root' })
export class DocumentStore {
  private http = inject(HttpClient);
  private org = inject(OrgService);
  private toastSeq = 0;

  /* ===== server state ===== */
  readonly docs = signal<DocumentDto[]>([]);
  readonly stats = signal<DashboardStats | null>(null);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);

  /* ===== lịch sử thay đổi (audit log) của văn bản đang mở ===== */
  readonly history = signal<AuditLog[]>([]);
  readonly historyLoading = signal(false);

  /* ===== quan hệ nghiệp vụ (thay thế/bãi bỏ/sửa đổi) của văn bản đang mở ===== */
  readonly relations = signal<DocRelation[]>([]);

  /* ===== lịch sử phiên bản (snapshot mỗi lần ban hành) của văn bản đang mở ===== */
  readonly versions = signal<DocVersion[]>([]);

  /* ===== ui state ===== */
  readonly query = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly sortBy = signal<SortKey>('urgency');
  readonly selectedId = signal<number | null>(null);
  readonly showNotif = signal(false);
  readonly toasts = signal<Toast[]>([]);

  /* ===== bộ lọc nâng cao (tra cứu) ===== */
  readonly typeFilter = signal<TypeFilter>('all');       // loại văn bản
  readonly companyFilter = signal<CompanyFilter>('all'); // công ty
  readonly deptFilter = signal<DeptFilter>('all');       // đơn vị ban hành (trung tâm)
  readonly ownerQuery = signal('');                      // người phụ trách
  readonly expiryFrom = signal('');                      // hết hạn từ (yyyy-mm-dd)
  readonly expiryTo = signal('');                        // hết hạn đến (yyyy-mm-dd)

  /* ===== chọn nhiều để thao tác hàng loạt ===== */
  readonly selectedIds = signal<Set<number>>(new Set());
  readonly bulkBusy = signal(false);

  /* ===== phân trang danh sách ===== */
  readonly page = signal(1);
  readonly pageSize = signal(12);

  constructor() {
    this.org.load(); // tên công ty/trung tâm thật cho hiển thị & bộ lọc (thay map tĩnh DEPT_VN)

    // đổi bộ lọc / sắp xếp → quay về trang 1 để không kẹt ở trang trống
    effect(() => {
      this.query(); this.statusFilter(); this.sortBy();
      this.typeFilter(); this.companyFilter(); this.deptFilter(); this.ownerQuery();
      this.expiryFrom(); this.expiryTo(); this.pageSize();
      this.page.set(1);
    });

    // đổi công ty đang lọc -> bỏ chọn trung tâm cũ (có thể không còn thuộc công ty mới)
    effect(() => {
      this.companyFilter();
      this.deptFilter.set('all');
    });
  }

  /* ===== derived ===== */
  readonly all = computed<DocView[]>(() => this.docs().map(d => this.present(d)));

  readonly filtered = computed<DocView[]>(() => {
    const q = this.query().trim().toLowerCase();
    const st = this.statusFilter();
    const tp = this.typeFilter();
    const cp = this.companyFilter();
    const dp = this.deptFilter();
    const oq = this.ownerQuery().trim().toLowerCase();
    const from = this.expiryFrom();
    const to = this.expiryTo();
    const match = (d: DocView) =>
      // ô tìm toàn cục: số VB, tên, đơn vị, loại, cấp, người phụ trách, nội dung mô tả
      (!q || `${d.title} ${d.code} ${d.deptName} ${d.typeVn} ${d.levelVn} ${d.ownerVn} ${d.description ?? ''}`.toLowerCase().includes(q)) &&
      (st === 'all' || d.dispStatus === st || (st === 'PENDING' && (d.dispStatus === 'DRAFT' || d.dispStatus === 'REJECTED'))) &&
      (tp === 'all' || d.type === tp) &&                                   // loại văn bản
      (cp === 'all' || d.companyId === cp) &&                             // công ty
      (dp === 'all' || d.departmentId === dp) &&                          // đơn vị ban hành (trung tâm)
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

  /* ===== phân trang (paged là tập render trên bảng; filtered giữ nguyên cho CSV/chọn tất cả) ===== */
  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.filtered().length / this.pageSize())));

  /** Trang hiện tại đã kẹp trong [1, pageCount] để luôn hợp lệ khi số kết quả đổi. */
  readonly currentPage = computed(() => Math.min(Math.max(1, this.page()), this.pageCount()));

  readonly paged = computed<DocView[]>(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    return this.filtered().slice(start, start + this.pageSize());
  });

  /** Nhãn "x–y / tổng văn bản" cho thanh phân trang. */
  readonly pageRangeText = computed(() => {
    const total = this.filtered().length;
    if (total === 0) return '0 văn bản';
    const start = (this.currentPage() - 1) * this.pageSize() + 1;
    const end = Math.min(start + this.pageSize() - 1, total);
    return `${start}–${end} / ${total} văn bản`;
  });

  /* ===== tuỳ chọn cho dropdown lọc — chỉ hiện giá trị đang có trong phạm vi role ===== */
  readonly typeOptions = computed<DocType[]>(() => {
    const present = new Set(this.all().map(d => d.type));
    return (['CONTRACT', 'LICENSE', 'CERTIFICATE', 'SR'] as DocType[]).filter(t => present.has(t));
  });

  /** Tên trung tâm ưu tiên lấy từ OrgService (dữ liệu thật); DEPT_VN chỉ là fallback cũ. */
  private deptName(id: number): string {
    return this.org.departments().find(d => d.id === id)?.name ?? DEPT_VN[id] ?? `Phòng ban #${id}`;
  }
  private companyName(id: number): string {
    return this.org.companies().find(c => c.id === id)?.name ?? `Công ty #${id}`;
  }

  /** Danh sách công ty đang xuất hiện trong dữ liệu đang xem — dùng cho dropdown lọc theo công ty. */
  readonly companyOptions = computed<{ id: number; name: string }[]>(() => {
    const ids = [...new Set(this.all().map(d => d.companyId).filter((x): x is number => x != null))];
    return ids.map(id => ({ id, name: this.companyName(id) })).sort((a, b) => a.id - b.id);
  });

  /** Trung tâm — thu hẹp theo công ty đang lọc (nếu có chọn) để cascade đúng. */
  readonly deptOptions = computed<{ id: number; name: string }[]>(() => {
    const cp = this.companyFilter();
    const source = cp === 'all' ? this.all() : this.all().filter(d => d.companyId === cp);
    const ids = [...new Set(source.map(d => d.departmentId).filter((x): x is number => x != null))];
    return ids.map(id => ({ id, name: this.deptName(id) })).sort((a, b) => a.id - b.id);
  });

  /** Có đang bật ít nhất một bộ lọc nâng cao không (để hiện nút Xóa lọc). */
  readonly hasAdvancedFilter = computed(() =>
    this.typeFilter() !== 'all' || this.companyFilter() !== 'all' || this.deptFilter() !== 'all' ||
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
      approved: by('APPROVED'),
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
    req: { title: string; description: string; type: DocType; level: DocLevel; expiryDate: string; effectiveDate: string | null; departmentId: number | null; companyId: number | null },
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

  /** Lấy 1 văn bản theo id (cho form sửa) — luôn gọi thẳng API, không phụ thuộc cache đã load hay chưa. */
  async getOne(id: number): Promise<DocumentDto | null> {
    try {
      return await firstValueFrom(this.http.get<DocumentDto>(`${API}/${id}`));
    } catch (err) {
      this.toast('err', this.errText(err as HttpErrorResponse, 'Không tải được văn bản'));
      return null;
    }
  }

  /** Sửa văn bản DRAFT/REJECTED — trả về true nếu thành công. */
  async update(
    id: number,
    req: { title: string; description: string; type: DocType; level: DocLevel; expiryDate: string; effectiveDate: string | null; departmentId: number | null; companyId: number | null }
  ): Promise<boolean> {
    try {
      await firstValueFrom(this.http.put<DocumentDto>(`${API}/${id}`, req));
      this.afterMutation(id, 'Đã cập nhật văn bản');
      return true;
    } catch (err) {
      this.toast('err', this.errText(err as HttpErrorResponse, 'Không cập nhật được văn bản'));
      return false;
    }
  }

  /** Thay tệp đính kèm khi sửa văn bản (await được, dùng trong luồng lưu của form). */
  async replaceFile(id: number, file: File): Promise<boolean> {
    const form = new FormData();
    form.append('file', file);
    try {
      await firstValueFrom(this.http.post<DocumentDto>(`${API}/${id}/upload`, form));
      this.load();
      return true;
    } catch (err) {
      this.toast('err', this.errText(err as HttpErrorResponse, 'Không cập nhật được tệp đính kèm'));
      return false;
    }
  }

  submit(id: number): void { this.action(id, 'submit', 'Đã gửi duyệt'); }
  approve(id: number): void { this.action(id, 'approve', 'Đã phê duyệt văn bản'); }

  reject(id: number, reason: string): void {
    this.http.post<DocumentDto>(`${API}/${id}/reject`, { reason }).subscribe({
      next: () => this.afterMutation(id, 'Đã từ chối văn bản'),
      error: err => this.toast('err', this.errText(err, 'Không từ chối được'))
    });
  }

  /** Tải lịch sử thay đổi của một văn bản — dùng cho drawer chi tiết. */
  loadHistory(id: number): void {
    this.historyLoading.set(true);
    this.history.set([]);
    this.http.get<AuditLog[]>(`${API}/${id}/history`).subscribe({
      next: h => { this.history.set(h); this.historyLoading.set(false); },
      error: () => { this.historyLoading.set(false); }
    });
  }

  /** Tải danh sách quan hệ nghiệp vụ của một văn bản. */
  loadRelations(id: number): void {
    this.http.get<DocRelation[]>(`${API}/${id}/relations`).subscribe({
      next: r => this.relations.set(r),
      error: () => this.relations.set([])
    });
  }

  /** Tải lịch sử phiên bản (snapshot mỗi lần ban hành) của một văn bản. */
  loadVersions(id: number): void {
    this.http.get<DocVersion[]>(`${API}/${id}/versions`).subscribe({
      next: v => this.versions.set(v),
      error: () => this.versions.set([])
    });
  }

  /** Mở lại văn bản đã ban hành về DRAFT để sửa đổi rồi nộp duyệt lại (tái ban hành). */
  reopen(id: number): void {
    this.http.post<DocumentDto>(`${API}/${id}/reopen`, {}).subscribe({
      next: () => {
        this.afterMutation(id, 'Đã mở lại văn bản — sửa đổi xong hãy Gửi duyệt để tái ban hành');
        this.loadHistory(id);
      },
      error: err => this.toast('err', this.errText(err, 'Không mở lại được văn bản'))
    });
  }

  /** Tạo quan hệ: văn bản {id} {type} cho văn bản {targetId}. */
  relate(id: number, targetId: number, type: RelationType): void {
    const okText: Record<RelationType, string> = {
      REPLACE: 'Đã đánh dấu thay thế văn bản',
      REPEAL: 'Đã đánh dấu bãi bỏ văn bản',
      AMEND: 'Đã đánh dấu sửa đổi/bổ sung văn bản'
    };
    this.http.post<DocumentDto>(`${API}/${id}/relate`, { targetId, type }).subscribe({
      next: () => { this.afterMutation(id, okText[type]); this.loadHistory(id); this.loadRelations(id); },
      error: err => this.toast('err', this.errText(err, 'Không tạo được quan hệ văn bản'))
    });
  }

  /** Đổi ngày hiệu lực của văn bản APPROVED; đặt <= hôm nay = kích hoạt ngay. */
  setEffective(id: number, isoDate: string): void {
    this.http.patch<DocumentDto>(`${API}/${id}/effective-date`, { effectiveDate: isoDate }).subscribe({
      next: () => { this.afterMutation(id, `Đã cập nhật ngày hiệu lực: ${fmtDate(toDate(isoDate))}`); this.loadHistory(id); },
      error: err => this.toast('err', this.errText(err, 'Không cập nhật được ngày hiệu lực'))
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

  /* ===== phân trang ===== */
  setPage(n: number): void { this.page.set(Math.min(Math.max(1, n), this.pageCount())); }
  nextPage(): void { this.setPage(this.currentPage() + 1); }
  prevPage(): void { this.setPage(this.currentPage() - 1); }

  /* ===== chọn nhiều / thao tác hàng loạt ===== */
  toggleSelect(id: number): void {
    this.selectedIds.update(s => {
      const n = new Set(s);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
  }
  isSelected(id: number): boolean { return this.selectedIds().has(id); }
  clearSelection(): void { this.selectedIds.set(new Set()); }
  selectAllFiltered(): void { this.selectedIds.set(new Set(this.filtered().map(d => d.id))); }

  /** Gia hạn hàng loạt tới cùng một ngày. */
  bulkRenew(ids: number[], isoDate: string): void {
    this.bulkAction('bulk/renew', { ids, newExpiryDate: isoDate }, 'gia hạn');
  }
  /** Phê duyệt hàng loạt. */
  bulkApprove(ids: number[]): void {
    this.bulkAction('bulk/approve', { ids }, 'phê duyệt');
  }

  private bulkAction(path: string, body: object, label: string): void {
    this.bulkBusy.set(true);
    this.http.post<{ ok: number; failed: number }>(`${API}/${path}`, body).subscribe({
      next: r => {
        this.bulkBusy.set(false);
        this.clearSelection();
        this.load();
        this.toast(r.failed ? 'err' : 'ok',
          `Đã ${label}: ${r.ok} thành công${r.failed ? `, ${r.failed} thất bại` : ''}`);
      },
      error: err => { this.bulkBusy.set(false); this.toast('err', this.errText(err, `Không ${label} được`)); }
    });
  }

  /** Xuất danh sách văn bản đang lọc ra CSV (UTF-8 BOM để Excel đọc đúng tiếng Việt). */
  exportCsv(): void {
    const rows = this.filtered();
    const headers = ['Mã', 'Tiêu đề', 'Phiên bản', 'Loại', 'Cấp', 'Đơn vị', 'Người phụ trách', 'Ngày ban hành', 'Ngày hiệu lực', 'Ngày hết hạn', 'Trạng thái', 'Số lần gia hạn'];
    const esc = (v: unknown) => {
      const s = String(v ?? '');
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    };
    const lines = [headers.join(',')];
    for (const d of rows) {
      lines.push([d.code, d.title, 'v' + d.version, d.typeVn, d.levelVn, d.deptName, d.ownerVn, d.issuedVn, d.effectiveVn, d.expiryVn, d.statusVn, d.renewalCount]
        .map(esc).join(','));
    }
    const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `van-ban-${fmtIso(new Date())}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    this.toast('ok', `Đã xuất ${rows.length} văn bản ra CSV`);
  }

  /** Xóa toàn bộ bộ lọc (ô tìm, trạng thái, và các bộ lọc nâng cao). */
  clearAllFilters(): void {
    this.query.set('');
    this.statusFilter.set('all');
    this.typeFilter.set('all');
    this.companyFilter.set('all');
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
    if (err.status === 413) return 'Tệp vượt quá dung lượng cho phép (tối đa 10MB)';
    const body = err.error;
    // tránh hiển thị trang lỗi HTML thô (vd trang 413 của nginx)
    if (typeof body === 'string' && body && !body.trimStart().startsWith('<')) return body;
    return body?.message ?? body?.error ?? fallback;
  }

  private present(d: DocumentDto): DocView {
    const daysLeft = daysFromToday(d.expiryDate);
    // ACTIVE/WARNING suy lại theo daysLeft để UI realtime, không chờ cron backend;
    // APPROVED đã tới ngày hiệu lực cũng hiển thị như đang hiệu lực, không chờ job 0h05
    let dispStatus: DocStatus = d.status;
    if (d.status === 'ACTIVE' || d.status === 'WARNING'
        || (d.status === 'APPROVED' && d.effectiveDate != null && daysFromToday(d.effectiveDate) <= 0)) {
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
      deptName: d.departmentId != null
        ? this.deptName(d.departmentId)
        : d.companyId != null ? this.companyName(d.companyId) : LEVEL_VN[d.level],
      ownerVn: d.ownerName?.trim() || `Người dùng #${d.ownerId}`,
      daysLeft,
      dispStatus,
      statusVn: st.vn,
      badgeBg: st.bg,
      badgeColor: st.color,
      stripe: st.stripe,
      daysVn: dispStatus === 'PENDING' || dispStatus === 'DRAFT' || dispStatus === 'REJECTED'
        ? st.vn
        : dispStatus === 'APPROVED' && d.effectiveDate
          ? `Hiệu lực từ ${fmtDate(toDate(d.effectiveDate))}`
          : daysText(daysLeft),
      iconBg,
      iconFg,
      expiryVn: fmtDate(toDate(d.expiryDate)),
      updatedVn: fmtDate(toDate(d.updatedAt)),
      createdVn: fmtDate(created),
      issuedVn: d.issuedDate ? fmtDate(toDate(d.issuedDate)) : '—',
      effectiveVn: d.effectiveDate ? fmtDate(toDate(d.effectiveDate)) : '—'
    };
  }
}
