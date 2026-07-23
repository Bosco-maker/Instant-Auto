package com.example.instantauto.actions;

/**
 * Interface for both:
 * actions that can be created with parameters from a text file.
 * and
 * actions that internally created in java
 */
public interface MetaAction {
    /**
     * @return The identifier used in the text file (e.g., "GO_TO").
     */
    String getIdentifier();

    /**
     * Creates an executable Action based on the provided parameter.
     * @param params The parameter (e.g., a "10, 20, 0" String or a Pose2d object).
     * @return A RoadRunner-style Action.
     */
    Action create(Object params);

    Action create(String params);
}
