# AgentOps Platform — 运行手册

## 环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 21 | `JAVA_HOME` 指向 JDK 21 安装目录 |
| Maven | 3.8+ | 项目使用 Maven Wrapper，也可用本地 Maven |
| Docker | 24+ | 运行 MySQL、Redis 等基础设施 |
| LLM API Key | — | DeepSeek / OpenAI / 通义千问 任选其一 |

## 项目结构

```
AgentOpsPlatform/
├── agent-api/                  # Spring Boot 启动入口 + REST API
├── agent-runtime/              # 模型调用客户端
├── agent-tools/                # 工具注册中心
├── agent-prompts/              # Prompt 模板管理
├── agent-workflow/             # 工作流引擎
├── agent-memory/               # 记忆存储
├── agent-mcp/                  # MCP 集成（待开发）
├── business-exception-agent/   # 业务异常诊断 Agent
├── docker/                     # Docker Compose + 配置文件
└── docs/                       # 项目文档
```

## 环境准备

### Windows

```powershell
# 切换到 JDK 21（假设 JDK 21 安装在以下路径）
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 验证 Java 版本
java -version
# 应输出：java version "21.0.x"

# 验证 Maven 版本
mvn -version
# 应输出：Java version: 21.0.x, vendor: Oracle Corporation
```

### Linux / macOS

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH=$JAVA_HOME/bin:$PATH

java -version
mvn -version
```

## 基础设施管理

### 启动所有服务

```bash
docker compose -f docker/docker-compose.yml up -d
```

### 查看服务状态

```bash
docker compose -f docker/docker-compose.yml ps
```

### 查看日志

```bash
# 所有服务
docker compose -f docker/docker-compose.yml logs -f

# 单个服务
docker compose -f docker/docker-compose.yml logs -f mysql
```

### 停止服务

```bash
# 停止但不删除数据
docker compose -f docker/docker-compose.yml down

# 停止并删除所有数据（重置环境）
docker compose -f docker/docker-compose.yml down -v
```

### 服务端口一览

| 服务 | 端口 | 默认凭据 | Web 控制台 |
|------|:----:|----------|------------|
| MySQL | 3306 | `agentops` / `agentops123` | — |
| Redis | 6379 | 无密码 | — |
| Prometheus | 9090 | — | http://localhost:9090 |
| Grafana | 3000 | `admin` / `admin` | http://localhost:3000 |

### 连接 MySQL

```bash
# Docker 容器内连接
docker exec -it agentops-mysql mysql -u agentops -pagentops123 agentops

# 宿主机连接（需安装 mysql 客户端）
mysql -h 127.0.0.1 -P 3306 -u agentops -pagentops123 agentops
```

数据库 `agentops` 在容器首次启动时自动创建，包含以下表：
- `memory_entries` — Agent 记忆存储
- `diagnosis_reports` — 诊断报告

## 应用启动

### LLM 配置

应用支持以下环境变量，按优先级从高到低：

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `AGENTOPS_LLM_API_KEY` | — | **必填**，LLM API 密钥 |
| `AGENTOPS_LLM_BASE_URL` | `https://api.deepseek.com/v1` | API 地址 |
| `AGENTOPS_LLM_MODEL` | `deepseek-chat` | 模型名称 |

#### DeepSeek（推荐，国内可直接访问）

```powershell
$env:AGENTOPS_LLM_API_KEY = "sk-xxxxxxxx"
# base-url 和 model 使用默认值即可
```

#### OpenAI

```powershell
$env:AGENTOPS_LLM_API_KEY = "sk-xxxxxxxx"
$env:AGENTOPS_LLM_BASE_URL = "https://api.openai.com/v1"
$env:AGENTOPS_LLM_MODEL = "gpt-4o"
```

#### 阿里云通义千问

```powershell
$env:AGENTOPS_LLM_API_KEY = "sk-xxxxxxxx"
$env:AGENTOPS_LLM_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:AGENTOPS_LLM_MODEL = "qwen-turbo"
```

### 启动方式

#### 方式一：Maven 直接运行（开发推荐）

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:AGENTOPS_LLM_API_KEY = "sk-xxxxxxxx"
mvn spring-boot:run -pl agent-api
```

```bash
# Linux / macOS
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export AGENTOPS_LLM_API_KEY="sk-xxxxxxxx"
./mvnw spring-boot:run -pl agent-api
```

#### 方式二：先打包再运行

```bash
# 打包
mvn package -pl agent-api -DskipTests

# 运行
java -jar agent-api/target/agent-api-0.1.0-SNAPSHOT.jar
```

#### 方式三：IDE 中运行

在 IntelliJ IDEA 中：
1. 打开 `agent-api` 模块
2. 找到 `AgentOpsApplication.java`
3. 右键 → Run 'AgentOpsApplication'
4. 在 Run Configuration 的 Environment Variables 中添加 `AGENTOPS_LLM_API_KEY=sk-xxx`

### 验证启动成功

```bash
curl http://localhost:8080/api/health
```

正常响应：
```json
{
  "status": "UP",
  "version": "0.2.0-SNAPSHOT",
  "prompts": "1 loaded"
}
```

## API 接口

### POST /api/diagnosis — 异常诊断

提交异常堆栈，获取 LLM 生成的诊断报告。

**请求：**
```bash
curl -X POST http://localhost:8080/api/diagnosis \
  -H "Content-Type: application/json" \
  -d '{
    "stackTrace": "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"name\" is null\n\tat com.agentops.service.OrderService.getOrder(OrderService.java:42)\n\tat com.agentops.controller.OrderController.list(OrderController.java:28)\n\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)"
  }'
```

**成功响应：**
```json
{
  "success": true,
  "report": {
    "summary": "OrderService.getOrder 方法中发生空指针异常...",
    "exceptionType": "java.lang.NullPointerException",
    "likelyRootCause": "name 字段为 null，可能由于数据库查询返回了不完整的记录...",
    "relatedModules": [],
    "recommendations": [],
    "confidence": 0.7
  },
  "llmResponse": "完整的 LLM 分析文本..."
}
```

**失败响应：**
```json
{
  "success": false,
  "error": "缺少 stackTrace 字段"
}
```

### GET /api/health — 健康检查

```bash
curl http://localhost:8080/api/health
```

## 开发指南

### 编译

```bash
# 全量编译
mvn compile

# 编译单个模块
mvn compile -pl agent-tools

# 编译并安装到本地仓库
mvn install -DskipTests
```

### 添加新的 Prompt 模板

1. 在 `business-exception-agent/src/main/resources/prompts/` 下创建 `.txt` 文件
2. 使用 `{{变量名}}` 语法定义占位符
3. 重启应用自动加载（通过 `classpath*:prompts/*.txt` 扫描）

示例 `my-prompt.txt`：
```
你是一个 {{role}} 专家。
请分析以下内容：
{{content}}
```

### 注册新工具

```java
@Bean
ToolRegistry toolRegistry() {
    InMemoryToolRegistry registry = new InMemoryToolRegistry();
    
    registry.register(
        new ToolDefinition("my-tool", "我的工具描述", Map.of(
            "type", "object",
            "properties", Map.of("param1", Map.of("type", "string"))
        )),
        args -> ToolResult.success("工具执行结果")
    );
    
    return registry;
}
```

## 故障排查

### 编译报错：找不到 JDK 21

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -version  # 确认 Java version: 21
```

### 启动报错：AGENTOPS_LLM_API_KEY 未设置

```powershell
$env:AGENTOPS_LLM_API_KEY = "sk-xxxxxxxx"
```

### 启动报错：端口 8080 被占用

```yaml
# 在 application.yml 中修改端口
server:
  port: 8081
```

### MySQL 容器启动失败

```bash
# 检查端口冲突
netstat -ano | findstr 3306

# 如果已有本地 MySQL 占用 3306，修改 docker-compose.yml 端口映射
# 将 "3306:3306" 改为 "3307:3306"
```

### LLM 调用返回 401

检查 API Key 是否正确，确认账户余额是否充足。

### LLM 调用超时

DeepSeek 等国内服务响应较快，如使用 OpenAI 可能需要配置代理。修改 `OpenAIModelClient` 中的 `HttpClient` 配置添加代理支持。
