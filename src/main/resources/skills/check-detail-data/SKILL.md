---
name: check-detail-data
description: Verify detail data reasonability. Sum up the rmb_fact_ex_rate_ptd values for all period_ids, and compare the summed value with the current month amount in the system report to check data consistency.
allowed-tools: checkDetailData
---

# Verify Detail Data Reasonability

## Instructions

### Step 1: Call checkDetailData Tool
- Call the `checkDetailData` tool
- The tool sums rmb_fact_ex_rate_ptd values for all period_ids
- Compares the summed result with the system report data

### Step 2: Present Results
- Display verification results: Consistent/Inconsistent
- If inconsistent, display the difference amount
- Analyze possible reasons for the difference

## Guidelines
- Consistency criterion: difference less than 100 million
- If inconsistent, possible reasons include:
  - Some data was removed after filtering
  - period_id may not fully correspond to the P12 period
  - Data source or caliber differences exist
  - Aggregation logic may need further checking
