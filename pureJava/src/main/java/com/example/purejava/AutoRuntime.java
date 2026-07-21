package com.example.purejava;

import com.example.instantauto.actions.Action;
import com.example.instantauto.actions.AutoParser;
import com.example.instantauto.actions.MetaActionRegistry;
import com.example.instantauto.configs.MetaFieldRegistry;

import java.io.File;
import java.util.List;
import java.util.Scanner;

public class AutoRuntime {
    private static final String TEXTFILES_DIR = "pureJava/src/main/java/com/example/purejava/textfiles/";
    private static final String GENERAL_SETTINGS = TEXTFILES_DIR + "GeneralRobotSettings";
    private static final String META_ACTION_SETTINGS = TEXTFILES_DIR + "MetaActionSettings";

    public static void main(String[] args) {
        try {
            runRuntime();
        } catch (RuntimeException e) {
            System.err.println("\n************************************************");
            System.err.println("PRE-START CRITICAL FAILURE");
            System.err.println(e.getMessage());
            System.err.println("************************************************\n");
            // Exit gracefully so Gradle doesn't show a generic crash message
        }
    }

    private static void runRuntime() {
        System.out.println("=== Robot Autonomous Runtime ===");

        AutoParser autoParser = new AutoParser(GENERAL_SETTINGS, META_ACTION_SETTINGS);

        // Register custom conditions
        MetaActionRegistry.registerCondition("is_active", () -> true);

        // 1. Scan for [ACTIVE] files
        List<File> activeAutos = autoParser.findActiveAutos(TEXTFILES_DIR);

        // 2. Ask user which auto to run
        System.out.println("Select an autonomous to run:");
        for (int i = 0; i < activeAutos.size(); i++) {
            System.out.println((i + 1) + ". " + activeAutos.get(i).getName());
        }

        Scanner scanner = new Scanner(System.in);
        int choice = -1;
        while (choice < 1 || choice > activeAutos.size()) {
            System.out.print("Enter choice (1-" + activeAutos.size() + "): ");
            if (scanner.hasNextInt()) {
                choice = scanner.nextInt();
            } else {
                scanner.next();
            }
        }
        File selectedAuto = activeAutos.get(choice - 1);

        // 3. Parse the selected auto
        System.out.println("\n--- Parsing " + selectedAuto.getName() + " ---");
        autoParser.parse(selectedAuto);

        // 4. Show Errors/Logs from auto action parsing
        List<String> loadErrors = MetaActionRegistry.getLoadErrors();
        if (!loadErrors.isEmpty()) {
            System.out.println("\n[BIG ACTION ERRORS/WARNINGS]:");
            for (String log : loadErrors) System.out.println("  " + log);
        }

        List<String> configLogs = autoParser.getConfigLogs();
        if (!configLogs.isEmpty()) {
            System.out.println("\n[CONFIG ERRORS/WARNINGS]:");
            for (String log : configLogs) System.out.println("  " + log);
        }

        List<String> actionErrors = autoParser.getActionErrors();
        if (!actionErrors.isEmpty()) {
            System.out.println("\n[ACTION ERRORS]:");
            for (String err : actionErrors) System.out.println("  " + err);
        }

        // 5. Initialization Complete
        System.out.println("\nInitialization Complete. Waiting to be run...");
        System.out.println("Press ENTER to start autonomous sequence...");
        try {
            new Scanner(System.in).nextLine();
        } catch (Exception e) {
            // Ignore input errors during wait
        }

        // 6. Execute actions
        System.out.println("\n--- Executing Autonomous Sequence ---");
        for (Action action : autoParser.getActions()) {
            action.run();
        }
        System.out.println("\n=== Autonomous Sequence Complete ===");
        dumpAllFields();
    }

    private static void printField(String name) {
        MetaFieldRegistry.ConfigEntry<?> entry = MetaFieldRegistry.getEntry(name);
        if (entry != null) {
            System.out.println(entry.fieldName + ": " + entry.value);
        } else {
            System.out.println(name + ": [Not Registered]");
        }
    }

    private static void dumpAllFields() {
        List<String> registeredIdentifiers = MetaFieldRegistry.getAllRegisteredFieldNames();
        for (String identifier : registeredIdentifiers) {
            printField(identifier);
        }
    }
}
