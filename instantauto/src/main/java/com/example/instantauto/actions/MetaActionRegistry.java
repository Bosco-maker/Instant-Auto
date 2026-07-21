package com.example.instantauto.actions;

import com.example.instantauto.configs.MetaFieldRegistry;
import com.example.instantauto.configs.MetaField;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Registry for MetaActions. Handles both primitive "Mini Actions" and 
 * user-defined "Big Actions" parsed from text files.
 */
public class MetaActionRegistry {
    private static final Map<String, MetaAction> registry = new HashMap<>();
    private static final Map<String, BooleanSupplier> conditionSuppliers = new HashMap<>();
    private static final List<String> loadErrors = new ArrayList<>();

    public static void register(MetaAction action) {
        registry.put(action.getIdentifier().toUpperCase(), action);
    }

    /**
     * Registers a boolean supplier that can be used in 'if' conditions.
     * These suppliers are evaluated at runtime and cannot be overwritten by the variable system.
     */
    public static void registerCondition(String name, BooleanSupplier supplier) {
        conditionSuppliers.put(name.toLowerCase(), supplier);
    }

    /**
     * Creates an Action instance from a string line.
     * Supports:
     * - Function calls: PRINT("Hello"), GO.TO.POSE2D(0,0,0)
     * - Assignments: scorePose = pose2d(-72, -67, 0)
     * - Conditionals: if (isBlue) { ... } else { ... }
     */
    public static Action createAction(String line) {
        line = line.trim();
        if (line.isEmpty()) return null;

        // 1. Handle IF/ELSE Logic
        if (line.startsWith("if")) {
            int firstParen = line.indexOf("(");
            int matchingParen = findMatching(line, firstParen, '(', ')');
            if (firstParen != -1 && matchingParen != -1) {
                String condition = line.substring(firstParen + 1, matchingParen).trim();
                
                int firstBrace = line.indexOf("{", matchingParen);
                int matchingBrace = findMatching(line, firstBrace, '{', '}');
                
                if (firstBrace != -1 && matchingBrace != -1) {
                    String trueBlock = line.substring(firstBrace + 1, matchingBrace).trim();
                    final List<Action> trueActions = parseActionsFromBlock(trueBlock);
                    
                    String rest = line.substring(matchingBrace + 1).trim();
                    if (rest.startsWith("else")) {
                        String elseRest = rest.substring(4).trim();
                        if (elseRest.startsWith("if")) {
                            // Recursively handle 'else if'
                            Action elseIfAction = createAction(elseRest);
                            return () -> {
                                if (evaluateCondition(condition)) {
                                    for (Action a : trueActions) if (a != null) a.run();
                                } else {
                                    if (elseIfAction != null) elseIfAction.run();
                                }
                                return true;
                            };
                        } else {
                            // Handle 'else { ... }'
                            int elseBrace = rest.indexOf("{");
                            int elseMatchingBrace = findMatching(rest, elseBrace, '{', '}');
                            if (elseBrace != -1 && elseMatchingBrace != -1) {
                                String falseBlock = rest.substring(elseBrace + 1, elseMatchingBrace).trim();
                                final List<Action> falseActions = parseActionsFromBlock(falseBlock);
                                return () -> {
                                    boolean result = evaluateCondition(condition);
                                    List<Action> branch = result ? trueActions : falseActions;
                                    for (Action a : branch) if (a != null) a.run();
                                    return true;
                                };
                            }
                        }
                    }
                    
                    return () -> {
                        if (evaluateCondition(condition)) {
                            for (Action a : trueActions) if (a != null) a.run();
                        }
                        return true;
                    };
                }
            }
        }

        // 2. Handle Variable Assignment (e.g., var = value)
        int eqIndex = line.indexOf("=");
        if (eqIndex != -1) {
            // Ensure '=' is not inside parentheses
            int parenLevel = 0;
            for (int i = 0; i < eqIndex; i++) {
                if (line.charAt(i) == '(') parenLevel++;
                if (line.charAt(i) == ')') parenLevel--;
            }
            if (parenLevel == 0) {
                String varName = line.substring(0, eqIndex).trim();
                String valueExpr = line.substring(eqIndex + 1).trim();
                return () -> {
                    Object val = parseValue(valueExpr);
                    MetaFieldRegistry.ConfigEntry entry = MetaFieldRegistry.getEntry(varName);
                    if (entry != null) {
                        entry.value = val;
                    }
                    return true;
                };
            }
        }

        // 3. Handle Standard Actions (Mini/Big Actions)
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
            // Action without parameters
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
                    
                    StringBuilder actionContent = new StringBuilder();
                    String firstLineContent = currentLine.substring(currentLine.indexOf("={") + 2).trim();
                    actionContent.append(firstLineContent);
                    
                    int braceLevel = 1;
                    // Count braces in the first line
                    for (char c : firstLineContent.toCharArray()) {
                        if (c == '{') braceLevel++;
                        if (c == '}') braceLevel--;
                    }
                    
                    // If not closed on same line, continue reading
                    if (braceLevel > 0) {
                        while (++i < rawLines.size()) {
                            String nextLine = rawLines.get(i);
                            actionContent.append("\n").append(nextLine);
                            for (char c : nextLine.toCharArray()) {
                                if (c == '{') braceLevel++;
                                if (c == '}') braceLevel--;
                            }
                            if (braceLevel <= 0) break;
                        }
                    }
                    
                    String fullActionStr = actionContent.toString();
                    if (braceLevel > 0) {
                        addError("MetaActionSettings Line " + definitionStartLine + ": Malformed BigAction '" + actionName + "' (missing '}')");
                    }
                    
                    // Remove the last '}'
                    if (fullActionStr.lastIndexOf("}") != -1) {
                        fullActionStr = fullActionStr.substring(0, fullActionStr.lastIndexOf("}")).trim();
                    }
                    
                    List<String> subActionLines = splitByTopLevelCommas(fullActionStr);
                    boolean hasError = false;
                    for (String sub : subActionLines) {
                        if (createAction(sub) == null) {
                            addError("MetaActionSettings (in " + actionName + "): Unknown sub-action -> " + sub);
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

    public static List<String> splitByTopLevelCommas(String content) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenLevel = 0;
        int braceLevel = 0;
        
        char[] chars = content.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '(') parenLevel++;
            else if (c == ')') parenLevel--;
            else if (c == '{') braceLevel++;
            else if (c == '}') braceLevel--;
            
            // Split by comma, newline, or semicolon if at top level
            boolean isSeparator = (c == ',' || c == '\n' || c == ';') && parenLevel == 0 && braceLevel == 0;

            if (isSeparator) {
                // Peek ahead to see if the next non-whitespace is 'else'
                int j = i + 1;
                while (j < chars.length && Character.isWhitespace(chars[j])) j++;
                if (j + 3 < chars.length && 
                    chars[j] == 'e' && chars[j+1] == 'l' && chars[j+2] == 's' && chars[j+3] == 'e') {
                    isSeparator = false;
                }
                
                // Don't split on newline if current statement is 'if' or 'else' and needs a brace
                if (isSeparator && c == '\n') {
                    String trimmed = current.toString().trim();
                    if ((trimmed.startsWith("if") || trimmed.endsWith("else")) && !trimmed.contains("{")) {
                        isSeparator = false;
                    }
                }
            }

            if (isSeparator) {
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

    private static int findMatching(String str, int start, char open, char close) {
        if (start == -1 || start >= str.length()) return -1;
        int level = 0;
        for (int i = start; i < str.length(); i++) {
            if (str.charAt(i) == open) level++;
            else if (str.charAt(i) == close) {
                level--;
                if (level == 0) return i;
            }
        }
        return -1;
    }

    private static List<Action> parseActionsFromBlock(String block) {
        List<Action> actions = new ArrayList<>();
        for (String sub : splitByTopLevelCommas(block)) {
            Action a = createAction(sub);
            if (a != null) actions.add(a);
        }
        return actions;
    }

    private static boolean evaluateCondition(String condition) {
        condition = condition.trim().toLowerCase();
        if (condition.equals("true")) return true;
        if (condition.equals("false")) return false;

        // 1. Check registered BooleanSuppliers (Unchangeable by variables)
        BooleanSupplier supplier = conditionSuppliers.get(condition);
        if (supplier != null) {
            return supplier.getAsBoolean();
        }

        // 2. Check variable system
        MetaFieldRegistry.ConfigEntry<?> entry = MetaFieldRegistry.getEntry(condition);
        if (entry != null && entry.value instanceof Boolean) {
            return (Boolean) entry.value;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Object parseValue(String expr) {
        expr = expr.trim();
        if (expr.equalsIgnoreCase("true")) return true;
        if (expr.equalsIgnoreCase("false")) return false;
        
        try { return Integer.parseInt(expr); } catch (Exception e) {}
        try { return Double.parseDouble(expr); } catch (Exception e) {}
        
        if (expr.startsWith("\"") && expr.endsWith("\"")) {
            return expr.substring(1, expr.length() - 1);
        }
        
        // Handle MetaField types like pose2d(0,0,0)
        for (MetaField<?> type : MetaFieldRegistry.getAllRegisteredMetaFields()) {
            String id = type.getIdentifier();
            if (expr.startsWith(id + "(") && expr.endsWith(")")) {
                String params = expr.substring(id.length() + 1, expr.length() - 1);
                String[] parts = params.split(",");
                Class<?>[] expected = type.getParamTypes();
                if (parts.length == expected.length) {
                    Object[] args = new Object[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        args[i] = parseValue(parts[i].trim());
                    }
                    try {
                        return type.getClass().getConstructor(expected).newInstance(args);
                    } catch (Exception e) {}
                }
            }
        }
        
        MetaFieldRegistry.ConfigEntry<?> entry = MetaFieldRegistry.getEntry(expr);
        if (entry != null) return entry.value;

        return expr;
    }

    public static List<String> getRegisteredIdentifiers() {
        return new ArrayList<>(registry.keySet());
    }
}
