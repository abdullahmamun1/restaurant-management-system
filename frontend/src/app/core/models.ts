export type Role = 'MANAGER' | 'WAITER' | 'KITCHEN' | 'CASHIER';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  username: string;
  role: Role;
  fullName: string;
}

/** The authenticated user's public profile (mirrors the backend UserDto). */
export interface AuthUser {
  id: number;
  username: string;
  role: Role;
  fullName: string;
  enabled: boolean;
}
