package com.milktea.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Loads skill definitions and metadata from the standard skill directory structure.
 * <pre>
 * skills/
 *   skillName/
 *     description.md     - Skill description and documentation
 *     scripts/            - Executable scripts
 *     resources/           - Static resources
 * </pre>
 */
@Component
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    private final SkillManager skillManager;

    public SkillLoader(SkillManager skillManager) {
        this.skillManager = skillManager;
        loadSkillsFromClasspath();
    }

    private void loadSkillsFromClasspath() {
        try {
            Resource skillsDir = new ClassPathResource("skills");
            if (!skillsDir.exists()) {
                log.info("No skills directory found in classpath");
                return;
            }

            File dir = skillsDir.getFile();
            if (!dir.isDirectory()) return;

            for (File skillDir : Objects.requireNonNull(dir.listFiles(File::isDirectory))) {
                loadSkillFromDirectory(skillDir);
            }
        } catch (IOException e) {
            log.debug("Skills directory not accessible as file (may be in JAR): {}", e.getMessage());
        }
    }

    private void loadSkillFromDirectory(File skillDir) {
        String skillId = skillDir.getName();
        File descFile = new File(skillDir, "description.md");

        if (!descFile.exists()) {
            log.warn("Skill {} missing description.md, skipping", skillId);
            return;
        }

        try {
            String content = Files.readString(descFile.toPath(), StandardCharsets.UTF_8);
            String name = extractField(content, "name", skillId);
            String description = extractField(content, "description", "");
            int priority = Integer.parseInt(extractField(content, "priority", "10"));
            List<String> keywords = extractList(content, "keywords");

            SkillDefinition def = new SkillDefinition(
                    skillId, name, description, priority, true, keywords, skillDir.getAbsolutePath());
            skillManager.registerDefinition(def);
            log.info("Loaded skill definition from directory: {} ({})", skillId, name);
        } catch (Exception e) {
            log.error("Failed to load skill from {}", skillDir, e);
        }
    }

    /**
     * Load a skill from an external directory path (for dynamic loading).
     */
    public void loadExternalSkill(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + dirPath);
        }
        loadSkillFromDirectory(dir);
    }

    private String extractField(String mdContent, String field, String defaultValue) {
        for (String line : mdContent.split("\n")) {
            if (line.toLowerCase().startsWith(field + ":")) {
                return line.substring(field.length() + 1).trim();
            }
        }
        return defaultValue;
    }

    private List<String> extractList(String mdContent, String field) {
        for (String line : mdContent.split("\n")) {
            if (line.toLowerCase().startsWith(field + ":")) {
                String value = line.substring(field.length() + 1).trim();
                return Arrays.stream(value.split("[,，]"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        }
        return List.of();
    }
}
