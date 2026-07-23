package com.example.purejava;

import com.example.instantauto.configs.ConfigParser;
import com.example.instantauto.configs.MetaFieldRegistry;
import com.example.purejava.configs.ConfigManager;

import java.util.List;

public class TeleOpRuntime {

    public static void main(String[] args) {
        System.out.println("=== Initializing Robot TeleOp ===");

        // Register maxPower if not already registered in Registry static block
        if (MetaFieldRegistry.getEntry("maxPower") == null) {
            MetaFieldRegistry.registerField("maxPower", Double.class, 0.0);
        }

        ConfigParser engine = new ConfigParser();
        ConfigManager.init();
        String configPath = "pureJava/src/main/java/com/example/purejava/textfiles/GeneralRobotSettings";

        System.out.println("Reading config from: " + configPath);
        engine.parseConfig(configPath);

        System.out.println("\n--- Parser Logs (Validation/Dry Run) ---");
        List<String> logs = engine.getLogs();
        if (logs.isEmpty()) {
            System.out.println("No errors found.");
        } else {
            for (String log : logs) {
                System.out.println("[ERROR] " + log);
            }
        }

        System.out.println("\n--- Final Configured Values ---");
        printField("redGoalPose");
        printField("blueGoalPose");
        printField("intakeActive");
        printField("maxPower");
        printField("motorName");

        System.out.println("\n=== Initialization Complete ===");
    }

    private static void printField(String name) {
        MetaFieldRegistry.ConfigEntry<?> entry = MetaFieldRegistry.getEntry(name);
        if (entry != null) {
            System.out.println(entry.fieldName + ": " + entry.value);
        } else {
            System.out.println(name + ": [Not Registered]");
        }
    }
}
