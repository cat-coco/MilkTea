---
name: generate-report
description: Generate analysis report. Aggregate data by simplified scenario, analyze volatility for each scenario, and output the final analysis conclusion, including Top 5 scenarios with the highest volatility amounts.
allowed-tools: generateReport
---

# Aggregate by Simplified Scenario and Analyze Volatility

## Instructions

### Step 1: Call generateReport Tool
- Call the `generateReport` tool
- The tool aggregates by simplified scenario, calculating current month amount, previous month amount, volatility amount, and volatility ratio for each scenario

### Step 2: Present Results
- Create a new Sheet: "Cash Flow Report Analysis Conclusion"
- Display Top 5 scenarios with the highest volatility amounts
- List table with columns: Simplified Scenario | Current Month Amount (hundred million) | Previous Month Amount (hundred million) | Volatility Amount (hundred million) | Volatility Ratio (%)

### Step 3: Output Main Findings
- Summarize the most important findings
- Highlight the scenarios with the highest volatility
- Mark completion status

## Guidelines
- Sort by volatility amount in descending order
- Pay attention to both absolute amount and ratio
- After completion, indicate that the entire cash flow report analysis process has been successfully executed
