# 知识检索服务

这是“猫的角落”知识库使用的 Python 内部服务。它先把正文切成片段，使用中文字符级 TF-IDF 和余弦相似度返回最相关内容；配置火山方舟后，再让豆包仅根据这些片段生成带来源标记的回答。模型调用失败时仍然返回检索片段。

## 本地启动

```powershell
cd services/rag-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn rag_service.main:app --host 127.0.0.1 --port 8090
```

需要生成回答时，通过环境变量提供火山方舟配置：

```powershell
$env:ARK_API_KEY = '只保存在本机或服务器，不提交到 Git'
$env:ARK_MODEL = 'doubao-seed-2-1-turbo-260628'
```

健康检查地址为 `http://127.0.0.1:8090/health`，接口文档地址为 `http://127.0.0.1:8090/docs`。

## 测试

检索和模型客户端只使用 Python 标准库，不需要在测试中访问外网：

```powershell
cd services/rag-service
python -m unittest discover -s tests -v
```
