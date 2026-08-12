from fastapi import Depends, FastAPI

from auth import require_api_key
from config import API_PORT
from graph import analyze_exception
from models import AnalyzeExceptionRequest, AnalyzeExceptionResponse

app = FastAPI(
    title="ITETE AI Engine",
    version="0.1.0",
    description="Azure OpenAI analysis for trade exceptions. Confidence is scored in Java.",
)


@app.get("/api/health")
def health():
    return {"status": "UP", "service": "itee-ai-engine"}


@app.post(
    "/api/v1/analyze-exception",
    response_model=AnalyzeExceptionResponse,
    dependencies=[Depends(require_api_key)],
)
def analyze(request: AnalyzeExceptionRequest) -> AnalyzeExceptionResponse:
    return analyze_exception(request)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=API_PORT, reload=True)
