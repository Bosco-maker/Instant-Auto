package com.example.instantauto.actions;

import com.example.instantauto.configs.MetaFieldRegistry;
import com.example.instantauto.configs.types.Pose2d;

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
            // Handle Case 1: Received a Pose2d object (Variable Lookup)
            if (params instanceof Pose2d) {
                Pose2d p = (Pose2d) params;
                return ActionManager.goToPoseAction(p.x, p.y, p.heading);
            }
            
            // Handle Case 2: Received a String (Literal Parameters "x, y, h")
            if (params instanceof String) {
                try {
                    String s = (String) params;
                    if (s.isEmpty()) return null;
                    String[] nums = s.split(",");
                    if (nums.length != 3) return null;
                    return ActionManager.goToPoseAction(
                        Double.parseDouble(nums[0].trim()),
                        Double.parseDouble(nums[1].trim()),
                        Double.parseDouble(nums[2].trim())
                    );
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }));

        register(new MiniAction("PRINT", obj -> {
            if (obj instanceof Double) return new ActionManager.PrintAction((Double) obj);
            if (obj instanceof Integer) return new ActionManager.PrintAction((Integer) obj);
            if (obj instanceof Boolean) return new ActionManager.PrintAction((Boolean) obj);
            return new ActionManager.PrintAction(obj != null ? obj.toString() : "");
        }));
    }

    public static void register(MetaAction action) {
        registry.put(action.getIdentifier().toUpperCase(), action);
    }

    public static Action createAction(String line) {
        line = line.trim();
        if (line.isEmpty()) return null;
        int firstParen = line.indexOf("(");
        String name;
        String paramsLine;
        Object paramObject = null;

        if (firstParen != -1 && line.endsWith(")")) {
            name = line.substring(0, firstParen).trim();
            paramsLine = line.substring(firstParen + 1, line.lastIndexOf(")")).trim();
            MetaAction meta = registry.get(name.toUpperCase());

            if (meta == null) return null;

            // Try to resolve as a variable first
            MetaFieldRegistry.ConfigEntry<?> variableEntry = MetaFieldRegistry.getEntry(paramsLine);
            if (variableEntry != null && variableEntry.value != null) {
                paramObject = variableEntry.value;
                System.out.println("Variable Entry: " + paramObject + " " + name);

            }
            return meta.create(paramObject != null ? paramObject : paramsLine);
        } else {
            name = line;
            MetaAction meta = registry.get(name.toUpperCase());
            if (meta == null) return null;
            return meta.create("");
        }
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
