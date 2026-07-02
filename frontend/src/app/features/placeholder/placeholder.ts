import { Component } from '@angular/core';

@Component({
  selector: 'app-placeholder',
  imports: [],
  template: `
    <div class="ph card">
      <div class="icon"><span></span></div>
      <div class="head">Đang phát triển</div>
      <div class="sub">Phân hệ này đang được phát triển trong phiên bản tiếp theo.</div>
    </div>
  `,
  styles: `
    .ph { padding: 60px; text-align: center; }
    .icon {
      width: 54px; height: 54px; border-radius: 14px; background: #F5F2F5;
      margin: 0 auto 16px; display: flex; align-items: center; justify-content: center;
      span { width: 20px; height: 20px; border: 2px solid #C9C3CD; border-radius: 5px; }
    }
    .head { font-size: 16px; font-weight: 700; color: var(--ink); }
    .sub { font-size: 13px; color: var(--muted-2); margin-top: 4px; }
  `
})
export class PlaceholderPage {}
