from fastapi import FastAPI, HTTPException

from .llm import (
    AiOrganizationError,
    build_retrieval_question,
    classify_captures,
    compress_memory,
    draft_capture_article,
    generate_answer,
)
from .models import (
    CaptureArticleRequest,
    CaptureArticleResponse,
    CaptureClassificationRequest,
    CaptureClassificationResponse,
    HealthResponse,
    RagRetrieveRequest,
    RagRetrieveResponse,
    RagCompressRequest,
    RagCompressResponse,
)
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
    retrieval_question = build_retrieval_question(request.question, request.history, request.summary)
    matches = retrieve_indexed(
        request.userId, retrieval_question, request.documentIds, request.topK
    )
    answer = generate_answer(request.question, matches, request.history, request.summary)
    return RagRetrieveResponse(
        matches=matches,
        answer=answer,
        answerGenerated=answer is not None,
    )


@app.post("/internal/rag/compress", response_model=RagCompressResponse)
def compress_knowledge_memory(request: RagCompressRequest) -> RagCompressResponse:
    try:
        return RagCompressResponse(summary=compress_memory(request))
    except (AiOrganizationError, ValueError) as exception:
        raise HTTPException(status_code=503, detail="Memory compression is unavailable.") from exception


@app.post("/internal/captures/classify", response_model=CaptureClassificationResponse)
def classify_capture_items(
    request: CaptureClassificationRequest,
) -> CaptureClassificationResponse:
    try:
        return CaptureClassificationResponse.model_validate(
            classify_captures(request.items)
        )
    except AiOrganizationError as exception:
        raise HTTPException(status_code=503, detail="AI classification is unavailable.") from exception


@app.post("/internal/captures/article", response_model=CaptureArticleResponse)
def create_capture_article_draft(
    request: CaptureArticleRequest,
) -> CaptureArticleResponse:
    try:
        return CaptureArticleResponse.model_validate(
            draft_capture_article(request.items)
        )
    except AiOrganizationError as exception:
        raise HTTPException(status_code=503, detail="AI article generation is unavailable.") from exception
