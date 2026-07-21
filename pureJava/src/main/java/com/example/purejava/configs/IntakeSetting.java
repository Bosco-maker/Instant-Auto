package com.example.purejava.configs;

import com.example.instantauto.configs.MetaField;

import java.util.Locale;

public class IntakeSetting implements MetaField<IntakeSetting> {
    public final String mode;
    public final boolean isActive;
    public final double power;

    public IntakeSetting(String mode, boolean isActive, double power) {
        this.mode = mode;
        this.isActive = isActive;
        this.power = power;
    }

    @Override
    public String getIdentifier() {
        return "intakeSetting";
    }

    @Override
    public Class<?>[] getParamTypes() {
        return new Class<?>[]{String.class, boolean.class, double.class};
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "IntakeSetting(mode='%s', active=%b, power=%.2f)", mode, isActive, power);
    }
}
