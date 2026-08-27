# Subsystems

Subsystems encapsulate hardware logic and expose capabilities as actions. This keeps `ActionManager` clean and enables reusable, testable robot code.

---

## Subsystem Pattern

### 1. Subsystem Class
```java
public class Arm {
    private Motor shoulder;
    private Motor elbow;
    private Servo wrist;
    
    // Target positions (ticks or degrees)
    public static final double INTAKE_HEIGHT = 100;
    public static final double SCORE_HEIGHT = 800;
    public static final double STOW_HEIGHT = 0;
    
    public void init(HardwareMap hw) {
        shoulder = hw.get(Motor.class, "shoulder");
        elbow = hw.get(Motor.class, "elbow");
        wrist = hw.get(Servo.class, "wrist");
        
        // Configure motors
        shoulder.setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE);
        elbow.setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE);
        
        // Reset to known state
        goToHeight(STOW_HEIGHT);
    }
    
    // Return Action for text-file usage
    public Action goToHeight(double targetHeight) {
        return new Action() {
            private boolean initialized = false;
            
            @Override
            public boolean run() {
                if (!initialized) {
                    shoulder.setTargetPosition((int) targetHeight);
                    elbow.setTargetPosition((int) targetHeight);
                    shoulder.setMode(DcMotor.RunMode.RUN_TO_POSITION);
                    elbow.setMode(DcMotor.RunMode.RUN_TO_POSITION);
                    shoulder.setPower(1.0);
                    elbow.setPower(1.0);
                    initialized = true;
                }
                
                // Complete when both motors reach target
                return shoulder.isBusy() || elbow.isBusy();
            }
        };
    }
    
    public Action setWrist(double position) {
        return () -> {
            wrist.setPosition(position);
            return false;  // Instant complete
        };
    }
}
```

### 2. Initialize in ActionManager
```java
public class ActionManager {
    private Arm arm = new Arm();
    private Intake intake = new Intake();
    // ... other subsystems
    
    public void init(MecanumDrive drive, Telemetry telemetry, HardwareMap hw) {
        // Initialize ALL subsystems once
        arm.init(hw);
        intake.init(hw);
        
        // Register actions that delegate to subsystems
        UserActionRegistry.register(new MiniAction("ARM.HEIGHT", params -> {
            double[] d = ActionUtils.asDoubles(params, 1);
            return d != null ? arm.goToHeight(d[0]) : null;
        }));
        
        UserActionRegistry.register(new MiniAction("ARM.WRIST", params -> {
            double[] d = ActionUtils.asDoubles(params, 1);
            return d != null ? arm.setWrist(d[0]) : null;
        }));
        
        UserActionRegistry.register(new MiniAction("ARM.SCORE", params -> 
            arm.goToHeight(Arm.SCORE_HEIGHT)));
        
        UserActionRegistry.register(new MiniAction("ARM.INTAKE", params -> 
            arm.goToHeight(Arm.INTAKE_HEIGHT)));
        
        UserActionRegistry.register(new MiniAction("ARM.STOW", params -> 
            arm.goToHeight(Arm.STOW_HEIGHT)));
    }
}
```

### 3. Use in Text Files
```ini
# UserActionSettings.txt
scoreCycle = {
    ARM.INTAKE,
    INTAKE.ON(1.0),
    WAIT(0.5),
    ARM.SCORE,
    INTAKE.OFF,
    ARM.STOW
}

# ACTIVEAuto.txt
ARM.STOW
SPLINE.TO(intakePose, 0, 90)
scoreCycle
```

---

## RoadRunner Subsystem Actions

For subsystems that need **trajectory fusion** (e.g., arm moving during drive), return `BuilderAction`:

```java
public class Arm {
    // ... init ...
    
    public Action goToHeightFused(double targetHeight) {
        return new BuilderAction() {
            private com.acmerobotics.roadrunner.Action cached;
            
            @Override
            public TrajectoryActionBuilder apply(TrajectoryActionBuilder builder) {
                // This runs during fusion — builder is the drive trajectory
                // We can't directly control arm here, but we can return an action
                // that runs in parallel via PARALLEL
                return builder;  // No drive modification
            }
            
            @Override
            public boolean run() {
                if (cached == null) {
                    // Build arm action to run in parallel
                    cached = new com.acmerobotics.roadrunner.Action() {
                        @Override public boolean run(TelemetryPacket packet) {
                            return arm.goToHeight(targetHeight).run();
                        }
                    };
                }
                return cached.run(new TelemetryPacket());
            }
        };
    }
}
```

> **Note**: For true trajectory fusion (arm movement blended into drive path), use `PARALLEL` in text files. The `BuilderAction` pattern is mainly for drive actions (`STRAFE.TO`, `SPLINE.TO`).

---

## Best Practices

| Practice | Reason |
|----------|--------|
| `init(HardwareMap)` separate from constructor | HardwareMap not available at construction |
| Return `Action` from subsystem methods | Directly usable in text files via MiniAction |
| Use `RUN_TO_POSITION` for motors | Reliable completion detection |
| Set `ZeroPowerBehavior.BRAKE` | Holds position when action completes |
| Initialize subsystems **once** in `ActionManager.init()` | Prevents re-initialization on every action call |
| Expose both parameterized and preset actions | `ARM.HEIGHT(500)` and `ARM.SCORE` |

---

## Complete Subsystem Example: Intake

```java
public class Intake {
    private Motor motor;
    private Servo servo;
    
    public static final double INTAKE_POWER = 1.0;
    public static final double OUTTAKE_POWER = -1.0;
    public static final double SERVO_OPEN = 0.5;
    public static final double SERVO_CLOSED = 0.0;
    
    public void init(HardwareMap hw) {
        motor = hw.get(Motor.class, "intakeMotor");
        servo = hw.get(Servo.class, "intakeServo");
        motor.setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE);
        stop();
        close();
    }
    
    public Action setPower(double power) {
        return () -> { motor.setPower(power); return false; };
    }
    
    public Action stop() { return setPower(0); }
    
    public Action intake() { return setPower(INTAKE_POWER); }
    public Action outtake() { return setPower(OUTTAKE_POWER); }
    
    public Action setServo(double pos) {
        return () -> { servo.setPosition(pos); return false; };
    }
    public Action open() { return setServo(SERVO_OPEN); }
    public Action close() { return setServo(SERVO_CLOSED); }
}
```

**ActionManager registration:**
```java
UserActionRegistry.register(new MiniAction("INTAKE.ON", p -> {
    double[] d = ActionUtils.asDoubles(p, 1);
    return d != null ? intake.setPower(d[0]) : intake.intake();
}));
UserActionRegistry.register(new MiniAction("INTAKE.OFF", p -> intake.stop()));
UserActionRegistry.register(new MiniAction("INTAKE.OUTTAKE", p -> intake.outtake()));
UserActionRegistry.register(new MiniAction("INTAKE.SERVO", p -> {
    double[] d = ActionUtils.asDoubles(p, 1);
    return d != null ? intake.setServo(d[0]) : null;
}));
UserActionRegistry.register(new MiniAction("INTAKE.OPEN", p -> intake.open()));
UserActionRegistry.register(new MiniAction("INTAKE.CLOSE", p -> intake.close()));
```

**Text file usage:**
```ini
INTAKE.ON(1.0)      # Custom power
INTAKE.ON           # Default intake power
INTAKE.OFF
INTAKE.OUTTAKE
INTAKE.OPEN
INTAKE.CLOSE
INTAKE.SERVO(0.3)
```

---

## Next: [Custom Actions →](custom-actions.md)