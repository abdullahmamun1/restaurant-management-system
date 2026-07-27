import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { CircleAlert } from '../../core/icons';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Role } from '../../core/models';

interface DemoAccount {
  role: Role;
  label: string;
  username: string;
}

/**
 * Staff login, themed as a printed "guest check" (FR-01). Delegates all auth logic to
 * {@link AuthService}; this component only handles form state and navigation.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, LucideAngularModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  readonly icons = { alert: CircleAlert };

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  /** Seeded dev accounts — one tap fills the form (all share the dev passcode). */
  readonly demos: DemoAccount[] = [
    { role: 'MANAGER', label: 'Manager', username: 'manager' },
    { role: 'WAITER', label: 'Waiter', username: 'waiter' },
    { role: 'KITCHEN', label: 'Kitchen', username: 'kitchen' },
    { role: 'CASHIER', label: 'Cashier', username: 'cashier' }
  ];

  fill(username: string): void {
    this.form.setValue({ username, password: 'password123' });
    this.error.set(null);
  }

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.auth.login(this.form.getRawValue());
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
      await this.router.navigateByUrl(returnUrl);
    } catch (err: unknown) {
      const status = (err as { status?: number })?.status;
      this.error.set(
        status === 401
          ? 'Invalid server ID or passcode.'
          : 'Cannot reach the terminal — check the connection and retry.'
      );
    } finally {
      this.loading.set(false);
    }
  }
}
