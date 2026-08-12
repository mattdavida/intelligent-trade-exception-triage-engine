from fastapi import Header, HTTPException, status

from config import AI_ENGINE_API_KEY


def require_api_key(x_api_key: str | None = Header(default=None, alias="X-API-Key")) -> None:
    if not x_api_key or x_api_key != AI_ENGINE_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-API-Key",
        )
