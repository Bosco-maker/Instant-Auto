export type ConfigValue = string | number | boolean | any;

export interface ConfigEntry {
    fieldName: string;
    type: string;
    value: ConfigValue;
}

class ConfigRegistry {
    private entries: Map<string, ConfigEntry> = new Map();

    registerField(name: string, type: string, defaultValue: ConfigValue) {
        this.entries.set(name.toLowerCase(), {
            fieldName: name,
            type: type,
            value: defaultValue
        });
    }

    getEntry(name: string): ConfigEntry | undefined {
        return this.entries.get(name.toLowerCase());
    }

    updateValue(name: string, value: ConfigValue) {
        const entry = this.getEntry(name);
        if (entry) {
            entry.value = value;
        } else {
            // Register as a new entry if it doesn't exist
            this.registerField(name, typeof value, value);
        }
    }

    getAllFieldNames(): string[] {
        return Array.from(this.entries.values()).map(e => e.fieldName);
    }

    clear() {
        this.entries.clear();
    }
}

export const configRegistry = new ConfigRegistry();
