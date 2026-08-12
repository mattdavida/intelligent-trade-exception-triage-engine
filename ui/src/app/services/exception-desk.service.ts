import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  ACTIVE_STATUSES,
  DeskState,
  ResolveAction,
  TradeException,
} from '../models/exception.models';
import { ExceptionApiService } from './exception-api.service';
import { ExceptionStreamService } from './exception-stream.service';

@Injectable({ providedIn: 'root' })
export class ExceptionDeskService {
  private readonly api = inject(ExceptionApiService);
  private readonly stream = inject(ExceptionStreamService);

  readonly exceptions = signal<TradeException[]>([]);
  readonly selectedId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly actionBusy = signal(false);
  readonly lastError = signal<string | null>(null);

  readonly connected = this.stream.connected;
  readonly streamError = this.stream.error;

  readonly selected = computed(() => {
    const id = this.selectedId();
    if (!id) {
      return null;
    }
    return this.exceptions().find((e) => e.id === id) ?? null;
  });

  readonly queue = computed(() =>
    this.exceptions()
      .filter((e) => ACTIVE_STATUSES.includes(e.status))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
  );

  readonly deskState = computed<DeskState>(() => {
    if (!this.connected()) {
      return 'offline';
    }
    return this.queue().length > 0 ? 'live' : 'idle';
  });

  start(): void {
    void this.refresh();
    this.stream.connect((payload) => {
      void this.upsertFromStream(payload.id);
    });
  }

  reconnect(): void {
    this.stream.disconnect();
    this.start();
  }

  select(id: string): void {
    this.selectedId.set(id);
  }

  async refresh(): Promise<void> {
    this.loading.set(true);
    this.lastError.set(null);
    try {
      const rows = await firstValueFrom(this.api.list());
      this.exceptions.set(rows);
      const selected = this.selectedId();
      if (selected && !rows.some((r) => r.id === selected)) {
        this.selectedId.set(null);
      }
      if (!this.selectedId() && this.queue().length > 0) {
        this.selectedId.set(this.queue()[0].id);
      }
    } catch {
      this.lastError.set('Failed to load exceptions from orchestrator :8081');
    } finally {
      this.loading.set(false);
    }
  }

  async resolve(
    action: ResolveAction,
    notes?: string,
    overrideRecommendation?: string,
  ): Promise<void> {
    const current = this.selected();
    if (!current) {
      return;
    }
    this.actionBusy.set(true);
    this.lastError.set(null);
    try {
      const updated = await firstValueFrom(
        this.api.resolve(current.id, { action, notes, overrideRecommendation }),
      );
      this.exceptions.update((list) =>
        list.map((e) => (e.id === updated.id ? updated : e)),
      );
      const next = this.queue().find((e) => e.id !== updated.id);
      this.selectedId.set(next?.id ?? null);
    } catch {
      this.lastError.set(
        'Resolve failed — Approve needs PENDING_REVIEW + AI proposal; Reject/Override allowed on failed AI too.',
      );
    } finally {
      this.actionBusy.set(false);
    }
  }

  private async upsertFromStream(id: string): Promise<void> {
    try {
      const row = await firstValueFrom(this.api.get(id));
      this.exceptions.update((list) => {
        const idx = list.findIndex((e) => e.id === id);
        if (idx === -1) {
          return [row, ...list];
        }
        const copy = [...list];
        copy[idx] = row;
        return copy;
      });
      if (!this.selectedId() && ACTIVE_STATUSES.includes(row.status)) {
        this.selectedId.set(row.id);
      }
    } catch {
      // Keep last good snapshot; refresh can recover
    }
  }
}
