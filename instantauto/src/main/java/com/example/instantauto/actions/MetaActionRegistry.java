package com.example.instantauto.actions;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for MetaActions. Handles both primitive "Mini Actions" and 
 * user-defined "Big Actions" parsed from text files.
 */
public class MetaActionRegistry {
    private static final Map<String, MetaAction> registry = new HashMap<>();
    private static final List<String> loadErrors = new ArrayList<>();

    static {
        // Register Primitives (Mini Actions)
        register(new MiniAction("GO.TO.POSE2D", params -> {
            try {
                String[] nums = params.split(",");
                if (nums.length != 3) return null;
                // Validation of parameters
                Double.parseDouble(nums[0].trim());
                Double.parseDouble(nums[1].trim());
                Double.parseDouble(nums[2].trim());
                return ActionManager.goToPoseAction(
                    Double.parseDouble(nums[0].trim()),
                    Double.parseDouble(nums[1].trim()),
                    Double.parseDouble(nums[2].trim())
                );
            } catch (Exception e) {
                return null;
            }
        }));
        register(new MiniAction("PRINT", ActionManager.PrintAction::new));
    }

    public static void register(MetaAction action) {
        registry.put(action.getIdentifier().toUpperCase(), action);
    }

    public static Action createAction(String line) {
        line = line.trim();
        if (line.isEmpty()) return null;
        int firstParen = line.indexOf("(");
        String name;
        String params = "";

        if (firstParen != -1 && line.endsWith(")")) {
            name = line.substring(0, firstParen).trim();
            params = line.substring(firstParen + 1, line.lastIndexOf(")")).trim();
        } else {
            name = line;
        }

        MetaAction meta = registry.get(name.toUpperCase());
        if (meta == null) return null;
        
        return meta.create(params);
    }

    /**
     * Parses the MetaActionSettings file to define "Big Actions" composed of Mini Actions.
     */
    public static void loadSettings(String filePath) {
        loadErrors.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            List<String> rawLines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                rawLines.add(line);
            }
            
            for (int i = 0; i < rawLines.size(); i++) {
                String currentLine = rawLines.get(i).trim();
                if (currentLine.isEmpty() || currentLine.startsWith("//") || currentLine.startsWith("#")) continue;

                if (currentLine.contains("={")) {
                    int definitionStartLine = i + 1;
                    String actionName = currentLine.substring(0, currentLine.indexOf("=")).trim();
                    List<String> subActionLines = new ArrayList<>();
                    List<Integer> subActionLineNumbers = new ArrayList<>();
                    
                    String firstLineContent = currentLine.substring(currentLine.indexOf("={") + 2).trim();
                    boolean closed = false;
                    
                    if (firstLineContent.contains("}")) {
                        closed = true;
                        firstLineContent = firstLineContent.substring(0, firstLineContent.indexOf("}")).trim();
                    }
                    
                    if (!firstLineContent.isEmpty()) {
                        for (String sub : splitByTopLevelCommas(firstLineContent)) {
                            subActionLines.add(sub);
                            subActionLineNumbers.add(definitionStartLine);
                        }
                    }

                    if (!closed) {
                        while (++i < rawLines.size()) {
                            String nextLine = rawLines.get(i).trim();
                            boolean lineClosed = false;
                            if (nextLine.contains("}")) {
                                lineClosed = true;
                                nextLine = nextLine.substring(0, nextLine.indexOf("}")).trim();
                            }
                            
                            if (nextLine.endsWith(",")) {
                                nextLine = nextLine.substring(0, nextLine.length() - 1).trim();
                            }
                            
                            if (!nextLine.isEmpty()) {
                                for (String sub : splitByTopLevelCommas(nextLine)) {
                                    subActionLines.add(sub);
                                    subActionLineNumbers.add(i + 1);
                                }
                            }
                            
                            if (lineClosed) {
                                closed = true;
                                break;
                            }
                        }
                    }

                    if (!closed) {
                        addError("MetaActionSettings Line " + definitionStartLine + ": Malformed BigAction '" + actionName + "' (missing '}')");
                    }

                    boolean hasError = false;
                    for (int k = 0; k < subActionLines.size(); k++) {
                        String sub = subActionLines.get(k);
                        if (createAction(sub) == null) {
                            addError("MetaActionSettings Line " + subActionLineNumbers.get(k) + " (in " + actionName + "): Unknown or invalid sub-action -> " + sub);
                            hasError = true;
                        }
                    }
                    register(new BigAction(actionName, subActionLines, hasError));
                }
            }
        } catch (IOException e) {
            addError("Failed to load MetaActionSettings: " + e.getMessage());
        }
    }

    private static void addError(String error) {
        if (!loadErrors.contains(error)) {
            loadErrors.add(error);
        }
    }

    public static List<String> getLoadErrors() {
        return new ArrayList<>(loadErrors);
    }

    private static List<String> splitByTopLevelCommas(String content) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenLevel = 0;
        
        for (char c : content.toCharArray()) {
            if (c == '(') parenLevel++;
            if (c == ')') parenLevel--;
            
            if (c == ',' && parenLevel == 0) {
                String s = current.toString().trim();
                if (!s.isEmpty()) result.add(s);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String s = current.toString().trim();
        if (!s.isEmpty()) result.add(s);
        
        return result;
    }

    public static List<String> getRegisteredIdentifiers() {
        return new ArrayList<>(registry.keySet());
    }
}
