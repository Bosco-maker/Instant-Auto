package com.example.instantauto.configs;

/**
 * Interface for complex data types in the robot configuration, to be able to parse them from text files.
 */
public interface MetaField<T> {
    /**
     * @return The identifiable string for this type (e.g., "pose2d").
     */
    String getIdentifier();

    /**
     * @return The expected types in order (e.g., Double.class, Double.class, Double.class).
     */
    Class<?>[] getParamTypes();
}
