package com.example.instantauto.actions;

import com.example.instantauto.configs.ConfigParser;
import com.example.instantauto.configs.MetaFieldRegistry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AutoParser {
    private final String generalSettingsPath;
    private final String metaActionSettingsPath;
    private final ConfigParser configEngine;
    private final List<Action> actions = new ArrayList<>();
    private final List<String> actionErrors = new ArrayList<>();

    private String actionContent;

    public AutoParser(String generalSettingsPath, String metaActionSettingsPath) {
        this.generalSettingsPath = generalSettingsPath;
        this.metaActionSettingsPath = metaActionSettingsPath;
        this.configEngine = new ConfigParser();
    }

    /**
     * Scans a directory for autonomous files starting with [ACTIVE].
     */
    public List<File> findActiveAutos(String directoryPath) {
        File folder = new File(directoryPath);
        File[] listOfFiles = folder.listFiles();
        List<File> activeAutos = new ArrayList<>();

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile() && file.getName().startsWith("ACTIVE")) {
                    activeAutos.add(file);
                }
            }
        }

        if (activeAutos.isEmpty()) {
            throw new RuntimeException("CRITICAL ERROR: No active autonomous files found in " + directoryPath);
        }

        return activeAutos;
    }

    /**
     * Phase 1: Parses configuration hierarchy and saves action sequence text.
     */
    public void parseAutoConfig(File autoFile) {
        actions.clear();
        actionErrors.clear();
        configEngine.getLogs().clear();

        // 1. Parse General Settings (Base Config)
        configEngine.parseConfig(generalSettingsPath);

        // 2. Parse both Configs AND Actions from the auto file in one pass
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(autoFile))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = stripComments(line).trim();
                if (line.isEmpty()) continue;

                // Handle top-level configuration in the auto file
                if (line.contains("=")) {
                    configEngine.handleConfigLine(line, lineNumber);
                    sb.append("\n");
                    continue;
                }
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            actionErrors.add("Error reading auto file: " + e.getMessage());
        }
        this.actionContent = sb.toString();

        // --- Post-parsing Validation & Logic ---

        // Check for required "Starting" field
        MetaFieldRegistry.ConfigEntry<?> startingEntry = MetaFieldRegistry.getEntry("Starting");
        if (startingEntry == null || startingEntry.getValue() == null || startingEntry.getValue().toString().trim().isEmpty()) {
            throw new RuntimeException("CRITICAL ERROR: Required 'Starting' field is missing or empty in " + autoFile.getName());
        }
    }

    /**
     * Phase 2: Parses action strings into actual Action objects.
     * This should be called AFTER ActionManager has registered primitives.
     */
    public void parseActions() {
        // Initialize Registry from settings
        UserActionRegistry.loadSettings(metaActionSettingsPath);

        if (actionContent == null) return;

        // 3. Parse Action strings into actual Action objects
        // We now delegate action list creation and merging to a utility that can handle RR actions
        // But since this is core instantauto, we don't want to depend on Roadrunner here.
        // The merging will happen in AutonomousBase using ActionUtils.

        List<String> actionStrings = UserActionRegistry.splitByTopLevelCommas(actionContent);
        for (int i = 0; i < actionStrings.size(); i++) {
            String actionStr = actionStrings.get(i).trim();
            if (actionStr.isEmpty()) continue;
            Action action = UserActionRegistry.createAction(actionStr);
            if (action != null) {
                actions.add(action);
            } else {
                actionErrors.add("Action line " + (i + 1) + ": Unknown Action -> " + actionStr);
            }
        }
    }

    public String getActionContent() {
        return actionContent;
    }

    /**
     * Original combined parse method for compatibility.
     * @deprecated Use parseConfig() then parseActions() to handle dependencies.
     */
    @Deprecated
    public void parse(File autoFile) {
        parseAutoConfig(autoFile);
        parseActions();
    }

    public void parseTeleOpConfig() {
        configEngine.getLogs().clear();
        configEngine.parseConfig(generalSettingsPath);
    }

    public List<Action> getActions() {
        return actions;
    }

    public List<String> getActionErrors() {
        return actionErrors;
    }

    public List<String> getConfigLogs() {
        return configEngine.getLogs();
    }

    public static String stripComments(String line) {
        int commentIndex = line.indexOf("//");
        if (commentIndex != -1) line = line.substring(0, commentIndex);
        commentIndex = line.indexOf("#");
        if (commentIndex != -1) line = line.substring(0, commentIndex);
        return line;
    }
}
