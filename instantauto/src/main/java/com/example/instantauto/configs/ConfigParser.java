package com.example.instantauto.configs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic engine that handles file I/O, string cleaning, and validation.
 */
public class ConfigParser {
    private final List<String> logs = new ArrayList<>();

    /**
     * Parses the entire configuration file and updates registered fields.
     * Includes validation to report unknown keywords or syntax errors.
     */
    public void parseConfig(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Remove comments starting with // or #
                line = stripComments(line).trim();
                
                if (line.isEmpty()) continue;

                if (line.contains("=")) {
                    handleConfigLine(line, lineNumber);
                } else {
                    logs.add("Line " + lineNumber + ": Unknown format (Expected key=value) -> " + line);
                }
            }
        } catch (IOException e) {
            logs.add("Error reading file: " + e.getMessage());
        }
    }
    // Remove comments starting with // or #
    private String stripComments(String line) {
        int commentIndex = line.indexOf("//");
        if (commentIndex != -1) {
            line = line.substring(0, commentIndex);
        }
        commentIndex = line.indexOf("#");
        if (commentIndex != -1) {
            line = line.substring(0, commentIndex);
        }
        return line;
    }

    public void handleConfigLine(String line, int lineNumber) {
        //if a line has =, it is a variable assignment
        String[] parts = line.split("=", 2);
        String key = parts[0].trim();
        String value = parts[1].trim();

        // Check if the key is already registered internally or by text files
        MetaFieldRegistry.ConfigEntry<?> entry = MetaFieldRegistry.getEntry(key);
        if (entry == null) {
            //if not, add it as local variables
            addLocalVariables(key, value, lineNumber);
            return;
        }
        //Get if the variable is a simple type or a user-defined type
        MetaField<?> typeDef = MetaFieldRegistry.getTypeDefinition(entry.type);
        if (typeDef == null) {
            //if return null, it is a simple type
            Object parsedValue = convertSimpleType(value, entry.type, lineNumber);
            if (parsedValue != null) {
                updateEntryValue(entry, parsedValue);
            }
            return;
        }
        //if not null, it is a user-defined type
        Object parsedValue = parseMetaFieldValue(value, typeDef, lineNumber);
        if (parsedValue != null) {
            updateEntryValue(entry, parsedValue);
        }
    }

    private Object parseMetaFieldValue(String value, MetaField<?> typeDef, int lineNumber) {
        String identifier = typeDef.getIdentifier();
        //User defined variable must have "()" for entering parameters
        if (!value.startsWith(identifier + "(") || !value.endsWith(")")) {
            logs.add("Line " + lineNumber + ": Syntax Error. Expected " + identifier + "(...) but got '" + value + "'");
            return null;
        }

        String params = value.substring(identifier.length() + 1, value.length() - 1);
        String[] paramParts = splitParams(params);
        Class<?>[] expectedTypes = typeDef.getParamTypes();
        //Check if the number of parameters matches the expected types
        if (paramParts.length != expectedTypes.length) {
            logs.add("Line " + lineNumber + ": Parameter count mismatch for " + identifier + ". Expected " + expectedTypes.length + " but got " + paramParts.length);
            return null;
        }
        //Deal with individual parameters
        Object[] args = new Object[expectedTypes.length];
        for (int i = 0; i < expectedTypes.length; i++) {
            args[i] = convertSimpleType(paramParts[i].trim(), expectedTypes[i], lineNumber);
            if (args[i] == null) return null;
        }
        //Return a complete object with the parameters
        try {
            return typeDef.getClass().getConstructor(expectedTypes).newInstance(args);
        } catch (Exception e) {
            logs.add("Line " + lineNumber + ": Failed to instantiate " + typeDef.getClass().getSimpleName() + " -> " + e.getMessage());
            return null;
        }
    }
    //Split the parameters from user-defined type (like pose2d(10, 20, 30) )into individual strings
    private String[] splitParams(String params) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenLevel = 0;
        boolean inQuotes = false;
        for (char c : params.toCharArray()) {
            if (c == '\"') inQuotes = !inQuotes;
            if (c == '(' && !inQuotes) parenLevel++;
            if (c == ')' && !inQuotes) parenLevel--;

            if (c == ',' && !inQuotes && parenLevel == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }
    //Parse strings into simple types like int, double, bool, string
    private Object convertSimpleType(String val, Class<?> type, int lineNumber) {
        try {
            if (type == Double.class || type == double.class) {
                return Double.parseDouble(val);
            } else if (type == Integer.class || type == int.class) {
                return Integer.parseInt(val);
            } else if (type == Boolean.class || type == boolean.class) {
                if (val.equalsIgnoreCase("true")) return true;
                if (val.equalsIgnoreCase("false")) return false;
                logs.add("Line " + lineNumber + ": Type Mismatch. Cannot parse '" + val + "' as boolean");
                return null;
            } else if (type == String.class) {
                if (val.startsWith("\"") && val.endsWith("\"")) {
                    return val.substring(1, val.length() - 1);
                }
                return val;
            }
        } catch (NumberFormatException e) {
            logs.add("Line " + lineNumber + ": Type Mismatch. Cannot parse '" + val + "' as " + type.getSimpleName());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> void updateEntryValue(MetaFieldRegistry.ConfigEntry<T> entry, Object newValue) {
        entry.value = (T) newValue;
    }
    
    private void addLocalVariables(String key, String value, int lineNumber) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            registerLocal(key, Boolean.class, Boolean.parseBoolean(value));
            logs.add("Line " + lineNumber + ": Parsed as local boolean: '" + key + " = " + value);
            return;
        }

        try {
            registerLocal(key, Integer.class, Integer.parseInt(value));
            logs.add("Line " + lineNumber + ": Parsed as local int: '" + key + " = " + value);
            return;
        } catch (NumberFormatException e) {
            // Not an integer
        }

        try {
            registerLocal(key, Double.class, Double.parseDouble(value));
            logs.add("Line " + lineNumber + ": Parsed as local double: '" + key + " = " + value);
            return;
        } catch (NumberFormatException e) {
            // Not a double
        }

        for (MetaField<?> type : MetaFieldRegistry.getAllRegisteredMetaFields()) {
            if (value.contains(type.getIdentifier())) {
                Object metaFieldValue = parseMetaFieldValue(value, type, lineNumber);
                registerLocal(key, type.getClass(), metaFieldValue);
                logs.add("Line " + lineNumber + ": Parsed as local " + type.getIdentifier() + ": '" + key + " = " + value);
                return;
            }
        }
        logs.add("Line " + lineNumber + ": Parsed as local string: '" + key + " = " + value);
        registerLocal(key, String.class, value);
    }

    @SuppressWarnings("unchecked")
    private <T> void registerLocal(String key, Class<T> type, Object value) {
        MetaFieldRegistry.registerField(key, type, (T) value);
    }

    public List<String> getLogs() {
        return logs;
    }
}
