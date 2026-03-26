package com.milktea.agent.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * 从 workflow-plan SKILL.md 读取工作流规划，解析出5个待办任务。
 * 模拟"主skill"读取任务规划的过程：Agent读取后产生待办任务列表。
 */
@Service
public class WorkflowPlanService {

    private static final String SKILL_PATH = "skills/workflow-plan/SKILL.md";

    private List<WorkflowStep> workflowSteps;

    @PostConstruct
    public void init() {
        workflowSteps = parseSkillPlan();
    }

    public List<WorkflowStep> getWorkflowSteps() {
        return Collections.unmodifiableList(workflowSteps);
    }

    private List<WorkflowStep> parseSkillPlan() {
        String content = readSkillFile();
        List<WorkflowStep> steps = new ArrayList<>();

        // Parse 5 tasks from the SKILL.md
        // Use predefined structure matching the SKILL.md content
        steps.add(new WorkflowStep("step1",
                "第一步：获取EFM报表数据并计算波动值",
                "read-system-data",
                List.of(new SubTask("step1", "读取系统报表数据并计算波动比例"))));

        steps.add(new WorkflowStep("step2",
                "第二步：获取DCF010102明细数据",
                "read-account-detail-data",
                List.of(new SubTask("step2", "提取波动超20%报表项的明细数据"))));

        steps.add(new WorkflowStep("step3",
                "第三步：数据处理与层级标注",
                "mark-level,mark-detail-level,mark-ic-level,filter-detail-data",
                List.of(
                        new SubTask("step3_1", "3.1 获取股权信息并标注8大分层"),
                        new SubTask("step3_2", "3.2 关联明细数据标注层级"),
                        new SubTask("step3_3", "3.3 更新SR6数据的IC层级"),
                        new SubTask("step3_4", "3.4 过滤并创建处理后数据")
                )));

        steps.add(new WorkflowStep("step4",
                "第四步：校验明细数据合理性",
                "check-detail-data",
                List.of(new SubTask("step4", "明细数据与报表数据一致性校验"))));

        steps.add(new WorkflowStep("step5",
                "第五步：按简化场景汇聚并分析波动",
                "generate-report",
                List.of(new SubTask("step5", "生成波动分析结论"))));

        return steps;
    }

    private String readSkillFile() {
        try (var is = new ClassPathResource(SKILL_PATH).getInputStream();
             var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }

    public record WorkflowStep(String id, String title, String skills, List<SubTask> subTasks) {}
    public record SubTask(String id, String text) {}
}
