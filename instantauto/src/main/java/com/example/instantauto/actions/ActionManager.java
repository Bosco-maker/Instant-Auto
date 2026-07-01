package com.example.instantauto.actions;

import java.util.Locale;

public class ActionManager {
    public static class PrintAction implements Action {
        String message;

        public PrintAction(String message) {
            this.message = message;
        }

        public PrintAction (double n) {this.message = String.format(Locale.US, "%.2f", n);}
        public PrintAction (int n) {this.message = String.format(Locale.US, "%d", n);}
        public PrintAction (boolean b) {this.message = String.format(Locale.US, "%b", b);}


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
