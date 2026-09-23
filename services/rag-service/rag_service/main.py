from fastapi import FastAPI

from .llm import generate_answer
from .models import HealthResponse, RagRetrieveRequest, RagRetrieveResponse
from .vector_store import retrieve_indexed


app = FastAPI(
    title="猫的角落知识检索服务",
    version="0.2.0",
    description="中文文本检索与基于可信片段的回答生成服务。",
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="UP")


@app.post("/internal/rag/retrieve", response_model=RagRetrieveResponse)
def retrieve_knowledge(request: RagRetrieveRequest) -> RagRetrieveResponse:
    matches = retrieve_indexed(request.userId, request.question, request.documents, request.topK)
    answer = generate_answer(request.question, matches)
    return RagRetrieveResponse(
        matches=matches,
        answer=answer,
        answerGenerated=answer is not None,
    )
