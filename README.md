# 茶悦时光 - AI奶茶订单智能客服 Agent Copilot

基于 **Spring Boot + Spring AI Alibaba** 的奶茶店智能客服系统，集成下单、退单、查询订单功能，支持浏览器插件 Copilot 模式。

## 技术栈

- **后端**: Spring Boot 3.4 + Spring AI Alibaba (DashScope/通义千问)
- **构建**: Maven
- **前端**: HTML + JavaScript + Bootstrap
- **AI模型**: 通义千问 (qwen-plus)
- **存储**: 内存模拟 (ConcurrentHashMap)

## 项目结构

```
demo/
├── pom.xml                              # Maven配置
├── package-extension.sh                 # 浏览器插件打包脚本
├── milktea-copilot-extension.zip        # 打包后的浏览器插件
├── src/main/java/com/milktea/agent/
│   ├── MilkTeaAgentApplication.java     # 启动类
│   ├── model/                           # 数据模型
│   │   ├── Order.java                   # 订单模型
│   │   ├── OrderItem.java              # 订单项模型
│   │   └── OrderStatus.java            # 订单状态枚举
│   ├── repository/
│   │   └── OrderRepository.java        # 内存订单仓库
│   ├── service/
│   │   └── OrderService.java           # 订单业务逻辑
│   ├── controller/
│   │   ├── ChatController.java         # 聊天接口
│   │   └── AdminController.java        # 管理接口
│   ├── config/
│   │   └── AgentConfig.java            # Web配置(CORS等)
│   ├── prompt/
│   │   └── PromptManager.java          # 提示词管理
│   ├── context/
│   │   └── ConversationContextManager.java  # 上下文管理
│   ├── skill/
│   │   ├── OrderSkills.java            # 订单技能(Function Calling)
│   │   └── SkillRegistry.java          # 技能注册中心
│   └── rag/
│       └── RagManager.java             # RAG知识库管理
├── src/main/resources/
│   ├── application.yml                  # 应用配置
│   └── static/
│       └── index.html                   # 内嵌前端页面
└── frontend-extension/                  # 浏览器插件源码
    ├── manifest.json                    # Chrome插件配置
    ├── css/copilot.css                  # Copilot样式
    ├── js/content.js                    # 内容脚本
    ├── js/background.js                 # 后台脚本
    └── icons/                           # 插件图标
```

## 功能模块

### 1. 订单管理 (Skills)
- **下单**: 客户可通过对话自然语言点奶茶，AI自动提取饮品信息并创建订单
- **退单**: 支持取消未制作订单和退已完成订单
- **查询**: 支持按订单号、手机号、姓名查询订单

### 2. 提示词管理
- 内置系统提示词（含完整菜单、交互规则）
- 支持动态增删改提示词模板
- 变量插值渲染

### 3. 上下文管理
- 多会话支持，每个用户独立上下文
- 自动历史消息裁剪（最多50条）
- 会话属性存储
- 管理端可查看/清除会话

### 4. RAG 知识库
- 内置菜单详情、FAQ等知识条目
- 关键词匹配检索
- 自动将相关知识注入对话上下文
- 支持动态增删知识条目

## 快速开始

### 1. 配置 API Key

在阿里云 [DashScope控制台](https://dashscope.console.aliyun.com/) 获取 API Key，然后设置环境变量：

```bash
export DASHSCOPE_API_KEY=sk-your-api-key
```

或修改 `application.yml` 中的 `spring.ai.dashscope.api-key`。

### 2. 启动应用

```bash
cd demo
mvn spring-boot:run
```

### 3. 访问

- **网页版**: 打开浏览器访问 http://localhost:9000
- **API**: POST http://localhost:9000/api/chat/send

### 4. 安装浏览器插件

```bash
# 打包插件
./package-extension.sh
```

安装步骤：
1. 打开 Chrome 浏览器，访问 `chrome://extensions/`
2. 开启右上角「开发者模式」
3. 点击「加载已解压的扩展程序」，选择 `frontend-extension/` 文件夹
4. 在任意页面右下角出现奶茶图标，点击打开 Copilot 侧边栏

## API 接口

### 聊天接口
```
POST /api/chat/send
Body: { "sessionId": "xxx", "message": "我想点一杯珍珠奶茶" }

POST /api/chat/clear
Body: { "sessionId": "xxx" }

GET /api/chat/welcome
```

### 管理接口
```
GET    /api/admin/prompts          # 获取所有提示词
POST   /api/admin/prompts          # 添加/更新提示词
DELETE /api/admin/prompts/{key}    # 删除提示词

GET    /api/admin/contexts         # 查看活跃会话
DELETE /api/admin/contexts/{id}    # 清除会话

GET    /api/admin/skills           # 查看所有技能
POST   /api/admin/skills/{name}/toggle  # 启用/禁用技能

GET    /api/admin/rag              # 获取所有知识
POST   /api/admin/rag              # 添加知识
DELETE /api/admin/rag/{id}         # 删除知识
GET    /api/admin/rag/search?query=xx  # 搜索知识
```
