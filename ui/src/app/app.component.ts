import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AgGridAngular } from 'ag-grid-angular';
import {
  AllCommunityModule,
  ColDef,
  GetRowIdParams,
  GridReadyEvent,
  ModuleRegistry,
  RowClickedEvent,
  RowSelectedEvent,
} from 'ag-grid-community';
import { TradeException } from './models/exception.models';
import { ExceptionDeskService } from './services/exception-desk.service';

ModuleRegistry.registerModules([AllCommunityModule]);

@Component({
  selector: 'app-root',
  imports: [AgGridAngular, FormsModule, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  readonly desk = inject(ExceptionDeskService);

  readonly overrideText = signal('');
  readonly notes = signal('');
  readonly showOverride = signal(false);

  readonly columnDefs: ColDef<TradeException>[] = [
    { field: 'tradeId', headerName: 'Trade', flex: 1.1, minWidth: 120 },
    { field: 'discrepancyType', headerName: 'Type', flex: 1.2, minWidth: 140 },
    { field: 'counterparty', headerName: 'Cpty', flex: 1, minWidth: 110 },
    { field: 'status', headerName: 'Status', flex: 1.1, minWidth: 130 },
    { field: 'severity', headerName: 'Sev', width: 90 },
    {
      field: 'confidenceScore',
      headerName: 'Conf',
      width: 90,
      valueFormatter: (p) =>
        p.value == null ? '—' : Number(p.value).toFixed(2),
    },
    {
      field: 'amount',
      headerName: 'Amount',
      flex: 1,
      minWidth: 110,
      valueFormatter: (p) =>
        p.value == null
          ? ''
          : Number(p.value).toLocaleString(undefined, {
              maximumFractionDigits: 0,
            }),
    },
  ];

  readonly defaultColDef: ColDef = {
    sortable: true,
    resizable: true,
    suppressMovable: true,
  };

  readonly statusLabel = computed(() => {
    switch (this.desk.deskState()) {
      case 'live':
        return 'Live';
      case 'idle':
        return 'Idle';
      default:
        return 'Offline';
    }
  });

  readonly emptyMessage = computed(() => {
    switch (this.desk.deskState()) {
      case 'offline':
        return 'Orchestrator offline — start :8081, then Reconnect.';
      case 'idle':
        return 'Connected — queue empty. Run the producer to ingest exceptions.';
      default:
        return '';
    }
  });

  /** Approve only when AI finished successfully. */
  readonly canApprove = computed(() => {
    const ex = this.desk.selected();
    return (
      ex?.status === 'PENDING_REVIEW' &&
      !!ex.recommendation &&
      !!ex.severity
    );
  });

  /** Reject / Override allowed for ready or failed-AI rows (manual disposition). */
  readonly canRejectOrOverride = computed(() => {
    const s = this.desk.selected()?.status;
    return s === 'PENDING_REVIEW' || s === 'ANALYZING_FAILED';
  });

  readonly aiPanelState = computed(() => {
    const ex = this.desk.selected();
    if (!ex) {
      return 'none' as const;
    }
    if (ex.status === 'PENDING_REVIEW' && ex.recommendation) {
      return 'ready' as const;
    }
    if (ex.status === 'ANALYZING_FAILED') {
      return 'failed' as const;
    }
    if (ex.status === 'ANALYZING' || ex.status === 'NEW') {
      return 'pending' as const;
    }
    return 'none' as const;
  });

  ngOnInit(): void {
    this.desk.start();
  }

  onGridReady(_event: GridReadyEvent<TradeException>): void {
    // reserved for future grid API use
  }

  getRowId = (params: GetRowIdParams<TradeException>): string => params.data!.id;

  onRowClicked(event: RowClickedEvent<TradeException>): void {
    if (event.data?.id) {
      this.desk.select(event.data.id);
      this.showOverride.set(false);
      this.overrideText.set('');
    }
  }

  onRowSelected(event: RowSelectedEvent<TradeException>): void {
    if (event.node.isSelected() && event.data?.id) {
      this.desk.select(event.data.id);
    }
  }

  rowClassRules = {
    'row-selected-match': (params: { data?: TradeException }) =>
      !!params.data && params.data.id === this.desk.selectedId(),
  };

  approve(): void {
    if (!this.canApprove()) {
      return;
    }
    void this.desk.resolve('APPROVE', this.notes() || undefined);
  }

  reject(): void {
    if (!this.canRejectOrOverride()) {
      return;
    }
    void this.desk.resolve('REJECT', this.notes() || undefined);
  }

  openOverride(): void {
    if (!this.canRejectOrOverride()) {
      return;
    }
    this.showOverride.set(true);
    this.overrideText.set(this.desk.selected()?.recommendation ?? '');
  }

  submitOverride(): void {
    if (!this.canRejectOrOverride()) {
      return;
    }
    const text = this.overrideText().trim();
    if (!text) {
      return;
    }
    void this.desk.resolve('OVERRIDE', this.notes() || undefined, text);
    this.showOverride.set(false);
  }

  reconnect(): void {
    this.desk.reconnect();
  }

  refresh(): void {
    void this.desk.refresh();
  }
}
