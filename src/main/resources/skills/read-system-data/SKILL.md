---
name: read-system-data
description: Retrieve EFM report data and calculate volatility values. Called when the user wants to analyze report item volatility, extracting report data, calculating volatility ratio, and marking items with volatility exceeding 20%.
allowed-tools: readSystemData
---

# Retrieve System Report Data

## Instructions

When the user requests financial report volatility analysis, use this skill to retrieve report data.

### Step 1: Confirm Entity
- Confirm which entity company the user wants to analyze
- If the user hasn't specified the entity, ask proactively

### Step 2: Call readSystemData Tool
- Call the `readSystemData` tool with the entity name as parameter
- The tool returns report item data including: report item, previous period, current period, volatility amount, volatility ratio

### Step 3: Present Results
- Display the data in online Excel Sheet1
- Mark items with volatility exceeding 20%
- Summarize the number of items exceeding 20% volatility and the one with the highest volatility

## Guidelines
- The entity company is a required parameter and cannot be skipped
- Volatility ratio = (current period - previous period) / |previous period| * 100%
- Items with volatility exceeding 20% need further detail analysis
