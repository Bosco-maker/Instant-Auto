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
                if (file.isFile() && file.getName().startsWith("[ACTIVE]")) {
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
     * Parses the selected autonomous file, including configuration hierarchy and action sequence.
     */
    public void parse(File autoFile) {
        // 1. Parse General Settings (Base Config)
        configEngine.parseConfig(generalSettingsPath);

        // Initialize Registry from settings
        UserActionRegistry.loadSettings(metaActionSettingsPath);

        // 2. Parse both Configs AND Actions from the auto file in one pass
        StringBuilder actionContent = new StringBuilder();
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
                    continue;
                }
                actionContent.append(line).append("\n");
            }
        } catch (IOException e) {
            actionErrors.add("Error reading auto file: " + e.getMessage());
        }

        // --- Post-parsing Validation & Logic ---
        
        // Check for required "Starting" field
        MetaFieldRegistry.ConfigEntry<?> startingEntry = MetaFieldRegistry.getEntry("Starting");
        if (startingEntry == null || startingEntry.value == null || startingEntry.value.toString().trim().isEmpty()) {
            throw new RuntimeException("CRITICAL ERROR: Required 'Starting' field is missing or empty in " + autoFile.getName());
        }

        // Print Title if exists
        MetaFieldRegistry.ConfigEntry<?> titleEntry = MetaFieldRegistry.getEntry("Title");
        if (titleEntry != null && titleEntry.value != null && !titleEntry.value.toString().trim().isEmpty()) {
            System.out.println("Auto Title: " + titleEntry.value);
        }

        // 3. Parse Action strings into actual Action objects
        List<String> actionStrings = UserActionRegistry.splitByTopLevelCommas(actionContent.toString());
        for (int i = 0; i < actionStrings.size(); i++) {
            String actionStr = actionStrings.get(i).trim();
            if (actionStr.isEmpty()) continue;
            Action action = UserActionRegistry.createAction(actionStr);
            if (action != null) {
                actions.add(action);
            } else {
                actionErrors.add("Action " + (i + 1) + ": Unknown Action -> " + actionStr);
            }
        }
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
