import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CreateUserRequest, ResetPasswordRequest, UpdateUserRequest, User } from './users.models';

/**
 * Client-side gateway to the Manager-only User Management API, mirroring the other feature
 * services.
 *
 * <p>There is no delete method because there is no delete endpoint: staff who have taken orders or
 * settled payments are referenced by records the system keeps permanently, so an account is
 * retired with {@link disable} rather than removed.
 *
 * <p>Enable and disable are separate calls rather than one flag, matching the API — what happened
 * to an account is worth being able to read off the request.
 */
@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly api = inject(ApiService);

  list(): Promise<User[]> {
    return firstValueFrom(this.api.get<User[]>('/users'));
  }

  create(body: CreateUserRequest): Promise<User> {
    return firstValueFrom(this.api.post<User>('/users', body));
  }

  update(id: number, body: UpdateUserRequest): Promise<User> {
    return firstValueFrom(this.api.put<User>(`/users/${id}`, body));
  }

  resetPassword(id: number, body: ResetPasswordRequest): Promise<User> {
    return firstValueFrom(this.api.post<User>(`/users/${id}/password`, body));
  }

  enable(id: number): Promise<User> {
    return firstValueFrom(this.api.post<User>(`/users/${id}/enable`, {}));
  }

  disable(id: number): Promise<User> {
    return firstValueFrom(this.api.post<User>(`/users/${id}/disable`, {}));
  }
}
