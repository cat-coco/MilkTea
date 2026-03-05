---
name: cancel-order
description: 取消或退奶茶订单。当客户想要取消订单、退单、退款、不要了时使用此技能。
allowed-tools: cancelOrder
---

# 取消奶茶订单

## Instructions

当客户要取消或退订单时，请按照以下步骤操作：

### Step 1: 获取订单信息
- 询问客户的订单号
- 了解取消/退单的原因

### Step 2: 确认操作
- 确认客户确实要取消该订单
- 告知取消后的影响

### Step 3: 执行取消
- 调用 `cancelOrder` 工具执行取消操作
- 将取消结果反馈给客户

## Guidelines
- 操作前必须与客户确认
- 耐心了解退单原因
- 态度诚恳，尽量挽留但尊重客户意愿
