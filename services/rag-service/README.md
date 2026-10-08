# 知识检索服务

这是“猫的角落”知识库使用的 Python 内部服务。Java 服务分页收集当前用户允许检索的记录 ID，Python 再按数据库中的用户、记录版本和检索开关读取正文切片并写入 PostgreSQL pgvector。配置火山方舟后，使用 1024 维 Doubao 语义向量检索，再让豆包仅根据命中的片段生成带来源标记的回答。向量模型不可用或片段尚未全部回填时，使用原有的 1536 维字符特征检索；回答模型失败时仍然返回检索片段。

## 本地启动

```powershell
cd services/rag-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
$env:DATABASE_URL = 'postgresql://postgres:postgres@127.0.0.1:5432/postgres'
python -m uvicorn rag_service.main:app --host 127.0.0.1 --port 8090
```

需要生成回答时，通过环境变量提供火山方舟配置：

```powershell
$env:ARK_API_KEY = '只保存在本机或服务器，不提交到 Git'
$env:ARK_MODEL = 'doubao-seed-2-1-turbo-260628'
$env:ARK_EMBEDDING_MODEL = 'doubao-embedding-vision-251215'
```

健康检查地址为 `http://127.0.0.1:8090/health`，接口文档地址为 `http://127.0.0.1:8090/docs`。

## 对话记忆压缩

Java 通过 `POST /internal/rag/compress` 传入 `previousSummary` 和 `turns`
（按时间正序的 USER / ASSISTANT 消息，携带真实 messageId）。Python 复用
`ARK_API_KEY`、`ARK_MODEL` 和 `ARK_BASE_URL`，temperature 为 0、输出最多 800 token、
关闭深度思考，单次模型等待最多 60 秒，返回 `summary` JSON。无密钥、超时或无效摘要返回 503。

Java 在历史保守估算超过 3000 token 或待压缩旧消息达到 10 轮时按需调用，
保留最近 5 轮原文。摘要覆盖位置由 Java 确定，与新问答在同一短事务中提交；
回答失败不推进位置，压缩失败使用预算内的近期原文继续回答。
V15 为 `knowledge_chat` 增加摘要和覆盖到的消息 ID；Redis 过期后从数据库恢复，
超出最近 50 轮缓存范围的未覆盖消息通过原有分页查询分批补齐。
摘要仅用于理解主题和指代，回答的事实及引用仍来自本轮知识库片段。

## 测试

检索和模型客户端只使用 Python 标准库，不需要在测试中访问外网：

```powershell
cd services/rag-service
python -m unittest discover -s tests -v
```
