"""
Fail-fast environment configuration.
Loads repo-root .env. Raises at import time if Azure OpenAI vars are missing.
"""

from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

_REPO_ROOT = Path(__file__).resolve().parents[1]
load_dotenv(_REPO_ROOT / ".env")


def _require(key: str) -> str:
    value = os.getenv(key)
    if not value:
        raise RuntimeError(
            f"Missing required environment variable: {key}\n"
            f"Run .\\infra\\deploy.ps1 to create repo-root .env, or copy .env.example."
        )
    return value


AZURE_OPENAI_API_KEY = _require("AZURE_OPENAI_API_KEY")
AZURE_OPENAI_ENDPOINT = _require("AZURE_OPENAI_ENDPOINT")
AZURE_OPENAI_API_VERSION = os.getenv("AZURE_OPENAI_API_VERSION", "2024-02-01")
AZURE_OPENAI_CHAT_DEPLOYMENT = _require("AZURE_OPENAI_CHAT_DEPLOYMENT")

AI_ENGINE_API_KEY = _require("AI_ENGINE_API_KEY")
API_PORT = int(os.getenv("AI_ENGINE_PORT", "8000"))
