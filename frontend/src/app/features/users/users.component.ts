import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../core/auth.service';
import {
  ArrowLeft,
  CircleAlert,
  CircleCheck,
  CircleX,
  KeyRound,
  Pencil,
  UserPlus
} from '../../core/icons';
import { Role } from '../../core/models';
import { UsersService } from './users.service';
import { User } from './users.models';

type UserDialog = { mode: 'create' | 'edit'; user?: User };
type Confirm = { title: string; message: string; cta: string; run: () => Promise<void> };

/**
 * Manager-only User Management. Staff accounts are pre-registered here — there is no self-signup,
 * so this screen is the only way anyone gets an account.
 *
 * <p><strong>Retire, do not delete.</strong> There is no delete control because there is no delete
 * endpoint: a user who has taken an order or settled a payment is named on records the system keeps
 * permanently. Disabling revokes sign-in and leaves that history readable and attributable.
 *
 * <p>The two rules that could lock a manager out of their own system — no disabling or demoting
 * yourself, and never removing the last active manager — are enforced by the server. This screen
 * *also* disables the controls that would trip them, so the guard is visible before it is hit
 * rather than only as an error afterwards; the server remains the authority either way.
 */
@Component({
  selector: 'app-users',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule],
  templateUrl: './users.component.html',
  styleUrl: './users.component.css'
})
export class UsersComponent implements OnInit {
  private readonly users = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly icons = {
    back: ArrowLeft,
    add: UserPlus,
    edit: Pencil,
    password: KeyRound,
    enable: CircleCheck,
    disable: CircleX,
    alert: CircleAlert
  };

  readonly roles: Role[] = ['MANAGER', 'WAITER', 'KITCHEN', 'CASHIER'];

  readonly rows = signal<User[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly userDialog = signal<UserDialog | null>(null);
  readonly passwordTarget = signal<User | null>(null);
  readonly confirm = signal<Confirm | null>(null);

  private readonly me = computed(() => this.auth.user()?.username ?? '');

  /** How many managers could still sign in — the count the "last manager" guard turns on. */
  private readonly activeManagers = computed(
    () => this.rows().filter((u) => u.role === 'MANAGER' && u.enabled).length
  );

  readonly userForm = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    role: ['WAITER' as Role, Validators.required],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  readonly passwordForm = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  ngOnInit(): void {
    void this.reload();
  }

  isSelf(user: User): boolean {
    return user.username === this.me();
  }

  /** True when disabling or demoting this user would leave nobody able to administer the system. */
  isLastActiveManager(user: User): boolean {
    return user.role === 'MANAGER' && user.enabled && this.activeManagers() <= 1;
  }

  /** Why a row's disable control is unavailable, or null when it is available. */
  disableBlockedReason(user: User): string | null {
    if (this.isSelf(user)) return 'You cannot disable your own account';
    if (this.isLastActiveManager(user)) return 'This is the only active manager';
    return null;
  }

  async reload(): Promise<void> {
    this.loading.set(true);
    try {
      this.rows.set(await this.users.list());
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.loading.set(false);
    }
  }

  // ---- Create / edit -------------------------------------------------------

  openCreate(): void {
    this.userForm.reset({ username: '', fullName: '', role: 'WAITER', password: '' });
    this.userForm.controls.username.enable();
    this.userForm.controls.password.enable();
    this.userDialog.set({ mode: 'create' });
  }

  openEdit(user: User): void {
    this.userForm.reset({
      username: user.username,
      fullName: user.fullName,
      role: user.role,
      password: ''
    });
    // Neither is editable here: the username anchors the audit trail, and a password change is a
    // separate, deliberate act with its own dialog.
    this.userForm.controls.username.disable();
    this.userForm.controls.password.disable();
    this.userDialog.set({ mode: 'edit', user });
  }

  async saveUser(): Promise<void> {
    const dialog = this.userDialog();
    if (!dialog) return;
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      return;
    }
    const v = this.userForm.getRawValue();

    await this.run(async () => {
      if (dialog.mode === 'create') {
        await this.users.create({
          username: v.username.trim(),
          password: v.password,
          role: v.role,
          fullName: v.fullName.trim()
        });
        this.notice.set(`Account created for ${v.username.trim()}.`);
      } else if (dialog.user) {
        await this.users.update(dialog.user.id, { fullName: v.fullName.trim(), role: v.role });
        this.notice.set(`${dialog.user.username} updated.`);
      }
      this.userDialog.set(null);
      await this.reload();
    });
  }

  // ---- Password ------------------------------------------------------------

  openPassword(user: User): void {
    this.passwordForm.reset({ password: '' });
    this.passwordTarget.set(user);
  }

  async savePassword(): Promise<void> {
    const target = this.passwordTarget();
    if (!target) return;
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    await this.run(async () => {
      await this.users.resetPassword(target.id, this.passwordForm.getRawValue());
      this.passwordTarget.set(null);
      this.notice.set(`Password reset for ${target.username}.`);
    });
  }

  // ---- Enable / disable ----------------------------------------------------

  askDisable(user: User): void {
    this.confirm.set({
      title: 'Retire this account?',
      message:
        `${user.fullName} (${user.username}) will not be able to sign in. Their past orders, `
        + 'tickets and payments stay on record — nothing is deleted, and you can restore access '
        + 'at any time.',
      cta: 'Disable account',
      run: async () => {
        await this.users.disable(user.id);
        this.notice.set(`${user.username} can no longer sign in.`);
        await this.reload();
      }
    });
  }

  async enable(user: User): Promise<void> {
    await this.run(async () => {
      await this.users.enable(user.id);
      this.notice.set(`${user.username} can sign in again.`);
      await this.reload();
    });
  }

  async runConfirm(): Promise<void> {
    const c = this.confirm();
    if (!c) return;
    await this.run(async () => {
      await c.run();
      this.confirm.set(null);
    });
  }

  closeDialogs(): void {
    this.userDialog.set(null);
    this.passwordTarget.set(null);
    this.confirm.set(null);
  }

  private async run(fn: () => Promise<void>): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    this.notice.set(null);
    try {
      await fn();
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.saving.set(false);
    }
  }

  private message(e: unknown): string {
    return (e as { error?: { detail?: string } })?.error?.detail
      ?? 'Something went wrong. Please try again.';
  }
}
