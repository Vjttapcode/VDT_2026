import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DocumentStore } from '../../core/document-store.service';
import { AuthService } from '../../core/auth.service';
import { DocLevel, DocType, fmtDate, fmtIso, LEVEL_VN, TYPE_VN } from '../../core/models';

@Component({
  selector: 'app-add-doc-modal',
  imports: [FormsModule],
  templateUrl: './add-doc-modal.html',
  styleUrl: './add-doc-modal.scss'
})
export class AddDocModal {
  readonly store = inject(DocumentStore);
  readonly auth = inject(AuthService);

  title = '';
  description = '';
  type: DocType = 'CONTRACT';
  level: DocLevel = 'CENTER';
  submitNow = true;
  readonly days = signal(90);

  readonly typeOptions = (Object.keys(TYPE_VN) as DocType[]).map(k => ({ value: k, label: TYPE_VN[k] }));
  readonly levelOptions = (Object.keys(LEVEL_VN) as DocLevel[]).map(k => ({ value: k, label: LEVEL_VN[k] }));

  readonly expiryPreview = computed(() => fmtDate(this.expiryDateFor(this.days())));

  close(): void {
    this.store.showAdd.set(false);
  }

  submit(): void {
    const title = this.title.trim();
    if (!title) return;
    this.store.create(
      {
        title,
        description: this.description.trim(),
        type: this.type,
        level: this.level,
        expiryDate: fmtIso(this.expiryDateFor(this.days()))
      },
      this.submitNow
    );
    this.title = '';
    this.description = '';
    this.days.set(90);
  }

  private expiryDateFor(days: number): Date {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d;
  }
}
