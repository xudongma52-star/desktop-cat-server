# 知识检索服务

这是“猫的角落”第一版知识库使用的 Python 内部服务。它不调用大模型，负责把正文切成片段，并使用中文字符级 TF-IDF 和余弦相似度返回最相关内容。

## 本地启动

```powershell
cd services/rag-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn rag_service.main:app --host 127.0.0.1 --port 8090
```

健康检查地址为 `http://127.0.0.1:8090/health`，接口文档地址为 `http://127.0.0.1:8090/docs`。

## 测试

检索核心只使用 Python 标准库，不安装 FastAPI 也能运行单元测试：

```powershell
cd services/rag-service
python -m unittest discover -s tests -v
```
