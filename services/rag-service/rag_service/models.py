from typing import Annotated

from pydantic import BaseModel, Field, field_validator, model_validator


class HealthResponse(BaseModel):
    status: str


class RagDocument(BaseModel):
    documentId: int = Field(gt=0)
    title: str | None = Field(default=None, max_length=120)
    content: str = Field(min_length=1, max_length=30_000)
    version: int = Field(default=0, ge=0)


class RagConversationMessage(BaseModel):
    messageId: int | None = Field(default=None, gt=0)
    role: str
    content: str = Field(min_length=1, max_length=10_000)

    @field_validator("role")
    @classmethod
    def validate_role(cls, value: str) -> str:
        if value not in {"USER", "ASSISTANT"}:
            raise ValueError("role must be USER or ASSISTANT")
        return value


class MemorySummary(BaseModel):
    topic: str = Field(default="", max_length=500)
    userFacts: list[str] = Field(default_factory=list, max_length=30)
    discussedFindings: list[str] = Field(default_factory=list, max_length=30)
    constraints: list[str] = Field(default_factory=list, max_length=20)
    openQuestions: list[str] = Field(default_factory=list, max_length=20)
    entities: list[str] = Field(default_factory=list, max_length=30)


class RagCompressRequest(BaseModel):
    previousSummary: MemorySummary | None = None
    turns: list[RagConversationMessage] = Field(min_length=1, max_length=100)


class RagCompressResponse(BaseModel):
    summary: MemorySummary


class RagRetrieveRequest(BaseModel):
    userId: int = Field(gt=0)
    question: str = Field(min_length=1, max_length=500)
    topK: int = Field(default=3, ge=1, le=5)
    documentIds: list[Annotated[int, Field(gt=0)]] = Field(default_factory=list)
    history: list[RagConversationMessage] = Field(default_factory=list, max_length=100)
    summary: MemorySummary | None = None

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


class CaptureAiItem(BaseModel):
    captureId: str = Field(min_length=36, max_length=36)
    content: str = Field(default="", max_length=20_000)
    imageMimeType: str | None = None
    imageBase64: str | None = None

    @model_validator(mode="after")
    def require_content_or_image(self) -> "CaptureAiItem":
        if not self.content.strip() and not self.imageBase64:
            raise ValueError("capture content or image is required")
        if (self.imageMimeType is None) != (self.imageBase64 is None):
            raise ValueError("image mime type and data must be provided together")
        if self.imageMimeType not in (None, "image/png", "image/jpeg", "image/webp"):
            raise ValueError("unsupported capture image type")
        return self


class CaptureClassificationRequest(BaseModel):
    items: list[CaptureAiItem] = Field(min_length=1)


class CaptureDecision(BaseModel):
    captureId: str = Field(min_length=36, max_length=36)
    target: str

    @field_validator("target")
    @classmethod
    def validate_target(cls, value: str) -> str:
        if value not in {"RECORD", "EMOTION"}:
            raise ValueError("target must be RECORD or EMOTION")
        return value


class CaptureClassificationResponse(BaseModel):
    decisions: list[CaptureDecision]


class CaptureArticleRequest(BaseModel):
    items: list[CaptureAiItem] = Field(min_length=1, max_length=30)


class CaptureArticleResponse(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    content: str = Field(min_length=1, max_length=100_000)
