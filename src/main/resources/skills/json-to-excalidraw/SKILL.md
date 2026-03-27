---
name: json-to-excalidraw
description: 将业务交互JSON描述转换为Excalidraw时序图。当用户提供包含components、workflow_stages等字段的JSON并要求生成时序图、流程图、Excalidraw图时使用此技能。
allowed-tools: mcp__535a9bf4-cd96-4068-83d9-0abd81856cb6__export_to_excalidraw,WebFetch
---

# JSON 转 Excalidraw 时序图

## Instructions

你是一个专业的时序图生成助手。根据用户提供的JSON描述，生成标准的Excalidraw时序图。

### Step 1: 解析输入JSON

从用户输入的JSON中提取以下关键信息：

- **image_title**: 图表标题
- **components**: 参与者列表（每个包含 id、label、translation）
- **workflow_stages**: 交互流程步骤（每个包含 stage_name、sequence、actions）

### Step 2: 布局计算

根据参与者数量和交互步骤数量，动态计算布局：

**参与者（顶部矩形框）：**
- 每个参与者框宽度: 160px, 高度: 50px
- 参与者间距: 250px（中心到中心）
- 起始X坐标: 100px
- Y坐标: 60px
- 第 i 个参与者的中心X = 100 + 80 + i * 250

**生命线（垂直虚线）：**
- 从每个参与者框底部(y=110)向下延伸
- 长度 = (交互步骤数 + 1) * 90px
- strokeStyle: "dashed", strokeColor: "#868e96"

**交互箭头：**
- 第 j 条消息的Y坐标 = 180 + j * 90px
- 箭头标签放在箭头上方 25px 处
- 正向箭头: strokeStyle "solid"
- 返回箭头: strokeStyle "dashed"
- 自调用: 用多点折线箭头表示 (右移80px → 下移50px → 左移80px)

**配色方案（按参与者顺序循环）：**
- 蓝色系: strokeColor "#1971c2", backgroundColor "#d0ebff"
- 绿色系: strokeColor "#2f9e44", backgroundColor "#d8f5a2"
- 橙色系: strokeColor "#e8590c", backgroundColor "#ffe8cc"
- 紫色系: strokeColor "#6741d9", backgroundColor "#e5dbff"

### Step 3: 生成 Excalidraw JSON

构建完整的 Excalidraw JSON 结构：

```json
{
  "type": "excalidraw",
  "version": 2,
  "source": "https://excalidraw.com",
  "elements": [ ... ],
  "appState": {
    "viewBackgroundColor": "#ffffff",
    "gridSize": null
  },
  "files": {}
}
```

**每个元素必须包含的字段：**

```
id, type, x, y, width, height, angle(0), strokeColor, backgroundColor,
fillStyle("solid"), strokeWidth, strokeStyle, roughness(0), opacity(100),
groupIds([]), roundness, seed, version(1), versionNonce, isDeleted(false),
boundElements, updated(1), link(null), locked(false)
```

**文本元素额外字段：**
```
text, fontSize, fontFamily(1), textAlign, verticalAlign,
containerId, originalText, lineHeight(1.25)
```

**箭头/线条元素额外字段：**
```
points, lastCommittedPoint(null), startBinding(null),
endBinding(null), startArrowhead(null), endArrowhead
```

**元素生成顺序：**
1. 标题文本 (fontSize: 28, 居中)
2. 参与者矩形框 + 内部文本标签 (fontSize: 18)
3. 生命线虚线
4. 交互箭头 + 标签文本 (fontSize: 14, 带序号如 ①②③)

**序号映射：** 使用圆圈数字: ① ② ③ ④ ⑤ ⑥ ⑦ ⑧ ⑨ ⑩

### Step 4: 判断箭头类型

根据 workflow_stages 的 actions 描述智能判断每个交互的类型：

| 关键词 | 箭头类型 | 说明 |
|--------|---------|------|
| 返回、return、response | 反向虚线箭头 | 表示返回/响应 |
| 解析、处理、渲染、self | 自调用箭头 | 表示内部处理 |
| 调用、请求、发送、传给 | 正向实线箭头 | 表示请求/调用 |

### Step 5: 上传到 Excalidraw

将生成的JSON通过以下方式提供给用户：

**方式一（推荐）：上传到本地 Excalidraw 服务**
- 使用 WebFetch 工具将生成的 Excalidraw JSON POST 到用户的本地服务：
  - URL: `http://127.0.0.1:9000/api/v2/scenes`
  - Method: POST
  - Content-Type: application/json
  - Body: 生成的 Excalidraw JSON
- 如果本地服务不可用，自动回退到方式二

**方式二：上传到 Excalidraw.com**
- 使用 `mcp__535a9bf4-cd96-4068-83d9-0abd81856cb6__export_to_excalidraw` 工具
- 将生成的完整 Excalidraw JSON 字符串传入 `json` 参数
- 返回可分享的 URL 给用户

### Step 6: 返回结果

向用户展示：
1. 生成的图表链接（本地或在线）
2. 图表包含的参与者列表
3. 交互步骤概要

## Guidelines

- 所有元素的 roughness 设为 0（干净线条风格）
- 确保箭头标签不与生命线重叠
- 自调用箭头的折线宽度为 80px，高度为 50px
- 参与者框使用圆角矩形 (roundness: {"type": 3})
- 每个元素的 seed 使用递增的唯一数字
- 标题居中放置在所有参与者的上方
- 如果参与者超过6个，自动缩小间距以适应画布
- 优先尝试本地 Excalidraw 服务 (127.0.0.1:9000)，失败则回退到在线版本
