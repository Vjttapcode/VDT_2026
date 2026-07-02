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
      enabled: c.enabled
    });
  }
}
