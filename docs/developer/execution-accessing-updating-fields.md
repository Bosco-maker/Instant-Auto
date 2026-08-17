# Execution (Page 1): Accessing & Updating Fields/Suppliers in MetaFieldRegistry

## Overview

This page covers how to **read** and **write** configuration fields and sensor suppliers at runtime — from Java code, action factories, and text files. The `MetaFieldRegistry` is the central nervous system for all variable data.

---

## Reading Values

### From Java Code

```java
import com.example.instantauto.configs.MetaFieldRegistry;

// Get typed entry (recommended)
MetaFieldRegistry.ConfigEntry<Pose2d> entry = 
    (MetaFieldRegistry.ConfigEntry<Pose2d>) MetaFieldRegistry.getEntry("redGoalPose");
Pose2d pose = entry.getValue();  // Returns Pose2d (static or supplier-evaluated)

// Get raw Object
Object rawValue = MetaFieldRegistry.getEntry("autoTimer").getValue();

// Check if field exists
if (MetaFieldRegistry.getEntry("optionalField") != null) { ... }

// Iterate all fields (for telemetry/debugging)
for (String name : MetaFieldRegistry.getAllRegisteredFieldNames()) {
    ConfigEntry<?> entry = MetaFieldRegistry.getEntry(name);
    telemetry.addLine(name + ": " + entry.getValue());
}
```

### From Action Factories (MiniAction)

```java
private Action strafeToFactory(Object params) {
    // params can be:
    // - String: variable name ("redGoalPose") 
    // - String: literal params ("30, 0, 0")
    // - Pose2d: pre-resolved object (rare)

    if (params instanceof String) {
        String varName = (String) params;
        ConfigEntry<?> entry = MetaFieldRegistry.getEntry(varName);
        if (entry != null && entry.getValue() instanceof Pose2d) {
            Pose2d pose = (Pose2d) entry.getValue();
            // Use pose.x, pose.y, pose.heading
        }
    }
    // ...
}
```

### From Conditions (if/else)

```java
// In UserActionRegistry.evaluateCondition()
ConfigEntry<?> entry = MetaFieldRegistry.getEntry(conditionName);
if (entry != null && entry.getValue() instanceof Boolean) {
    return (Boolean) entry.getValue();
}
```

---

## Writing/Updating Values

### From Java Code (Static Update)

```java
// Update a static field (switches supplier → static if needed)
MetaFieldRegistry.getEntry("autoTimer").setValue(30.0);

// Update with MetaField object
MetaFieldRegistry.getEntry("redGoalPose").setValue(new Pose2d(-60, 48, 0));

// Update from dashboard/user input
public void onDashboardInput(String fieldName, String value) {
    configEngine.userUpdateStaticEntry(fieldName, value);
}
```

### From Text Files (Config Lines)

```ini
# In auto file or GeneralRobotSettings.txt
redGoalPose = pose2d(-72, 48, 0)
autoTimer = 15.0
intakeActive = intakeSetting("NORMAL", true, 0.8)
```

### From Action Strings (Assignment Actions)

```ini
# In action sequence
scorePose = pose2d(-72, -67, 0)
intakePower = 0.9
isBlue = true
```

Creates an `Action` that executes the assignment on `run()`.

### From Suppliers (Dynamic Values)

```java
// Register supplier (evaluated on EVERY getValue() call)
MetaFieldRegistry.registerField("distance", Double.class, 
    (Supplier<Double>) () -> hardwareMap.get(DistanceSensor.class, "sensor")
        .getDistance(DistanceUnit.CM));

MetaFieldRegistry.registerField("gamepadLeftY", Double.class, 
    (Supplier<Double>) () -> (double) gamepad1.left_stick_y);

MetaFieldRegistry.registerSupplier("batteryVoltage", Double.class, 
    () -> hardwareMap.voltageSensor.iterator().next().getVoltage());
```

> **Key insight**: Suppliers are **live**. Every `getValue()` calls the lambda. Use for sensors, gamepads, system time.

---

## Supplier vs Static: When to Use Which

| Scenario | Approach | Example |
|----------|----------|---------|
| Constant pose | Static | `Starting = pose2d(0,0,0)` |
| Tunable parameter | Static (updatable) | `autoTimer = 30.0` |
| Live sensor reading | **Supplier** | `distance = sensor.getDistance()` |
| Gamepad input | **Supplier** | `leftY = gamepad1.left_stick_y` |
| System time | **Supplier** | `sysTime = System.nanoTime()` |
| Battery voltage | **Supplier** | `voltage = voltageSensor.getVoltage()` |
| Computed value | **Supplier** | `heading = imu.getHeading()` |

---

## Type-Safe Access Patterns

### Pattern 1: Cast ConfigEntry (Best for known types)

```java
ConfigEntry<Pose2d> entry = (ConfigEntry<Pose2d>) MetaFieldRegistry.getEntry("Starting");
Pose2d pose = entry.getValue();  // Compile-time Pose2d
com.acmerobotics.roadrunner.Pose2d rrPose = pose.getRRPose2d();
```

### Pattern 2: Check Type at Runtime

```java
ConfigEntry<?> entry = MetaFieldRegistry.getEntry("someField");
if (entry != null) {
    Object value = entry.getValue();
    if (value instanceof Pose2d) { ... }
    else if (value instanceof Double) { ... }
    else if (value instanceof Integer) { ... }
}
```

### Pattern 3: Helper Methods in ActionUtils

```java
// Parse CSV string with variable resolution
double[] coords = ActionUtils.asDoubles(params, 3);
// Handles: "30, 0, 0" OR "myPose" (resolves to Pose2d → extracts x,y,heading)

String str = ActionUtils.asString(value);
// Formats: Double → "1.23", Integer → "42", Boolean → "true"
```

---

## Common Pitfalls

### ❌ Capturing Stale Values in Lambdas

```java
// WRONG: Captures value at registration time
double initialDist = sensor.getDistance(DistanceUnit.CM);
registerField("distance", Double.class, initialDist);

// CORRECT: Supplier evaluates live
registerField("distance", Double.class, 
    () -> sensor.getDistance(DistanceUnit.CM));
```

### ❌ Supplier Capturing Old OpMode Reference

```java
// In ConfigManager.init(OpMode opMode):
registerField("gamepadY", Double.class, 
    () -> opMode.gamepad1.left_stick_y);  // OK if cleared in stop()

// MUST clear in stop():
@Override public void stop() {
    MetaFieldRegistry.clear();  // Removes suppliers capturing old OpMode
}
```

### ❌ Forgetting to Register MetaField Types

```java
// MUST register type BEFORE parsing text files
MetaFieldRegistry.registerType(new Pose2d(0, 0, 0));
// Then text file can use: myPose = pose2d(10, 20, 30)
```

### ❌ Modifying Supplier Fields via setValue()

```java
ConfigEntry<?> entry = MetaFieldRegistry.getEntry("distance");
entry.setValue(5.0);  // This REPLACES supplier with static value 5.0!
// Subsequent getValue() returns 5.0, not live sensor reading
```

---

## Debugging & Telemetry

### Dump All Fields (from AutonomousBase.loop())

```java
private void dumpAllFields() {
    for (String name : MetaFieldRegistry.getAllRegisteredFieldNames()) {
        ConfigEntry<?> entry = MetaFieldRegistry.getEntry(name);
        telemetry.addLine(entry.fieldName + ": " + entry.getValue());
    }
}
```

### ConfigParser Logs

```java
for (String log : autoParser.getConfigLogs()) {
    telemetry.addLine("CFG: " + log);
}
```

### UserActionRegistry Errors

```java
for (String err : UserActionRegistry.getLoadErrors()) {
    telemetry.addLine("ACT: " + err);
}
```

---

## Summary: Field Access Cheat Sheet

| Operation | Code |
|-----------|------|
| Get static value | `registry.getEntry("name").getValue()` |
| Get supplier value (live) | `registry.getEntry("name").getValue()` |
| Update to static value | `registry.getEntry("name").setValue(newVal)` |
| Register static field | `MetaFieldRegistry.registerField("name", Type.class, defaultVal)` |
| Register supplier field | `MetaFieldRegistry.registerField("name", Type.class, supplier)` |
| Register supplier (alias) | `MetaFieldRegistry.registerSupplier("name", Type.class, supplier)` |
| List all field names | `MetaFieldRegistry.getAllRegisteredFieldNames()` |
| Clear all (between opmodes) | `MetaFieldRegistry.clear()` |
| Parse with variable resolution | `ActionUtils.asDoubles(params, count)` |
| Format for telemetry | `ActionUtils.asString(value)` |