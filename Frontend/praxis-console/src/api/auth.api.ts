import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/api';
import { http } from './client';

export async function register(request: RegisterRequest): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>('/auth/register', request);
  return data;
}

export async function login(request: LoginRequest): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>('/auth/login', request);
  return data;
}
