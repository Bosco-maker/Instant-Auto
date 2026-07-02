package com.example.instantauto.actions;

import java.util.List;

public class BigAction implements MetaAction {
    private final String id;
    private final List<String> subActionLines;
    private final boolean hasError;

    public BigAction(String id, List<String> subActionLines, boolean hasError) {
        this.id = id;
        this.subActionLines = subActionLines;
        this.hasError = hasError;
    }

    @Override
    public String getIdentifier() {
        return id;
    }

    @Override
    public Action create(Object params) {
        System.out.println("BIG ACTION: " + id);
        if (hasError) {
            return () -> false;
        }

        List<Action> actions = new java.util.ArrayList<>();
        for (String line : subActionLines) {
            Action a = MetaActionRegistry.createAction(line);
            if (a != null) {
                actions.add(a);
            }
        }

        return () -> {
            for (Action a : actions) {
                a.run();
            }
            return false;
        };
    }

    @Override
    public Action create(String params) {
        if (hasError) {
            return () -> false;
        }

        List<Action> actions = new java.util.ArrayList<>();
        for (String line : subActionLines) {
            Action a = MetaActionRegistry.createAction(line);
            if (a != null) {
                actions.add(a);
            }
        }

        return () -> {
            for (Action a : actions) {
                a.run();
            }
            return false;
        };
    }
}
