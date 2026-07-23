package com.example.instantauto.configs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetaFieldRegistry {
    
    public static class ConfigEntry<T> {
        public final String fieldName;
        public final Class<T> type;
        public T value;

        public ConfigEntry(String fieldName, Class<T> type, T defaultValue) {
            this.fieldName = fieldName;
            this.type = type;
            this.value = defaultValue;
        }
    }
    //Here stores all your variables (MetaField) from the text file and java
    private static final Map<String, ConfigEntry<?>> entries = new HashMap<>();
    //Here stores all the definitions of different variable types (MetaField)
    private static final Map<Class<?>, MetaField<?>> typeDefinitions = new HashMap<>();

    public static void registerType(MetaField<?> typeDef) {
        typeDefinitions.put(typeDef.getClass(), typeDef);
    }

    public static <T> void registerField(String fieldName, Class<T> type, T defaultValue) {
        entries.put(fieldName.toLowerCase(), new ConfigEntry<>(fieldName, type, defaultValue));
    }

    public static ConfigEntry<?> getEntry(String fieldName) {
        return entries.get(fieldName.toLowerCase());
    }

    public static MetaField<?> getTypeDefinition(Class<?> type) {
        return typeDefinitions.get(type);
    }
    public static Collection<MetaField<?>> getAllRegisteredMetaFields() {
        return typeDefinitions.values();
    }

    public static List<String> getAllRegisteredFieldNames() {
        List<String> identifiers = new ArrayList<>();
        for (ConfigEntry<?> entry : entries.values()) {
            identifiers.add(entry.fieldName);
        }
        return identifiers;
    }
    //clear out all the variables and types to prevent leaking into next opMode
    public static void clear() {
        entries.clear();
        typeDefinitions.clear();
    }
}
