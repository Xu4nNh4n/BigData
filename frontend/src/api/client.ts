import type { ApiErrorResponse, RefreshTokenResponse } from '../types';

const BASE_URL = '/api';

interface RequestOptions extends RequestInit {
  skipAuth?: boolean;
}

let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
}

function addRefreshSubscriber(callback: (token: string) => void) {
  refreshSubscribers.push(callback);
}

export async function apiFetch<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const url = `${BASE_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
  const token = localStorage.getItem('access_token');

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (token && !options.skipAuth) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const config: RequestInit = {
    ...options,
    headers,
  };

  try {
    const response = await fetch(url, config);

    // Handle 204 No Content
    if (response.status === 204) {
      return {} as T;
    }

    // Handle 401 Unauthorized (Token expired)
    if (response.status === 401 && !options.skipAuth && !endpoint.includes('/auth/login') && !endpoint.includes('/auth/refresh')) {
      const refreshToken = localStorage.getItem('refresh_token');

      if (!refreshToken) {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('auth_user');
        window.dispatchEvent(new Event('auth:unauthorized'));
        throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      }

      if (!isRefreshing) {
        isRefreshing = true;

        try {
          const refreshRes = await fetch(`${BASE_URL}/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken }),
          });

          if (!refreshRes.ok) {
            throw new Error('Refresh token invalid');
          }

          const refreshData: RefreshTokenResponse = await refreshRes.json();
          localStorage.setItem('access_token', refreshData.accessToken);
          if (refreshData.refreshToken) {
            localStorage.setItem('refresh_token', refreshData.refreshToken);
          }

          isRefreshing = false;
          onTokenRefreshed(refreshData.accessToken);

          // Retry current request with new token
          headers['Authorization'] = `Bearer ${refreshData.accessToken}`;
          const retryResponse = await fetch(url, { ...options, headers });
          if (!retryResponse.ok) {
            const errBody = await retryResponse.json().catch(() => ({ message: retryResponse.statusText }));
            throw new Error(errBody.message || `Lỗi yêu cầu: ${retryResponse.status}`);
          }
          return (await retryResponse.json()) as T;
        } catch {
          isRefreshing = false;
          refreshSubscribers = [];
          localStorage.removeItem('access_token');
          localStorage.removeItem('refresh_token');
          localStorage.removeItem('auth_user');
          window.dispatchEvent(new Event('auth:unauthorized'));
          throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
        }
      }

      // If already refreshing, wait for token
      return new Promise<T>((resolve, reject) => {
        addRefreshSubscriber(async (newToken) => {
          try {
            headers['Authorization'] = `Bearer ${newToken}`;
            const retryRes = await fetch(url, { ...options, headers });
            if (!retryRes.ok) {
              const err = await retryRes.json().catch(() => ({ message: retryRes.statusText }));
              reject(new Error(err.message || 'Lỗi yêu cầu'));
            } else {
              resolve((await retryRes.json()) as T);
            }
          } catch (e) {
            reject(e);
          }
        });
      });
    }

    if (!response.ok) {
      let errorMessage = `Yêu cầu thất bại (${response.status})`;
      try {
        const errorData: ApiErrorResponse = await response.json();
        errorMessage = errorData.message || errorData.error || errorMessage;
      } catch {
        errorMessage = response.statusText || errorMessage;
      }
      throw new Error(errorMessage);
    }

    return (await response.json()) as T;
  } catch (error: unknown) {
    if (error instanceof Error) {
      throw error;
    }
    throw new Error('Lỗi kết nối máy chủ backend');
  }
}

export const api = {
  get: <T>(endpoint: string, options?: RequestOptions) =>
    apiFetch<T>(endpoint, { method: 'GET', ...options }),

  post: <T>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    apiFetch<T>(endpoint, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
      ...options,
    }),

  put: <T>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    apiFetch<T>(endpoint, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
      ...options,
    }),

  delete: <T>(endpoint: string, options?: RequestOptions) =>
    apiFetch<T>(endpoint, { method: 'DELETE', ...options }),
};
