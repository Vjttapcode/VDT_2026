import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocLevel } from './models';

export interface AlertLog {
  id: number;
  documentId: number;
  recipientEmail: string;
  recipientRole: string | null;
  departmentId: number | null;
  alertType: 'WARNING' | 'EXPIRED';
  daysLeft: number | null;
  status: 'SENT' | 'FAILED';
  errorMessage: string | null;
  sentDate: string;
  createdAt: string;
}

export interface AlertConfig {
  id: number;
  documentLevel: DocLevel;
  warningDays: number;
  escalateDays: number;
  remindDays: string;   // mốc nhắc, vd "30,15,7,1"
  enabled: boolean;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);

  readonly logs = signal<AlertLog[]>([]);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);

  loadLogs(departmentId?: number | null, from?: string, to?: string): void {
    let params = new HttpParams();
    if (departmentId != null) params = params.set('departmentId', departmentId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    this.loading.set(true);
    this.loadError.set(null);
    this.http.get<AlertLog[]>('/api/notifications/alert-logs', { params }).subscribe({
      next: logs => {
        this.logs.set(logs);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.loadError.set(err.status === 0
          ? 'Không kết nối được máy chủ thông báo'
          : err.error?.message ?? 'Không tải được nhật ký cảnh báo');
      }
    });
  }

  configs(): Observable<AlertConfig[]> {
    return this.http.get<AlertConfig[]>('/api/notifications/alert-configs');
  }

  updateConfig(c: AlertConfig): Observable<AlertConfig> {
    return this.http.put<AlertConfig>(`/api/notifications/alert-configs/${c.id}`, {
      warningDays: c.warningDays,
      escalateDays: c.escalateDays,
      remindDays: c.remindDays,
      enabled: c.enabled
    });
  }

  /** Admin chạy quét cảnh báo ngay (thay vì chờ cron 8h). Trả số alert đã enqueue. */
  runCheck(): Observable<{ status: string; enqueued: number }> {
    return this.http.post<{ status: string; enqueued: number }>('/api/notifications/admin/run-check', {});
  }

  /** Gửi lại một cảnh báo đã FAILED. */
  resend(logId: number): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`/api/notifications/admin/resend/${logId}`, {});
  }

  /** Gửi email cảnh báo thử (mặc định về hòm thư admin). */
  sendTest(email?: string): Observable<{ status: string; email: string }> {
    return this.http.post<{ status: string; email: string }>('/api/notifications/admin/test', { email: email ?? null });
  }

  /** Thống kê gửi cảnh báo 30 ngày gần nhất (ADMIN). */
  stats(): Observable<{ total: number; sent: number; failed: number; warning: number; expired: number }> {
    return this.http.get<{ total: number; sent: number; failed: number; warning: number; expired: number }>(
      '/api/notifications/admin/stats');
  }
}
