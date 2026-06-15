package com.example.instantauto.configs.types;

import java.util.Locale;

public class Pose2d implements MetaField<Pose2d> {
    public final double x, y, heading;

    public Pose2d(double x, double y, double heading) {
        this.x = x;
        this.y = y;
        this.heading = heading;
    }

    @Override
    public String getIdentifier() {
        return "pose2d";
    }

    @Override
    public Class<?>[] getParamTypes() {
        return new Class<?>[]{double.class, double.class, double.class};
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Pose2d(%.2f, %.2f, %.2f)", x, y, heading);
    }
}
