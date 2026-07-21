package com.example.purejava.action;

import com.example.instantauto.actions.Action;
import com.example.instantauto.actions.MetaActionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ActionUtils {
    /**
     * Parses a CSV string into an array of doubles.
     * @param params The parameter object (usually a String).
     * @param count The expected number of doubles.
     * @return A double array or null if parsing fails.
     */
    public static double[] asDoubles(Object params, int count) {
        if (params instanceof String) {
            String s = (String) params;
            if (s.isEmpty()) return null;
            String[] parts = s.split(",");
            if (parts.length != count) return null;
            double[] result = new double[count];
            try {
                for (int i = 0; i < count; i++) {
                    result[i] = Double.parseDouble(parts[i].trim());
                }
                return result;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Recursively parses a CSV string of actions into a List of Actions.
     * @param params The parameter object (usually a String).
     * @return A list of actions or null if input is not a string.
     */
    public static List<Action> asActions(Object params) {
        if (params instanceof String) {
            List<String> subActionStrings = MetaActionRegistry.splitByTopLevelCommas((String) params);
            List<Action> actions = new ArrayList<>();
            for (String sub : subActionStrings) {
                Action a = MetaActionRegistry.createAction(sub);
                if (a != null) actions.add(a);
            }
            return actions;
        }
        return null;
    }

    /**
     * Converts an object to a string with consistent formatting for primitives.
     * @param obj The object to convert.
     * @return A formatted string.
     */
    public static String asString(Object obj) {
        if (obj == null) return "";
        if (obj instanceof Double) return String.format(Locale.US, "%.2f", (Double) obj);
        if (obj instanceof Integer) return String.format(Locale.US, "%d", (Integer) obj);
        if (obj instanceof Boolean) return String.format(Locale.US, "%b", (Boolean) obj);
        return obj.toString();
    }
}
