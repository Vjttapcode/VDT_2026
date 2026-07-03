import { Component, OnInit, computed, inject } from '@angular/core';
import { DocumentStore } from '../../core/document-store.service';
import { DocView, toDate } from '../../core/models';

interface MonthCell {
  index: number;
  label: string;
  docs: { day: number; doc: DocView }[];
  isCurrent: boolean;
}

@Component({
  selector: 'app-calendar',
  imports: [],
  templateUrl: './calendar.html',
  styleUrl: './calendar.scss'
})
export class CalendarPage implements OnInit {
  readonly store = inject(DocumentStore);

  readonly year = new Date().getFullYear();
  private readonly thisMonth = new Date().getMonth();

  readonly months = computed<MonthCell[]>(() => {
    const cells: MonthCell[] = Array.from({ length: 12 }, (_, i) => ({
      index: i,
      label: 'Tháng ' + (i + 1),
      docs: [],
      isCurrent: i === this.thisMonth
    }));
    for (const d of this.store.all()) {
      const dt = toDate(d.expiryDate);
      if (dt.getFullYear() !== this.year) continue;
      cells[dt.getMonth()].docs.push({ day: dt.getDate(), doc: d });
    }
    for (const c of cells) c.docs.sort((a, b) => a.day - b.day);
    return cells;
  });

  /** văn bản hết hạn ngoài năm hiện tại — hiện đếm để không "mất" dữ liệu */
  readonly otherYears = computed(() =>
    this.store.all().filter(d => toDate(d.expiryDate).getFullYear() !== this.year).length
  );

  ngOnInit(): void {
    if (this.store.docs().length === 0) this.store.load();
  }
}
