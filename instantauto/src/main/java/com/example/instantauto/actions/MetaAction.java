package com.example.instantauto.actions;

/**
 * Interface for actions that can be created with parameters from a text file.
 */
public interface MetaAction {
    /**
     * @return The identifier used in the text file (e.g., "GO_TO").
     */
    String getIdentifier();

    /**
     * Creates an executable Action based on the provided parameter string.
     * @param params The parameters inside the parentheses (e.g., "10, 20, 0").
     * @return A RoadRunner-style Action.
     */
    Action create(String params);
}
