package com.milktea.agent.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Finance report analysis tools for the ReactAgent, using @Tool annotation.
 * Contains 8 skills for the financial report volatility analysis workflow.
 * All data is mock data for demonstration purposes.
 */
@Service
public class FinanceTools {

    /**
     * Skill 1: Read system report data and calculate volatility values
     */
    @Tool(name = "readSystemData", description = "Retrieve EFM report data and calculate volatility. Input: entity company name. Returns report items with previous period, current period, volatility amount and ratio.")
    public String readSystemData(
            @ToolParam(description = "Entity company name, e.g. 'Company A Entity'") String entity) {

        // Mock report data
        String result = String.format("""
                Successfully retrieved %s cash flow report data, results as follows:

                | Report Item | Previous Period (hundred million) | Current Period (hundred million) | Volatility Amount (hundred million) | Volatility Ratio | Volatility > 20%% |
                |-------------|------|------|------|------|------|
                | DCF010101 Operating Revenue | 119.85 | 120.32 | 0.47 | 0.39%% | No |
                | DCF010102 Operating Costs | 85.23 | 86.01 | 0.78 | 0.92%% | No |
                | DCF010201 Investment Income | 12.05 | 18.40 | 6.35 | 52.69%% | Yes |
                | DCF010202 Fair Value Changes | 8.80 | 4.30 | -4.50 | -51.16%% | Yes |
                | DCF010301 Asset Disposal Income | 3.20 | 3.45 | 0.25 | 7.81%% | No |
                | DCF010302 Exchange Gains | 2.10 | 2.35 | 0.25 | 11.90%% | No |
                | DCF010401 Operating Expenses | 15.60 | 16.20 | 0.60 | 3.85%% | No |
                | DCF010402 Admin Expenses | 10.50 | 10.80 | 0.30 | 2.86%% | No |

                Summary: 2 items with volatility exceeding 20%%:
                - DCF010201 has the highest volatility (52.69%%), amount increased by approximately 6.35 hundred million
                - DCF010202 volatility 51.16%%, amount decreased by approximately 4.50 hundred million

                Now proceeding to analyze detail data for items exceeding 20%% volatility one by one.
                """, entity);
        return result;
    }

    /**
     * Skill 2: Read account detail data for a specific report item
     */
    @Tool(name = "readAccountDetailData", description = "Retrieve detail data for a specific report item. Input: report item code. Returns detailed transaction records.")
    public String readAccountDetailData(
            @ToolParam(description = "Report item code, e.g. 'DCF010201'") String account) {

        // Mock detail data
        return String.format("""
                Successfully retrieved %s report item detail data, total 15 records, examples as follows:

                | Simplified Scenario | Scenario | period_id | company_trace_group | account_code_name | IC_info | je_category |
                |------|------|------|------|------|------|------|
                | EMS After-sales Collection | 1111_EMS Adjustment | 2004082004 | 0001 | 11100 (Accounts Receivable) | 0000 | 2141_DIM_APL |
                | Other Product Sales | 2222_Product Sales | 2004082004 | 0002 | 11200 (Other Receivables) | 0011G | 3251_DIM_REV |
                | Deposit Guarantee | 3333_Deposit | 2004082004 | 0003 | 22100 (Other Payables) | 0000 | 4161_DIM_EXP |
                | Other Receivables Reversal | 4444_Reversal | 2004082004 | 0001 | 11200 (Other Receivables) | 0022G | 2141_DIM_APL |
                | Interest | 5555_Interest Income | 2004082004 | 0004 | 66100 (Interest Income) | 0000 | 5171_DIM_FIN |
                | EMS After-sales Repurchase | 6666_EMS Repurchase | 2004082004 | 0001 | 11100 (Accounts Receivable) | 0011G | 2141_DIM_APL |
                | Intercompany Transactions | 7777_IC Transaction | 2004082004 | 0005 | 12100 (Intercompany Receivable) | 0033G | 6181_DIM_IC |
                | Other Transactions | 8888_Other | 2004082004 | 0006 | 99100 (Other) | 0000 | 9999_DIM_OTH |

                Detail data retrieval complete, inserted into Sheet2-%s Detail Data.
                """, account, account);
    }

    /**
     * Skill 3.1: Retrieve equity info and mark 8 major tiers
     */
    @Tool(name = "markLevel", description = "Retrieve equity information and mark 8 major tiers. Returns company ownership data with tier classification.")
    public String markLevel() {

        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        return """
                Successfully retrieved equity information and marked 8 major tiers, examples:

                | Company Code | Company Name | Investee 8-Tier Classification |
                |------|------|------|
                | 0001 | Subsidiary A | 0011G |
                | 0002 | Subsidiary B | 0011G |
                | 0003 | Subsidiary C | 0022G |
                | 0004 | Subsidiary D | 0011G |
                | 0005 | Associated Company E | 0033G |
                | 0006 | Joint Venture F | 0044G |

                Step 3.1 complete! Successfully retrieved equity information and marked 8 major tiers. Data inserted into online Excel Sheet3 Ownership Data.
                """;
    }

    /**
     * Skill 3.2: Associate detail data and mark tiers
     */
    @Tool(name = "markDetailLevel", description = "Associate detail data and mark tiers. Combine detail data with equity data to mark company tier and IC tier.")
    public String markDetailLevel() {

        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        return """
                Step 3.2 complete! Successfully associated detail data with equity data, marked company tier and IC tier.
                - Total 15 detail records processed
                - Successfully matched tier info for 13 records
                - 2 records have no matching equity information (classified as external transaction)
                """;
    }

    /**
     * Skill 3.3: Update SR data IC tier
     */
    @Tool(name = "markICLevel", description = "Update SR data IC tier. Update IC tier information for data containing SR special remarks.")
    public String markICLevel() {

        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        return """
                Step 3.3 complete! Successfully updated IC tier for SR-containing data.
                - Found 4 SR data records
                - IC tier updated for all, added 2 new columns to detail data: Company Tier, IC Tier

                | Record | Company Tier | IC Tier |
                |------|------|------|
                | Record 1 | 0011G | 0022G |
                | Record 2 | 0011G | 0033G |
                | Record 3 | 0022G | 0011G |
                | Record 4 | 0011G | 0044G |
                """;
    }

    /**
     * Skill 3.4: Filter and create processed data
     */
    @Tool(name = "filterDetailData", description = "Filter and create processed data. Filter marked detail data according to rules, remove irrelevant records.")
    public String filterDetailData() {

        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        return """
                Step 3.4 complete! Successfully filtered data and created processed data.
                - Original records: 15
                - Filtered out: 3 records (1 test data, 2 zero-amount records)
                - Remaining records: 12
                - Created Sheet4-DCF010201 Processed Detail Data
                """;
    }

    /**
     * Skill 4: Check detail data reasonability
     */
    @Tool(name = "checkDetailData", description = "Verify detail data reasonability. Sum rmb_fact_ex_rate_ptd values and compare with system report data to check consistency.")
    public String checkDetailData() {

        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        return """
                Step 4 complete! Detail data reasonability verification results:

                Detail data rmb_fact_ex_rate_ptd sum: 15.88 hundred million
                System report DCF010201 current month amount: 18.40 hundred million
                Difference: 2.52 hundred million

                Conclusion: Not consistent (difference >= 100 million)

                Analysis notes:
                - Detail data sum and report data have a 2.52 hundred million difference
                - Difference is significant, possible reasons:
                  1. Some data was removed after filtering
                  2. period_id may not fully correspond to P12 period
                  3. Data source or caliber differences exist
                  4. Aggregation logic may need further checking
                - Please confirm the reason for the difference and resolution approach.
                """;
    }

    /**
     * Skill 5: Generate analysis report
     */
    @Tool(name = "generateReport", description = "Generate analysis report. Aggregate by simplified scenario, analyze volatility for each scenario, output Top 5 highest volatility scenarios and analysis conclusion.")
    public String generateReport() {

        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        return """
                Step 5 complete! Aggregated by simplified scenario and analyzed volatility. Created Sheet: Cash Flow Report Analysis Conclusion

                Top 5 Scenarios by Volatility Amount:
                | Simplified Scenario | Current Month (hundred million) | Previous Month (hundred million) | Volatility Amount (hundred million) | Volatility Ratio (%) |
                |------|------|------|------|------|
                | Other Product Sales | 7.05 | 1.70 | 5.35 | 75.82% |
                | Deposit Guarantee | 6.05 | 5.05 | 1.00 | 16.52% |
                | Other Receivables Reversal | -6.29 | -6.79 | 0.49 | -7.84% |
                | Interest | 2.24 | 2.05 | 0.19 | 8.47% |
                | EMS After-sales Repurchase | 3.48 | 3.79 | -0.32 | -9.17% |

                Main Findings:
                1. Other Product Sales has the highest volatility, growth of 75.8%, increase of 5.35 hundred million
                2. Deposit Guarantee growth of 16.5%, increase of 1.00 hundred million
                3. Interest growth of 8.5%, increase of 0.19 hundred million

                Cash flow report analysis process completed successfully!
                """;
    }
}
