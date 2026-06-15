package com.example.instantauto.actions;

import java.util.function.Function;

public class MiniAction implements MetaAction {
    private final String id;
    private final Function<String, Action> factory;

    public MiniAction(String id, Function<String, Action> factory) {
        this.id = id;
        this.factory = factory;
    }

    @Override
    public String getIdentifier() {
        return id;
    }

    @Override
    public Action create(String params) {
        return factory.apply(params);
    }
}
