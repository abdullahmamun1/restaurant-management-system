import { Role } from '../../core/models';

/**
 * A staff account. There is deliberately no password field anywhere in this file — the API never
 * returns one, and the two places a password is *sent* have their own request types below.
 */
export interface User {
  id: number;
  username: string;
  role: Role;
  fullName: string;
  /** False for a retired account: sign-in is refused, history stays intact. */
  enabled: boolean;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  role: Role;
  fullName: string;
}

/** Username is absent on purpose: it identifies the person in the audit trail and cannot change. */
export interface UpdateUserRequest {
  fullName: string;
  role: Role;
}

export interface ResetPasswordRequest {
  password: string;
}
