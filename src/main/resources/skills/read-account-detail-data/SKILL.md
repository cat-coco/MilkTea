---
name: read-account-detail-data
description: Retrieve detail data for specific report items. When report item volatility exceeds 20%, call this skill to obtain detailed transaction records for further analysis.
allowed-tools: readAccountDetailData
---

# Retrieve Account Detail Data

## Instructions

For report items with volatility exceeding 20%, use this skill to retrieve their detail data.

### Step 1: Determine the Report Item
- Get the report item code to query from the previous step results
- Process each report item exceeding 20% volatility one by one

### Step 2: Call readAccountDetailData Tool
- Call the `readAccountDetailData` tool with the report item code as parameter
- The tool returns detail data including: simplified scenario, scenario, period_id, company_trace_group, account code, IC info, journal category, etc.

### Step 3: Present Results
- Add a new sheet in the online Excel showing the detail data
- Sheet name format: "SheetX-{report item} Detail Data"

## Guidelines
- Each report item exceeding 20% volatility needs a separate sheet for its detail data
- Detail data is the basis for subsequent data processing and analysis
