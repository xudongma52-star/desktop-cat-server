from typing import Annotated

from pydantic import BaseModel, Field, field_validator


class HealthResponse(BaseModel):
    status: str


class RagDocument(BaseModel):
    documentId: int = Field(gt=0)
    title: str | None = Field(default=None, max_length=120)
    content: str = Field(min_length=1, max_length=30_000)
    version: int = Field(default=0, ge=0)


class RagConversationMessage(BaseModel):
    role: str
    content: str = Field(min_length=1, max_length=10_000)

    @field_validator("role")
    @classmethod
    def validate_role(cls, value: str) -> str:
        if value not in {"USER", "ASSISTANT"}:
            raise ValueError("role must be USER or ASSISTANT")
        return value


class RagRetrieveRequest(BaseModel):
    userId: int = Field(gt=0)
    question: str = Field(min_length=1, max_length=500)
    topK: int = Field(default=3, ge=1, le=5)
    documentIds: list[Annotated[int, Field(gt=0)]] = Field(default_factory=list)
    history: list[RagConversationMessage] = Field(default_factory=list, max_length=100)

    @field_validator("question")
    @classmethod
    def normalize_question(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("question must not be blank")
        return normalized


class RagMatch(BaseModel):
    documentId: int
    content: str
    score: float


class RagRetrieveResponse(BaseModel):
    matches: list[RagMatch]
    answer: str | None = None
    answerGenerated: bool = False
