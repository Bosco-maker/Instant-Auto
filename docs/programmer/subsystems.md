# Subsystems

### Concept
Subsystems encapsulate hardware logic and provide an `init()` method to set default states (e.g., homing an arm). You then expose these capabilities to the text system via `MiniAction` registrations.

### Example
**Subsystem Class**: `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystem/Intake.java`

```java
public class Intake {
    Servo s;
    public void init(HardwareMap hw) { 
        s = hw.get(Servo.class, "s"); 
        s.setPosition(0); 
    }
    public Action setPos(double p) { 
        return () -> { 
            s.setPosition(p); 
            return false; 
        }; 
    }
}
```

**Registration**: In `ActionManager.java`

```java
Intake intake = new Intake();
intake.init(hardwareMap); // Called during init
UserActionRegistry.register(new MiniAction("INTAKE", (p) -> intake.setPos(Double.parseDouble(p.toString()))));
```

### Expected Outcome
Hardware is safely initialized once during the OpMode init phase, and complex robot behaviors are broken down into simple text commands.
