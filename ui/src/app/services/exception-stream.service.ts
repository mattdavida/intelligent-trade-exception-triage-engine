import { Injectable, OnDestroy, signal } from '@angular/core';
import { StreamPayload } from '../models/exception.models';

export const STREAM_URL = 'http://localhost:8081/api/stream';

@Injectable({ providedIn: 'root' })
export class ExceptionStreamService implements OnDestroy {
  readonly connected = signal(false);
  readonly error = signal<string | null>(null);

  private source: EventSource | null = null;
  private onUpdate: ((payload: StreamPayload) => void) | null = null;

  connect(onUpdate: (payload: StreamPayload) => void, url = STREAM_URL): void {
    this.disconnect();
    this.onUpdate = onUpdate;
    this.error.set(null);

    const es = new EventSource(url);
    this.source = es;

    es.onopen = () => {
      this.connected.set(true);
      this.error.set(null);
    };

    es.addEventListener('exception', (evt) => {
      try {
        const data = JSON.parse((evt as MessageEvent).data) as StreamPayload;
        this.onUpdate?.(data);
      } catch {
        this.error.set('Failed to parse SSE exception event');
      }
    });

    es.onerror = () => {
      this.connected.set(false);
      this.error.set('SSE disconnected — is the orchestrator running on :8081?');
    };
  }

  disconnect(): void {
    if (this.source) {
      this.source.close();
      this.source = null;
    }
    this.connected.set(false);
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
