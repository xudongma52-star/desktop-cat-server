from fastapi import FastAPI

from .models import HealthResponse, RagRetrieveRequest, RagRetrieveResponse
from .retrieval import retrieve


app = FastAPI(
    title="猫的角落知识检索服务",
    version="0.1.0",
    description="无大模型依赖的中文文本切片与相关度检索服务。",
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="UP")


@app.post("/internal/rag/retrieve", response_model=RagRetrieveResponse)
def retrieve_knowledge(request: RagRetrieveRequest) -> RagRetrieveResponse:
    return RagRetrieveResponse(
        matches=retrieve(request.question, request.documents, request.topK)
    )
