package com.example.purejava.action;

import com.example.instantauto.actions.Action;
import com.example.instantauto.actions.MetaActionRegistry;
import com.example.instantauto.actions.MiniAction;
import com.example.purejava.configs.Pose2d;

import java.util.Locale;

public class ActionManager {
    public static void init() {
        // Register Primitives (Mini Actions)
        MetaActionRegistry.register(new MiniAction("GO.TO.POSE2D", params -> {
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

        MetaActionRegistry.register(new MiniAction("PRINT", obj -> {
            if (obj instanceof Double) return new ActionManager.PrintAction((Double) obj);
            if (obj instanceof Integer) return new ActionManager.PrintAction((Integer) obj);
            if (obj instanceof Boolean) return new ActionManager.PrintAction((Boolean) obj);
            return new ActionManager.PrintAction(obj != null ? obj.toString() : "");
        }));
    }
    public static class PrintAction implements Action {
        String message;

        public PrintAction(String message) {
            this.message = message;
        }

        public PrintAction (double n) {this.message = String.format(Locale.US, "%.2f", n);}
        public PrintAction (int n) {this.message = String.format(Locale.US, "%d", n);}
        public PrintAction (boolean b) {this.message = String.format(Locale.US, "%b", b);}


        @Override
        public boolean run() {
            System.out.println(message);
            return false;
        }
    }

    public static Action goToPoseAction(double x, double y, double h) {
        return new PrintAction(String.format(Locale.US, "GOING.TO.POSE(%.2f, %.2f, %.2f)", x, y, h));
    }
}
