import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class LoginPage {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = 'admin@vdt.com';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);

  readonly demoAccounts = [
    { email: 'admin@vdt.com', label: 'Quản trị Tập đoàn' },
    { email: 'manager.center@vdt.com', label: 'Trưởng TT Phần mềm' },
    { email: 'user1@vdt.com', label: 'Nhân viên TT Phần mềm' }
  ];

  useDemo(email: string): void {
    this.email = email;
    this.password = 'password';
    this.error.set(null);
  }

  submit(): void {
    if (this.loading()) return;
    this.error.set(null);
    this.loading.set(true);
    this.auth.login(this.email.trim(), this.password).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: err => {
        this.loading.set(false);
        this.error.set(err.status === 401 || err.status === 400
          ? 'Email hoặc mật khẩu không đúng'
          : err.status === 0
            ? 'Không kết nối được máy chủ — kiểm tra backend đang chạy'
            : err.error?.message ?? 'Đăng nhập thất bại, thử lại sau');
      }
    });
  }
}
