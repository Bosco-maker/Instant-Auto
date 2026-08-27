# Variables, Conditions & Hardware

This page covers integrating sensors, gamepad inputs, and dynamic values into InstantAuto via the `MetaFieldRegistry` and `UserActionRegistry`.

---

## Variable Registration (ConfigManager)

All variable registration happens in `ConfigManager.init(OpMode opMode)`.

### Static Fields (With Defaults)
```java
// Simple types
MetaFieldRegistry.registerField("autoTimer", Double.class, 0.0);
MetaFieldRegistry.registerField("maxVelocity", Double.class, 60.0);
MetaFieldRegistry.registerField("loopCount", Integer.class, 3);
MetaFieldRegistry.registerField("enabled", Boolean.class, true);
MetaFieldRegistry.registerField("motorName", String.class, "leftMotor");

// MetaField types (require registerType first)
MetaFieldRegistry.registerField("scorePose", Pose2d.class, new Pose2d(48, 24, 90));
MetaFieldRegistry.registerField("intakeConfig", IntakeSetting.class, new IntakeSetting("NORMAL", true, 1.0));

// Required fields for AutoParser
MetaFieldRegistry.registerField("Starting", Pose2d.class, new Pose2d(0, 0, 0));
MetaFieldRegistry.registerField("Title", String.class, "");
```

### Sensor Suppliers (Live Values)
Suppliers are called **every time** `getValue()` is invoked — perfect for sensors.

```java
// Distance sensor
MetaFieldRegistry.registerField("distanceCm", Double.class, 
    () -> opMode.hardwareMap.get(DistanceSensor.class, "distance").getDistance(DistanceUnit.CM));

// Battery voltage
MetaFieldRegistry.registerField("batteryVoltage", Double.class,
    () -> opMode.hardwareMap.voltageSensor.iterator().next().getVoltage());

// Gamepad input
MetaFieldRegistry.registerField("gamepadLeftY", Double.class,
    () -> (double) opMode.gamepad1.left_stick_y);

// IMU heading
MetaFieldRegistry.registerField("robotHeading", Double.class,
    () -> opMode.hardwareMap.get(IMU.class, "imu").getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES));

// System time
MetaFieldRegistry.registerField("sysTime", Long.class, System::nanoTime);
```

> **Alias**: `MetaFieldRegistry.registerSupplier(name, type, supplier)` does the same thing.

### Text File Usage
```ini
# ACTIVEAuto.txt
Starting = pose2d(-24, 0, 0)

# Read live sensor in condition
if (distanceCm < 10) {
    INTAKE.CLOSE
}

# Print live values
RACE(
    PRINT(batteryVoltage),
    WAIT(2)
)
```

---

## Condition Suppliers (Boolean Logic)

Register boolean suppliers for `if` conditions via `UserActionRegistry`. These have **highest priority** and **cannot be overwritten** by text-file assignments.

```java
// In ConfigManager.init()
UserActionRegistry.registerCondition("withinDistance", () -> 
    opMode.hardwareMap.get(DistanceSensor.class, "distance").getDistance(DistanceUnit.CM) < 10.0);

UserActionRegistry.registerCondition("sampleDetected", () -> 
    visionPortal.getFrameCount() > 0 && detector.getDetections().size() > 0);

UserActionRegistry.registerCondition("isActive", () -> true);  // Always true
```

### Text File Usage
```ini
if (withinDistance) {
    INTAKE.CLOSE
} else if (sampleDetected) {
    VISION.ALIGN
} else {
    SEARCH.PATTERN
}
```

### Priority Recap
| Priority | Source | Can Overwrite? |
|----------|--------|----------------|
| 1 | Literal `true`/`false` | N/A |
| 2 | **Condition Supplier** | **NO** |
| 3 | Boolean Variable | Yes (at init) |
| 4 | Undefined | Defaults `false` |

---

## Hardware Integration Pattern

### 1. Subsystem Classes
Encapsulate hardware in classes with `init(HardwareMap)`:

```java
public class Intake {
    Servo intakeServo;
    Motor intakeMotor;
    
    public void init(HardwareMap hw) {
        intakeServo = hw.get(Servo.class, "intakeServo");
        intakeMotor = hw.get(Motor.class, "intakeMotor");
        intakeServo.setPosition(0);  // Default state
    }
    
    // Return Actions for text-file usage
    public Action setPower(double power) {
        return () -> {
            intakeMotor.setPower(power);
            return false;  // Instant complete
        };
    }
    
    public Action setServo(double position) {
        return () -> {
            intakeServo.setPosition(position);
            return false;
        };
    }
}
```

### 2. Register in ActionManager
```java
public class ActionManager {
    Intake intake = new Intake();
    
    public void init(MecanumDrive drive, Telemetry telemetry, Hardwaremap hw) {
        intake.init(hw);  // Initialize hardware ONCE
        
        UserActionRegistry.register(new MiniAction("INTAKE.ON", params -> {
            double[] d = ActionUtils.asDoubles(params, 1);
            return d != null ? intake.setPower(d[0]) : null;
        }));
        
        UserActionRegistry.register(new MiniAction("INTAKE.OFF", params -> 
            intake.setPower(0)));
        
        UserActionRegistry.register(new MiniAction("INTAKE.SERVO", params -> {
            double[] d = ActionUtils.asDoubles(params, 1);
            return d != null ? intake.setServo(d[0]) : null;
        }));
    }
}
```

### 3. Use in Text Files
```ini
# UserActionSettings.txt
intakeCycle = {
    INTAKE.ON(1.0),
    WAIT(0.5),
    INTAKE.OFF
}

# ACTIVEAuto.txt
INTAKE.ON(0.8)
SPLINE.TO(scorePose, 0, 45)
intakeCycle
```

---

## Parameter Parsing with Variable Resolution

`ActionUtils.asDoubles()` handles both literals and variable references:

```java
// "30, 0, 0" → [30, 0, 0]
// "myPose" → resolves myPose (Pose2d) → [x, y, heading]
public static double[] asDoubles(Object params, int count) { ... }
```

**Usage in factory:**
```java
private Action myActionFactory(Object params) {
    double[] vals = ActionUtils.asDoubles(params, 2);
    if (vals == null) return null;  // Error handling
    
    double param1 = vals[0];
    double param2 = vals[1];
    // ...
}
```

---

## Complete ConfigManager Example

```java
public class ConfigManager {
    public static void init(OpMode opMode) {
        HardwareMap hw = opMode.hardwareMap;
        
        // 1. Register MetaField types
        MetaFieldRegistry.registerType(new Pose2d(0, 0, 0));
        MetaFieldRegistry.registerType(new IntakeSetting("", false, 0));
        
        // 2. Register static fields with defaults
        MetaFieldRegistry.registerField("Starting", Pose2d.class, new Pose2d(0, 0, 0));
        MetaFieldRegistry.registerField("Title", String.class, "");
        MetaFieldRegistry.registerField("scorePose", Pose2d.class, new Pose2d(48, 24, 90));
        MetaFieldRegistry.registerField("parkPose", Pose2d.class, new Pose2d(12, 12, 0));
        MetaFieldRegistry.registerField("maxVelocity", Double.class, 60.0);
        MetaFieldRegistry.registerField("intakePower", Double.class, 1.0);
        MetaFieldRegistry.registerField("isBlue", Boolean.class, true);
        
        // 3. Register sensor suppliers (live values)
        MetaFieldRegistry.registerField("distanceCm", Double.class,
            () -> hw.get(DistanceSensor.class, "distance").getDistance(DistanceUnit.CM));
        MetaFieldRegistry.registerField("batteryVoltage", Double.class,
            () -> hw.voltageSensor.iterator().next().getVoltage());
        
        // 4. Register condition suppliers (unchangeable logic)
        UserActionRegistry.registerCondition("withinDistance", () ->
            hw.get(DistanceSensor.class, "distance").getDistance(DistanceUnit.CM) < 10.0);
        UserActionRegistry.registerCondition("lowBattery", () ->
            hw.voltageSensor.iterator().next().getVoltage() < 11.0);
    }
}
```

---

## Text File Usage Summary

| Variable Type | Java Registration | Text File Example |
|---------------|-------------------|-------------------|
| Tunable constant | `registerField("speed", Double.class, 1.0)` | `speed = 0.8` |
| Default pose | `registerField("start", Pose2d.class, new Pose2d(0,0,0))` | `Starting = pose2d(-24,0,0)` |
| Live sensor | `registerField("dist", Double.class, () -> sensor.getDistance())` | `if (dist < 10) { ... }` |
| Logic condition | `registerCondition("near", () -> sensor.getDistance() < 10)` | `if (near) { ... }` |
| Gamepad | `registerField("leftY", Double.class, () -> gamepad1.left_stick_y)` | `ARM.SET(leftY * 100)` |

---

## Cleanup (Critical)

Always clear registries in `stop()` to prevent supplier leaks between OpModes:

```java
@Override
public void stop() {
    MetaFieldRegistry.clear();        // Clears fields + types
    UserActionRegistry.clear();       // Clears actions + conditions
}
```

---

## Next: [Subsystems →](subsystems.md)