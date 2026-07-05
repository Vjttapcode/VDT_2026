import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DocumentStore, StatusFilter } from '../../core/document-store.service';
import { DocDrawer } from '../../shared/doc-drawer/doc-drawer';

interface NavItem { path: string; label: string; icon: string; badge?: boolean; roles?: string[]; }

const SECTIONS: Record<string, [string, string]> = {
  dashboard: ['TỔNG QUAN · OVERVIEW', 'Bảng điều khiển văn bản'],
  documents: ['VĂN BẢN · DOCUMENTS', 'Toàn bộ văn bản'],
  'documents/new': ['VĂN BẢN · TẠO MỚI', 'Thêm văn bản mới'],
  alerts: ['CẢNH BÁO · ALERTS', 'Văn bản cần xử lý'],
  calendar: ['LỊCH · CALENDAR', 'Lịch hết hạn'],
  reports: ['NHẬT KÝ · ALERT LOG', 'Nhật ký cảnh báo'],
  profile: ['QUẢN TRỊ · ADMIN', 'Quản trị hệ thống']
};

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, DocDrawer],
  templateUrl: './shell.html',
  styleUrl: './shell.scss'
})
export class Shell implements OnInit {
  readonly store = inject(DocumentStore);
  readonly auth = inject(AuthService);
  readonly router = inject(Router);

  readonly showUserMenu = signal(false);

  readonly nav: NavItem[] = [
    { path: 'dashboard', label: 'Tổng quan', icon: 'grid' },
    { path: 'documents', label: 'Văn bản', icon: 'doc' },
    { path: 'alerts', label: 'Cảnh báo', icon: 'bell', badge: true },
    { path: 'calendar', label: 'Lịch hết hạn', icon: 'calendar' },
    { path: 'reports', label: 'Nhật ký cảnh báo', icon: 'chart' },
    { path: 'profile', label: 'Quản trị', icon: 'user', roles: ['ADMIN'] }
  ];

  readonly visibleNav = computed(() => {
    const role = this.auth.user()?.role ?? '';
    return this.nav.filter(n => !n.roles || n.roles.includes(role));
  });

  readonly currentPath = signal(this.pathOf(this.router.url));

  readonly section = computed(() => SECTIONS[this.currentPath()] ?? SECTIONS['dashboard']);

  readonly kpis = computed(() => {
    const c = this.store.counts();
    const pct = c.total ? Math.round((c.active / c.total) * 100) : 0;
    return [
      { key: 'all' as StatusFilter, vn: 'Tổng văn bản', value: c.total, sub: `${c.pending} chờ xử lý`, subColor: '#3B6BB5', accent: '#E22F29', bg: 'rgba(226,47,41,.10)' },
      { key: 'WARNING' as StatusFilter, vn: 'Sắp hết hạn', value: c.warning, sub: 'trong 30 ngày tới', subColor: '#E0A22E', accent: '#E0A22E', bg: 'rgba(224,162,46,.14)' },
      { key: 'EXPIRED' as StatusFilter, vn: 'Đã hết hạn', value: c.expired, sub: 'cần gia hạn ngay', subColor: '#C62B26', accent: '#C62B26', bg: 'rgba(198,43,38,.12)' },
      { key: 'ACTIVE' as StatusFilter, vn: 'Còn hiệu lực', value: c.active, sub: `${pct}% đúng hạn`, subColor: '#1E8E5A', accent: '#1E8E5A', bg: 'rgba(30,142,90,.13)' }
    ];
  });

  readonly filterLabel = computed(() => {
    const labels: Record<StatusFilter, string> = {
      all: 'từ khóa', WARNING: 'sắp hết hạn', EXPIRED: 'đã hết hạn', ACTIVE: 'còn hiệu lực', PENDING: 'chờ xử lý'
    };
    return labels[this.store.statusFilter()];
  });

  readonly isFiltering = computed(() => this.store.statusFilter() !== 'all' || !!this.store.query().trim());

  private readonly titleSrv = inject(Title);

  ngOnInit(): void {
    this.store.load();
    this.updateTitle();
    this.router.events.subscribe(() => {
      this.currentPath.set(this.pathOf(this.router.url));
      this.updateTitle();
    });
  }

  private updateTitle(): void {
    const section = SECTIONS[this.pathOf(this.router.url)];
    this.titleSrv.setTitle(`${section?.[1] ?? 'VB'} · VB Quản lý văn bản`);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.store.selectedId.set(null);
    this.store.showNotif.set(false);
    this.showUserMenu.set(false);
  }

  goNew(): void {
    this.router.navigate(['/documents/new']);
    this.store.showNotif.set(false);
    this.showUserMenu.set(false);
  }

  /** đóng dropdown khi click ra ngoài vùng chuông / avatar */
  @HostListener('document:click', ['$event'])
  onDocClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.notif-wrap')) this.store.showNotif.set(false);
    if (!target.closest('.user-wrap')) this.showUserMenu.set(false);
  }

  go(path: string): void {
    this.router.navigate(['/', path]);
    this.store.showNotif.set(false);
    this.showUserMenu.set(false);
  }

  kpiClick(key: StatusFilter): void {
    this.store.statusFilter.set(key);
    const p = this.currentPath();
    if (p !== 'dashboard' && p !== 'documents' && p !== 'alerts') this.go('documents');
  }

  clearFilter(): void {
    this.store.statusFilter.set('all');
    this.store.query.set('');
  }

  onSearch(value: string): void {
    this.store.query.set(value);
  }

  toggleNotif(): void {
    this.store.showNotif.update(v => !v);
    this.showUserMenu.set(false);
  }

  toggleUserMenu(): void {
    this.showUserMenu.update(v => !v);
    this.store.showNotif.set(false);
  }

  openDoc(id: number): void {
    this.store.selectedId.set(id);
    this.store.showNotif.set(false);
  }

  private pathOf(url: string): string {
    const segments = url.split('?')[0].split('/').filter(Boolean);
    const joined = segments.join('/');
    // ưu tiên path đầy đủ (vd documents/new), fallback segment đầu
    return SECTIONS[joined] ? joined : segments[0] ?? 'dashboard';
  }
}
