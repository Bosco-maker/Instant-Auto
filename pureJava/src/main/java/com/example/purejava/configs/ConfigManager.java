package com.example.purejava.configs;

import static com.example.instantauto.configs.MetaFieldRegistry.registerField;
import static com.example.instantauto.configs.MetaFieldRegistry.registerType;
import static com.example.instantauto.actions.MetaActionRegistry.registerCondition;

public class ConfigManager {
    public static void init() {
        // Register type definitions
        registerType(new Pose2d(0, 0, 0));
        registerType(new IntakeSetting("", false, 0));

        // Register specific fields with default values
        registerField("maxPower", Double.class, 0.0);
        registerField("redGoalPose", Pose2d.class, new Pose2d(-72, 48, 0));
        registerField("blueGoalPose", Pose2d.class, new Pose2d(72, 48, 0));
        registerField("intakeActive", IntakeSetting.class, new IntakeSetting("NORMAL", true, 0.8));
        registerField("motorName", String.class, "motorName");

        // New required fields for Auto
        registerField("Title", String.class, "");
        registerField("Starting", String.class, "");

        // Register custom conditions
        registerCondition("is_active", () -> true);
    }
}
