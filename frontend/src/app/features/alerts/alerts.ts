import { Component, computed, inject } from '@angular/core';
import { DocumentStore } from '../../core/document-store.service';

@Component({
  selector: 'app-alerts',
  imports: [],
  templateUrl: './alerts.html',
  styleUrl: './alerts.scss'
})
export class AlertsPage {
  readonly store = inject(DocumentStore);

  readonly list = computed(() => {
    const q = this.store.query().trim().toLowerCase();
    return this.store.alertList().filter(d =>
      !q || `${d.title} ${d.code} ${d.deptName}`.toLowerCase().includes(q));
  });
}
