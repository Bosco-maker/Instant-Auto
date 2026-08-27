# Configuration (MetaField & MetaFieldRegistry)

## Overview

The **Configuration** subsystem provides a unified variable system that bridges:
- **Java code** (hardware sensors, computed values, constants)
- **Text files** (autonomous configurations, field poses, tuning parameters)
- **Runtime** (telemetry, dashboard updates, conditional logic)

At its core is `MetaFieldRegistry` — a thread-safe, type-safe registry storing `ConfigEntry` objects that can hold either **static values** or **dynamic suppliers**.

---

## MetaField Interface (`com.example.instantauto.configs.MetaField<T>`)

### Purpose
Defines **custom data types** that can be parsed from text files. Each implementation specifies:
- An **identifier** (e.g., `"pose2d"`, `"intakeSetting"`)
- Expected **parameter types** for parsing

### Interface Definition

```java
public interface MetaField<T> {
    /** @return Text file identifier (e.g., "pose2d") */
    String getIdentifier();

    /** @return Parameter types in order (e.g., double.class, double.class, double.class) */
    Class<?>[] getParamTypes();
}
```

### Built-in Examples

#### Pose2d (`org.firstinspires.ftc.teamcode.configs.Pose2d`)
```java
public class Pose2d implements MetaField<Pose2d> {
    public final double x, y, heading;

    public Pose2d(double x, double y, double heading) { ... }

    @Override public String getIdentifier() { return "pose2d"; }
    @Override public Class<?>[] getParamTypes() { 
        return new Class<?>[]{double.class, double.class, double.class}; 
    }

    public com.acmerobotics.roadrunner.Pose2d getRRPose2d() {
        return new com.acmerobotics.roadrunner.Pose2d(x, y, Math.toRadians(heading));
    }
}
```

**Text file usage:**
```ini
redGoalPose = pose2d(-72, 48, 0)
blueGoalPose = pose2d(72, 48, 0)
Starting = pose2d(0, 0, 0)
```

#### IntakeSetting (`org.firstinspires.ftc.teamcode.configs.IntakeSetting`)
```java
public class IntakeSetting implements MetaField<IntakeSetting> {
    public final String mode;
    public final boolean isActive;
    public final double power;

    @Override public String getIdentifier() { return "intakeSetting"; }
    @Override public Class<?>[] getParamTypes() { 
        return new Class<?>[]{String.class, boolean.class, double.class}; 
    }
}
```

**Text file usage:**
```ini
intakeActive = intakeSetting("NORMAL", true, 0.8)
```

---

## MetaFieldRegistry (`com.example.instantauto.configs.MetaFieldRegistry`)

### Core Data Structures

```java
public class MetaFieldRegistry {
    // All registered fields (from Java + text files)
    private static final Map<String, ConfigEntry<?>> entries = new HashMap<>();
    
    // All registered type definitions (MetaField implementations)
    private static final Map<Class<?>, MetaField<?>> typeDefinitions = new HashMap<>();
}
```

### ConfigEntry (`MetaFieldRegistry.ConfigEntry<T>`)

Each registered field is wrapped in a `ConfigEntry` that can operate in two modes:

| Mode | Constructor | Behavior |
|------|-------------|----------|
| **Static Value** | `ConfigEntry(name, type, defaultValue)` | Returns stored value; can be updated via `setValue()` |
| **Dynamic Supplier** | `ConfigEntry(name, type, supplier)` | Calls `supplier.get()` on every `getValue()` |

```java
public static class ConfigEntry<T> {
    public final String fieldName;
    public final Class<T> type;
    private T value;
    private Supplier<T> supplier;

    // Static value mode
    public ConfigEntry(String fieldName, Class<T> type, T defaultValue) { ... }
    
    // Supplier mode
    public ConfigEntry(String fieldName, Class<T> type, Supplier<T> supplier) { ... }

    public T getValue() {
        if (supplier != null) return supplier.get();  // Dynamic: called every time
        return value;                                  // Static: returns stored value
    }

    public void setValue(T newValue) {
        this.value = newValue;
        this.supplier = null;  // Switches to static mode
    }
}
```

---

## Registration API

### Registering Type Definitions (MetaField implementations)

```java
// In ConfigManager.init():
MetaFieldRegistry.registerType(new Pose2d(0, 0, 0));
MetaFieldRegistry.registerType(new IntakeSetting("", false, 0));
```

### Registering Fields with Static Defaults

```java
// Simple types
MetaFieldRegistry.registerField("autoTimer", Double.class, 0.0);
MetaFieldRegistry.registerField("motorName", String.class, "motorName");
MetaFieldRegistry.registerField("Title", String.class, "");

// MetaField types
MetaFieldRegistry.registerField("redGoalPose", Pose2d.class, new Pose2d(-72, 48, 0));
MetaFieldRegistry.registerField("blueGoalPose", Pose2d.class, new Pose2d(72, 48, 0));
MetaFieldRegistry.registerField("intakeActive", IntakeSetting.class, new IntakeSetting("NORMAL", true, 0.8));
MetaFieldRegistry.registerField("Starting", Pose2d.class, new Pose2d(0, 0, 0));
```

### Registering Fields with Dynamic Suppliers

Suppliers are **evaluated on every access** — perfect for sensor readings, gamepad inputs, and system time.

```java
// Gamepad input (evaluated every loop)
MetaFieldRegistry.registerField("gamepadLeftY", Double.class, 
    (Supplier<Double>) () -> (double) opMode.gamepad1.left_stick_y);

// System time
MetaFieldRegistry.registerField("sysTime", Long.class, 
    (Supplier<Long>) System::nanoTime);

// Distance sensor
MetaFieldRegistry.registerField("distance", Double.class, 
    (Supplier<Double>) () -> opMode.hardwareMap.get(DistanceSensor.class, "distanceSensor")
        .getDistance(DistanceUnit.CM));

// Alias: registerSupplier (same as registerField with supplier)
MetaFieldRegistry.registerSupplier("batteryVoltage", Double.class,
    () -> opMode.hardwareMap.voltageSensor.iterator().next().getVoltage());
```

### Registering from Text Files (Local Variables)

`ConfigParser` automatically creates local variables for unknown keys:

```ini
# In text file - auto-registered as local variables
customSpeed = 1.5
myPose = pose2d(10, 20, 90)
enableFeature = true
```

---

## Accessing Values

### From Java Code

```java
// Get ConfigEntry (type-safe)
ConfigEntry<Pose2d> entry = (ConfigEntry<Pose2d>) MetaFieldRegistry.getEntry("redGoalPose");
Pose2d pose = entry.getValue();  // Returns Pose2d object

// Get raw value (Object)
Object value = MetaFieldRegistry.getEntry("autoTimer").getValue();

// Update static value
MetaFieldRegistry.getEntry("autoTimer").setValue(30.0);

// List all registered fields
List<String> names = MetaFieldRegistry.getAllRegisteredFieldNames();
for (String name : names) {
    ConfigEntry<?> entry = MetaFieldRegistry.getEntry(name);
    telemetry.addLine(name + ": " + entry.getValue());
}
```

### In Action Strings (UserActionRegistry)

Variables are resolved automatically in action parameters:

```ini
# Variable reference in action
STRAFE.TO(redGoalPose)           # Looks up "redGoalPose" in registry
STRAFE.TO(30, 0, 0)              # Literal values
STRAFE.TO(scorePose, 90, -90)    # Mixed: pose variable + literal tangents
```

### In Conditions (if/else)

```ini
if (isBlue) {                     # Boolean variable or registered condition
    STRAFE.TO(blueGoalPose)
} else {
    STRAFE.TO(redGoalPose)
}

if (withinDistance) {             # Registered BooleanSupplier condition
    INTAKE.CLOSE
}
```

---

## Type Resolution Flow

```
Text File:  redGoalPose = pose2d(-72, 48, 0)
                    │
                    ▼
         ConfigParser.handleConfigLine()
                    │
                    ▼
    MetaFieldRegistry.getEntry("redGoalPose")  ──→ ConfigEntry<Pose2d>
                    │
                    ▼
    MetaFieldRegistry.getTypeDefinition(Pose2d.class)  ──→ Pose2d MetaField
                    │
                    ▼
    Pose2d.getParamTypes() → [double, double, double]
                    │
                    ▼
    Split params: "-72", "48", "0"
                    │
                    ▼
    Convert each to double
                    │
                    ▼
    new Pose2d(-72, 48, 0) via reflection
                    │
                    ▼
    ConfigEntry.setValue(parsedPose2d)
```

---

## Lifecycle & Cleanup

### Per-OpMode Initialization

```java
@Override
public void init() {
    ConfigManager.init(this);  // Registers all Java-side fields/types/suppliers
    // ... parse text files ...
}
```

### Cleanup Between OpModes

```java
@Override
public void stop() {
    MetaFieldRegistry.clear();      // Clears entries + typeDefinitions
    UserActionRegistry.clear();     // Clears actions + conditions
}
```

> **Critical**: Always call `clear()` in `stop()` to prevent field leakage between opmodes (especially suppliers capturing old `OpMode` references).

---

## Best Practices

### 1. Use Suppliers for Sensor Data
```java
// ✅ Good: Live sensor reading
registerField("distance", Double.class, 
    () -> hardwareMap.get(DistanceSensor.class, "sensor").getDistance(DistanceUnit.CM));

// ❌ Bad: Stale value captured at init
double dist = hardwareMap.get(DistanceSensor.class, "sensor").getDistance(DistanceUnit.CM);
registerField("distance", Double.class, dist);
```

### 2. Register Types Before Parsing
```java
// In ConfigManager.init() - MUST be before AutoParser.parseConfig()
MetaFieldRegistry.registerType(new Pose2d(0, 0, 0));
MetaFieldRegistry.registerType(new IntakeSetting("", false, 0));
```

### 3. Use Descriptive Field Names
```java
// ✅ Clear purpose
registerField("blueAllianceStartPose", Pose2d.class, ...);
registerField("intakeDefaultPower", Double.class, 0.8);

// ❌ Ambiguous
registerField("pose1", Pose2d.class, ...);
registerField("power", Double.class, 0.8);
```

### 4. Leverage Type Safety
```java
// Get typed entry
ConfigEntry<Pose2d> entry = (ConfigEntry<Pose2d>) MetaFieldRegistry.getEntry("Starting");
Pose2d start = entry.getValue();  // Compile-time Pose2d, runtime checked

// Convert to RoadRunner Pose2d
com.acmerobotics.roadrunner.Pose2d rrPose = start.getRRPose2d();
```

---

## Extending with Custom MetaFields

### Step 1: Implement MetaField
```java
public class ArmPreset implements MetaField<ArmPreset> {
    public final String name;
    public final double shoulderAngle;
    public final double elbowAngle;
    public final double wristAngle;

    public ArmPreset(String name, double shoulder, double elbow, double wrist) {
        this.name = name;
        this.shoulderAngle = shoulder;
        this.elbowAngle = elbow;
        this.wristAngle = wrist;
    }

    @Override public String getIdentifier() { return "armPreset"; }
    @Override public Class<?>[] getParamTypes() { 
        return new Class<?>[]{String.class, double.class, double.class, double.class}; 
    }
}
```

### Step 2: Register Type
```java
MetaFieldRegistry.registerType(new ArmPreset("", 0, 0, 0));
```

### Step 3: Register Fields
```java
MetaFieldRegistry.registerField("armIntakePos", ArmPreset.class, 
    new ArmPreset("INTAKE", 45, 90, 0));
MetaFieldRegistry.registerField("armScorePos", ArmPreset.class, 
    new ArmPreset("SCORE", 180, 45, 90));
```

### Step 4: Use in Text Files
```ini
armIntakePos = armPreset("INTAKE", 45, 90, 0)
armScorePos = armPreset("SCORE", 180, 45, 90)

# In actions:
MOVE.ARM(armIntakePos)
```

---

## Files Reference

| File | Location |
|------|----------|
| `MetaField.java` | `instantauto/src/main/java/com/example/instantauto/configs/` |
| `MetaFieldRegistry.java` | `instantauto/src/main/java/com/example/instantauto/configs/` |
| `ConfigParser.java` | `instantauto/src/main/java/com/example/instantauto/configs/` |
| `Pose2d.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/configs/` |
| `IntakeSetting.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/configs/` |
| `ConfigManager.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/configs/` |