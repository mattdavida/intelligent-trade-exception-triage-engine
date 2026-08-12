"""Pydantic contracts for /api/v1/analyze-exception — no confidence fields."""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class Side(str, Enum):
    BUY = "BUY"
    SELL = "SELL"


class Severity(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


class AnalyzeExceptionRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    id: Optional[UUID] = None
    tradeId: str
    counterparty: str
    discrepancyType: str
    instrument: str
    amount: Decimal
    currency: str
    side: str
    detectedAt: datetime
    rawDetails: str


class AnalyzeExceptionResponse(BaseModel):
    """LLM qualitative output only. Confidence is scored in Java."""

    severity: Severity
    recommendation: str = Field(min_length=1, max_length=2000)
    reasoning: str = Field(min_length=1, max_length=4000)
