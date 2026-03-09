---
name: filter-detail-data
description: Filter and create processed data. Filter the marked detail data according to rules, remove irrelevant records, and create the processed dataset.
allowed-tools: filterDetailData
---

# Filter and Create Processed Data

## Instructions

### Step 1: Call filterDetailData Tool
- Call the `filterDetailData` tool
- The tool filters data according to preset rules, removing records that don't meet the criteria

### Step 2: Present Results
- Create a new Sheet: "SheetX-{Report Item} Processed Detail Data"
- Display the filtered data results
- Report how many records were filtered out

## Guidelines
- Filtering rules include: removing test data, removing zero-amount records, etc.
- Processed data is used for subsequent reasonability verification
