---
name: workflow-plan
description: Main workflow planning skill for cash flow report volatility analysis. Reads the analysis workflow plan and produces 5 todo tasks for execution.
allowed-tools: readSystemData,readAccountDetailData,markLevel,markDetailLevel,markICLevel,filterDetailData,checkDetailData,generateReport
---

# 现金流报表波动合理性检查 - 工作流规划

## 说明

这是主规划技能(Main Skill)，负责读取并规划整个现金流报表波动合理性检查工作流。
读取后会产生5个待办任务(Todo Tasks)，然后由Agent逐步执行每个任务。

## 工作流任务规划

当用户指定实体公司后，Agent将自动规划并执行以下5个步骤：

### 任务1：获取EFM报表数据并计算波动值
- 技能：read-system-data
- 说明：连接EFM报表系统，读取报表数据，计算各报表项波动比例，标注异常波动项（波动>20%）
- 子任务：读取系统报表数据并计算波动比例
- 产出：Sheet1-报表数据

### 任务2：获取DCF010102明细数据
- 技能：read-account-detail-data
- 说明：查询波动超20%报表项的DCF010102明细数据，提取明细记录
- 子任务：提取波动超20%报表项的明细数据
- 产出：Sheet2-DCF010102明细数据

### 任务3：数据处理与层级标注
- 技能：mark-level, mark-detail-level, mark-ic-level, filter-detail-data
- 说明：数据加工处理，包含4个子任务
- 子任务：
  - 3.1 获取股权信息并标注8大分层（mark-level）
  - 3.2 关联明细数据标注层级（mark-detail-level）
  - 3.3 更新SR6数据的IC层级（mark-ic-level）
  - 3.4 过滤并创建处理后数据（filter-detail-data）
- 产出：Sheet3-所有权数据, Sheet4-DCF010102处理后数据

### 任务4：校验明细数据合理性
- 技能：check-detail-data
- 说明：汇总明细数据金额，与报表期间金额进行一致性比对校验
- 子任务：明细数据与报表数据一致性校验
- 产出：校验结论

### 任务5：按简化场景汇聚并分析波动
- 技能：generate-report
- 说明：按简化场景汇聚数据，分析各场景波动原因，生成波动分析结论
- 子任务：生成波动分析结论
- 产出：Sheet5-现金流报表分析结论

## 执行规则

1. Agent读取本规划后，生成5个待办任务发送给前端
2. 按顺序执行每个任务，每个任务完成后发送完成状态
3. 执行过程中将结果写入在线Excel进行数据可视化
4. 第四步完成后需等待用户确认是否继续
5. 全部任务完成后输出完整的分析结论
6. 全程使用中文回复
