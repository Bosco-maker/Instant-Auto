package com.example.purejava;

import com.example.instantauto.actions.Action;
import com.example.instantauto.actions.MetaActionRegistry;

import java.util.List;

public class ActionRegistryRuntime {
    public static void main(String[] args) {
        System.out.println("=== Action Registry Initialization ===");

        // Load the definitions from the text file
        String settingsPath = "pureJava/src/main/java/com/example/purejava/textfiles/MetaActionSettings";
        System.out.println("Loading settings from: " + settingsPath);
        MetaActionRegistry.loadSettings(settingsPath);

        List<String> loadErrors = MetaActionRegistry.getLoadErrors();
        if (!loadErrors.isEmpty()) {
            System.out.println("\n[BIG ACTION ERRORS/WARNINGS]:");
            for (String log : loadErrors) System.out.println("  " + log);
        }

        System.out.println("\n--- Registered Actions ---");
        List<String> ids = MetaActionRegistry.getRegisteredIdentifiers();
        for (String id : ids) {
            System.out.println("ID: " + id);
        }

        System.out.println("\n--- Testing 'Big Action' Execution ---");
        String testAction = "RED.THIRD.INTAKE";
        System.out.println("Creating and running: " + testAction);
        
        Action action = MetaActionRegistry.createAction(testAction);
        if (action != null) {
            action.run();
        } else {
            System.out.println("Error: Action not found!");
        }

        System.out.println("\n=== Initialization Complete ===");
    }
}
