package com.example.instantauto.actions;

import com.example.instantauto.configs.MetaFieldRegistry;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Registry for MetaActions. Handles both primitive "Mini Actions" and
 * user-defined "Big Actions" parsed from text files.
 */
public class UserActionRegistry {
    //Where all your actions are stored
    private static final Map<String, MetaAction> registry = new HashMap<>();
    //Where all your conditional suppliers from java and text files are stored
    private static final Map<String, BooleanSupplier> conditionSuppliers = new HashMap<>();
    private static final List<String> loadErrors = new ArrayList<>();

    // Callback for fusing nested actions (e.g., inside if/else blocks)
    // Set by the consuming module (e.g., TeamCode) which has access to MecanumDrive
    private static Function<List<Action>, List<Action>> actionMerger = actions -> actions;

    public static void register(MetaAction action) {
        registry.put(action.getIdentifier().toUpperCase(), action);
    }

    /**
     * Sets a function to merge/fuse nested actions (e.g., consecutive BuilderActions in if/else blocks).
     * Should be called during initialization by the module that has access to MecanumDrive.
     * @param merger Function that takes a list of actions and returns a fused list
     */
    public static void setActionMerger(Function<List<Action>, List<Action>> merger) {
        actionMerger = merger;
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

        // 1. Handle Variable Assignment (e.g., var = value)
        int eqIndex = line.indexOf("=");
        if (eqIndex != -1) {
            // Ensure '=' is not inside parentheses
            int parenLevel = 0;
            for (int i = 0; i < eqIndex; i++) {
                if (line.charAt(i) == '(') parenLevel++;
                if (line.charAt(i) == ')') parenLevel--;
            }
            if (parenLevel == 0) {
                final String varName = line.substring(0, eqIndex).trim();
                final String valueExpr = line.substring(eqIndex + 1).trim();
                return new Action() {
                    @Override
                    public boolean run() {
                        Object val = parseValue(valueExpr);
                        MetaFieldRegistry.ConfigEntry entry = MetaFieldRegistry.getEntry(varName);
                        if (entry != null) {
                            entry.setValue(val);
                        }
                        return false;
                    }
                };
            }
        }

        // 2. Handle IF/ELSE Logic
        if (line.toLowerCase().startsWith("if")) {
            int firstParen = line.indexOf("(");
            int matchingParen = findMatching(line, firstParen, '(', ')');
            if (firstParen != -1 && matchingParen != -1) {
                final String condition = line.substring(firstParen + 1, matchingParen).trim();

                int firstBrace = line.indexOf("{", matchingParen);
                int matchingBrace = findMatching(line, firstBrace, '{', '}');

                if (firstBrace != -1 && matchingBrace != -1) {
                    final List<Action> trueActions = parseActionsFromBlock(line.substring(firstBrace + 1, matchingBrace).trim());
                    String restStr = line.substring(matchingBrace + 1).trim();
                    if (!restStr.toLowerCase().startsWith("else")) {
                        restStr = ""; // Clear it if it's not an else
                    }
                    final String rest = restStr;
                    return new Action() {
                        private boolean initialized = false;
                        private boolean conditionResult = false;
                        private List<Action> targetActions = null;
                        private int currentIndex = 0;

                        @Override
                        public boolean run() {
                            if (!initialized) {
                                conditionResult = evaluateCondition(condition);
                                if (conditionResult) {
                                    targetActions = trueActions;
                                } else if (!rest.isEmpty()) {
                                    String elseRest = rest.substring(4).trim();
                                    if (elseRest.toLowerCase().startsWith("if")) {
                                        // This is tricky: we might need to wrap the else-if as well
                                        Action elseIfAction = createAction(elseRest);
                                        targetActions = java.util.Collections.singletonList(elseIfAction);
                                    } else {
                                        int elseBrace = rest.indexOf("{");
                                        if (elseBrace != -1) {
                                            int elseMatchingBrace = findMatching(rest, elseBrace, '{', '}');
                                            if (elseMatchingBrace != -1) {
                                                String falseBlock = rest.substring(elseBrace + 1, elseMatchingBrace).trim();
                                                targetActions = parseActionsFromBlock(falseBlock);
                                            }
                                        }
                                    }
                                }
                                initialized = true;
                            }

                            if (targetActions == null || currentIndex >= targetActions.size()) {
                                return false;
                            }

                            Action current = targetActions.get(currentIndex);
                            if (current == null || !current.run()) {
                                currentIndex++;
                            }

                            return currentIndex < targetActions.size();
                        }
                    };
                }
            }
        }

        // 3. Handle Standard Actions (Mini/Big Actions)
        int firstParen = line.indexOf("(");
        int lastParen = line.lastIndexOf(")");
        String name;
        String paramsLine;
        Object paramObject = null;

        if (firstParen != -1 && lastParen > firstParen && line.substring(lastParen + 1).trim().isEmpty()) {
            name = line.substring(0, firstParen).trim();
            paramsLine = line.substring(firstParen + 1, lastParen).trim();
            MetaAction meta = registry.get(name.toUpperCase());

            if (meta == null) return null;

//            // Try to resolve as a variable first
//            MetaFieldRegistry.ConfigEntry<?> variableEntry = MetaFieldRegistry.getEntry(paramsLine);
//            if (variableEntry != null && variableEntry.getValue() != null) {
//                paramObject = variableEntry.getValue();
//                System.out.println("Variable Entry: " + paramObject + " " + name);
//
//            }
            return meta.create(paramsLine);
        } else {
            // Action without parameters
            name = line;
            MetaAction meta = registry.get(name.toUpperCase());
            if (meta == null) return null;
            return meta.create("");
        }
    }

    /**
     * Parses the MetaActionSettings file to define "User Actions" composed of Mini Actions.
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

                // Support both "name={" and "name = {"
                if (currentLine.contains("=") && currentLine.substring(currentLine.indexOf("=") + 1).trim().startsWith("{")) {
                    int definitionStartLine = i + 1;
                    int eqIndex = currentLine.indexOf("=");
                    String actionName = currentLine.substring(0, eqIndex).trim();

                    StringBuilder actionContent = new StringBuilder();
                    int firstBrace = currentLine.indexOf("{", eqIndex);
                    String firstLineContent = currentLine.substring(firstBrace + 1).trim();
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
                            break;
                        }
                    }

                    registry.put(actionName.toUpperCase(), new UserAction(actionName, subActionLines, hasError));
                }
            }
        } catch (IOException e) {
            addError("Error reading MetaActionSettings: " + e.getMessage());
        }
    }

    public static void addError(String error) {
        loadErrors.add(error);
        System.err.println(error);
    }

    public static List<String> getLoadErrors() {
        return loadErrors;
    }

    public static List<String> splitByTopLevelCommas(String content) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenLevel = 0;
        int braceLevel = 0;
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '\"') inQuotes = !inQuotes;
            if (!inQuotes) {
                if (c == '(') parenLevel++;
                if (c == ')') parenLevel--;
                if (c == '{') braceLevel++;
                if (c == '}') braceLevel--;
            }

            if ((c == ',' || c == '\n') && parenLevel == 0 && braceLevel == 0 && !inQuotes) {
                String s = current.toString().trim();
                if (!s.isEmpty()) result.add(s);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            String s = current.toString().trim();
            if (!s.isEmpty()) result.add(s);
        }

        return result;
    }

    private static int findMatching(String str, int start, char open, char close) {
        if (start == -1) return -1;
        int level = 0;
        for (int i = start; i < str.length(); i++) {
            if (str.charAt(i) == open) level++;
            if (str.charAt(i) == close) level--;
            if (level == 0) return i;
        }
        return -1;
    }

    private static List<Action> parseActionsFromBlock(String block) {
        List<Action> actions = new ArrayList<>();
        for (String sub : splitByTopLevelCommas(block)) {
            Action a = createAction(sub);
            if (a != null) actions.add(a);
        }
        // Apply fusion/merging for nested actions (e.g., consecutive BuilderActions)
        return actionMerger.apply(actions);
    }

    public static boolean evaluateCondition(String condition) {
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
        if (entry != null && entry.getValue() instanceof Boolean) {
            return (Boolean) entry.getValue();
        }
        return false;
    }

    private static Object parseValue(String val) {
        val = val.trim();
        if (val.equalsIgnoreCase("true")) return true;
        if (val.equalsIgnoreCase("false")) return false;

        if (val.startsWith("\"") && val.endsWith("\"")) {
            return val.substring(1, val.length() - 1);
        }

        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e1) {
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e2) {
                // Return as string or lookup? For now, just return string
                return val;
            }
        }
    }

    public static List<String> getRegisteredIdentifiers() {
        return new ArrayList<>(registry.keySet());
    }

    public static void clear() {
        registry.clear();
        conditionSuppliers.clear();
        loadErrors.clear();
    }
}
