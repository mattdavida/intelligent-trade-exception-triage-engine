export type ExceptionStatus =
  | 'NEW'
  | 'ANALYZING'
  | 'PENDING_REVIEW'
  | 'ANALYZING_FAILED'
  | 'RESOLVED'
  | 'REJECTED'
  | 'OVERRIDDEN';

export type ResolveAction = 'APPROVE' | 'REJECT' | 'OVERRIDE';

export type DeskState = 'offline' | 'idle' | 'live';

export interface ConfidenceFactor {
  code: string;
  weight: number;
  fired: boolean;
}

export interface TradeException {
  id: string;
  tradeId: string;
  counterparty: string;
  discrepancyType: string;
  instrument: string;
  amount: number;
  currency: string;
  side: string;
  detectedAt: string;
  rawDetails: string;
  status: ExceptionStatus;
  severity: string | null;
  recommendation: string | null;
  reasoning: string | null;
  confidenceScore: number | null;
  confidenceRubricVersion: string | null;
  confidenceFactors: ConfidenceFactor[] | null;
  resolveAction: ResolveAction | null;
  resolveNotes: string | null;
  overrideRecommendation: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ResolveRequest {
  action: ResolveAction;
  notes?: string;
  overrideRecommendation?: string;
}

export interface StreamPayload {
  type: string;
  id: string;
  tradeId: string;
  status: ExceptionStatus;
  severity?: string;
  confidenceScore?: string;
  discrepancyType?: string;
}

export const ACTIVE_STATUSES: ExceptionStatus[] = [
  'NEW',
  'ANALYZING',
  'PENDING_REVIEW',
  'ANALYZING_FAILED',
];
