import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ExceptionStatus,
  ResolveRequest,
  TradeException,
} from '../models/exception.models';

export const API_BASE = 'http://localhost:8081';

@Injectable({ providedIn: 'root' })
export class ExceptionApiService {
  private readonly http = inject(HttpClient);

  list(status?: ExceptionStatus): Observable<TradeException[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<TradeException[]>(`${API_BASE}/api/exceptions`, { params });
  }

  get(id: string): Observable<TradeException> {
    return this.http.get<TradeException>(`${API_BASE}/api/exceptions/${id}`);
  }

  resolve(id: string, body: ResolveRequest): Observable<TradeException> {
    return this.http.post<TradeException>(`${API_BASE}/api/exceptions/${id}/resolve`, body);
  }

  health(): Observable<{ status: string; db?: string; aiEngine?: string }> {
    return this.http.get<{ status: string; db?: string; aiEngine?: string }>(
      `${API_BASE}/api/health`,
    );
  }
}
