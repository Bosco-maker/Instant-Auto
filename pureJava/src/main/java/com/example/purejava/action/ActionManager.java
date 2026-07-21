package com.example.purejava.action;

import com.example.instantauto.actions.Action;
import com.example.instantauto.actions.MetaActionRegistry;
import com.example.instantauto.actions.MiniAction;
import com.example.purejava.configs.Pose2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ActionManager {
    public static void init() {
        // Register Primitives (Mini Actions)
        MetaActionRegistry.register(new MiniAction("GO.TO.POSE2D", ActionManager::goToPoseFactory));

        MetaActionRegistry.register(new MiniAction("PRINT", obj ->
                new ActionManager.PrintAction(ActionUtils.asString(obj))));

        MetaActionRegistry.register(new MiniAction("PARALLEL", params -> {
            List<Action> actions = ActionUtils.asActions(params);
            return actions != null ? new ParallelAction(actions) : null;
        }));

        MetaActionRegistry.register(new MiniAction("RACE", params -> {
            List<Action> actions = ActionUtils.asActions(params);
            return actions != null ? new RaceAction(actions) : null;
        }));
    }

    private static Action goToPoseFactory(Object params) {
        // Handle Case 1: Received a Pose2d object (Variable Lookup)
        if (params instanceof Pose2d) {
            Pose2d p = (Pose2d) params;
            return ActionManager.goToPoseAction(p.x, p.y, p.heading);
        }

        // Handle Case 2: Received a String (Literal Parameters "x, y, h")
        double[] d = ActionUtils.asDoubles(params, 3);
        if (d != null) {
            return ActionManager.goToPoseAction(d[0], d[1], d[2]);
        }
        return null;
    }

    public static class PrintAction implements Action {
        String message;

        public PrintAction(String message) {
            this.message = message;
        }

        public PrintAction(double n) {
            this.message = String.format(Locale.US, "%.2f", n);
        }

        public PrintAction(int n) {
            this.message = String.format(Locale.US, "%d", n);
        }

        public PrintAction(boolean b) {
            this.message = String.format(Locale.US, "%b", b);
        }


        @Override
        public boolean run() {
            System.out.println(message);
            return false;
        }
    }

    public static class ParallelAction implements Action {
        private final List<Action> actions;

        public ParallelAction(Action... actions) {
            this.actions = new ArrayList<>(Arrays.asList(actions));
        }

        public ParallelAction(List<Action> actions) {
            this.actions = new ArrayList<>(actions);
        }

        @Override
        public boolean run() {
            actions.removeIf(action -> !action.run());
            return !actions.isEmpty();
        }
    }

    public static class RaceAction implements Action {
        private final List<Action> actions;

        public RaceAction(Action... actions) {
            this.actions = new ArrayList<>(Arrays.asList(actions));
        }

        public RaceAction(List<Action> actions) {
            this.actions = new ArrayList<>(actions);
        }

        @Override
        public boolean run() {
            boolean anyFinished = false;
            for (Action action : actions) {
                if (!action.run()) {
                    anyFinished = true;
                }
            }
            return !anyFinished;
        }
    }


    public static Action goToPoseAction(double x, double y, double h) {
        return new PrintAction(String.format(Locale.US, "GOING.TO.POSE(%.2f, %.2f, %.2f)", x, y, h));
    }
}
