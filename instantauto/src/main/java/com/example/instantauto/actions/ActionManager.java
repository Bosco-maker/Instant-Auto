package com.example.instantauto.actions;

import java.util.Locale;

public class ActionManager {
    public static class PrintAction implements Action {
        String message;

        public PrintAction(String message) {
            this.message = message;
        }

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
