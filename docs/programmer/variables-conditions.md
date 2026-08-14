# Variables, Conditions & Hardware

### Concept
Sensors are integrated by registering their live values as `Suppliers` in the `MetaFieldRegistry`. This allows you to treat a hardware sensor as a dynamic variable or a boolean condition inside your script.

### Example
**File**: `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/configs/ConfigManager.java`

```java
DistanceSensor dist = opMode.hardwareMap.get(DistanceSensor.class, "sensor");

// Register the raw reading as a variable
MetaFieldRegistry.registerField("distCM", Double.class, () -> dist.getDistance(DistanceUnit.CM));

// Register a logic gate as a condition
UserActionRegistry.registerCondition("isBlocked", () -> dist.getDistance(DistanceUnit.CM) < 10.0);
```

**Text File Usage**:
```kotlin
if (isBlocked) {
    PRINT("Path blocked at " + distCM)
}
```

### Expected Outcome
Real-time sensor data is accessible in text files for logic branching and telemetry debugging without further Java changes.
