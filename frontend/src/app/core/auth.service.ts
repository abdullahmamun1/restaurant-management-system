import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { AuthUser, LoginRequest, LoginResponse, Role } from './models';

const TOKEN_KEY = 'rs.token';

/**
 * Client-side authentication state (FR-01). Holds the JWT and the current user as signals,
 * persists the token in localStorage so a refresh keeps the session, and exposes login /
 * logout / session-restore. The single source of truth for "who is logged in".
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  private readonly _user = signal<AuthUser | null>(null);
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);
  readonly role = computed<Role | null>(() => this._user()?.role ?? null);

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  /** Authenticate and, on success, store the token and current user. */
  async login(credentials: LoginRequest): Promise<void> {
    const res = await firstValueFrom(
      this.api.post<LoginResponse>('/auth/login', credentials)
    );
    localStorage.setItem(TOKEN_KEY, res.token);
    this._user.set({
      id: 0,
      username: res.username,
      role: res.role,
      fullName: res.fullName,
      enabled: true
    });
  }

  /**
   * Re-hydrate the session on app start: if a token exists, fetch the profile. A failure
   * (expired/invalid token) clears the session silently. Called from an app initializer so
   * route guards see a settled state.
   */
  async restore(): Promise<void> {
    if (!this.token) {
      return;
    }
    try {
      const me = await firstValueFrom(this.api.get<AuthUser>('/auth/me'));
      this._user.set(me);
    } catch {
      this.clear();
    }
  }

  /** Clear local session state without navigating (used on 401s and restore failures). */
  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    this._user.set(null);
  }

  /** "Clock out": end the session and return to the login screen. */
  logout(): void {
    this.clear();
    this.router.navigateByUrl('/login');
  }
}
