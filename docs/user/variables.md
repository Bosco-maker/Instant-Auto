# Variables

Variables in InstantAuto are managed through a centralized system (`MetaFieldRegistry`) that allows seamless interaction between your Java code and your sequence files.

> [!NOTE]
> Variable names are **NOT case-sensitive** (`maxSpeed` = `maxspeed` = `MAXSPEED`).

---

## Variable Types

### 1. Local Variables (Text-File Only)
Defined directly in your `.txt` files using `key = value` syntax. If not pre-registered in Java, the parser automatically infers the type.

```ini
myNumber = 10          # Integer
myDouble = 3.14        # Double
myFlag = true          # Boolean
myText = "hello"       # String
myPose = pose2d(10, 20, 90)  # Pose2d (requires type registered)
```

**Type inference order:** Boolean → Integer → Double → String → MetaField types

> [!WARNING]
> Local variables have **no default value** in Java. If your Java code tries to read a variable that only exists in text files, it will be null/missing. Always register important variables in Java via `ConfigManager.init()`.

---

### 2. Registered Fields (Global Variables)
Registered in Java during initialization. These have default values and can be overridden in text files.

```java
// In ConfigManager.init()
MetaFieldRegistry.registerField("maxSpeed", Double.class, 0.8);
MetaFieldRegistry.registerField("scorePose", Pose2d.class, new Pose2d(48, 24, 90));
MetaFieldRegistry.registerField("intakeConfig", IntakeSetting.class, new IntakeSetting("NORMAL", true, 1.0));
```

**In text files:**
```ini
# Override the default
maxSpeed = 1.0
scorePose = pose2d(50, 25, 90)
```

---

### 3. Sensor Fields (Dynamic Variables / Suppliers)
Registered with a `Supplier` — the value is fetched **live** every time it's accessed.

```java
// In ConfigManager.init()
MetaFieldRegistry.registerField("batteryVoltage", Double.class, 
    () -> hardwareMap.voltageSensor.iterator().next().getVoltage());

MetaFieldRegistry.registerField("distanceCm", Double.class,
    () -> hardwareMap.get(DistanceSensor.class, "distance").getDistance(DistanceUnit.CM));
```

**In text files:**
```ini
# Reads live sensor value each time
RACE(
    PRINT(batteryVoltage),
    WAIT(3)
)
```

> [!WARNING]
> If you **assign** to a supplier field in your auto file (e.g., `batteryVoltage = 12.0`), the static value **overwrites the supplier** for the remainder of the run. The live sensor reading is lost.

---

### 4. Condition Suppliers (Boolean Logic Only)
Special boolean suppliers used exclusively for `if` conditions. Registered via `UserActionRegistry`.

```java
// In ConfigManager.init()
UserActionRegistry.registerCondition("withinDistance", () -> 
    hardwareMap.get(DistanceSensor.class, "distance").getDistance(DistanceUnit.CM) < 10.0);

UserActionRegistry.registerCondition("isActive", () -> true);  // Always true
```

**Key difference:** Unlike regular variables, **Condition Suppliers CANNOT be overwritten** by assignments in text files. They are evaluated fresh every time the `if` is reached.

```ini
# This assignment has NO EFFECT on the condition
withinDistance = false

if (withinDistance) {  // Still calls the supplier!
    INTAKE.CLOSE
}
```

---

## Variable Assignment Behavior

### Top-Level Assignments (Static — Init Time)
In `ACTIVE*.txt` and `GeneralRobotSettings.txt`, any `key = value` at the top level runs **during initialization**, before any action executes.

```ini
Starting = pose2d(-24, 0, 0)
isBlue = true          # Applied BEFORE first action
scorePose = pose2d(0, 0, 0)  # Override global!

STRAFE.TO(scorePose)   # Uses overridden value
```

> **All top-level assignments happen before the action sequence starts**, regardless of position in file or `if` blocks.

```ini
num = 1
if (false) {
    num = 2    // Still executes at init time!
}
RACE(PRINT(num), WAIT(2))  // Prints "2", not "1"
```

---

### Inside Big Actions (Runtime — Execution Time)
In `UserActionSettings.txt`, assignments inside `{ }` are **executable actions** that run when the Big Action executes.

```ini
# UserActionSettings.txt
dynamicAction = {
    PRINT("Before: " + scorePose),
    scorePose = pose2d(100, 100, 0),  # Runs HERE, at runtime
    PRINT("After: " + scorePose),
    STRAFE.TO(scorePose)
}
```

**Example showing the difference:**
```ini
# ACTIVEAuto.txt
Starting = pose2d(0, 0, 0)
num = 1
printAction
printAction

# UserActionSettings.txt
printAction = {
    RACE(PRINT(num), WAIT(1))
    num = 2
}
```
**Output:** `1` → `2` (assignment runs between the two calls)

---

## Using Variables in Actions

Most numeric parameters accept variable references:

```ini
# Literal
STRAFE.TO(30, 0, 0)

# Variable reference
STRAFE.TO(scorePose)
SPLINE.TO(pickupPose, 45, -45)

# Mixed
targetX = 40
STRAFE.TO(targetX, 0, headingVar)
```

**Resolution logic:** Parser tries literal parse → if fails, looks up in `MetaFieldRegistry`.

---

## Built-in Variable Types

| Type | Syntax | Java Registration |
|------|--------|-------------------|
| `double` | `speed = 1.5` | `registerField("speed", Double.class, 1.0)` |
| `int` | `count = 3` | `registerField("count", Integer.class, 0)` |
| `boolean` | `enabled = true` | `registerField("enabled", Boolean.class, false)` |
| `String` | `name = "bot"` | `registerField("name", String.class, "")` |
| `pose2d` | `p = pose2d(10,20,90)` | `registerType(new Pose2d(...))` + `registerField("p", Pose2d.class, ...)` |
| `intakeSetting` | `s = intakeSetting("NORMAL", true, 1.0)` | `registerType(new IntakeSetting(...))` + `registerField(...)` |

---

## Custom MetaField Types

Create your own types by implementing `MetaField<T>`:

```java
public class ArmPreset implements MetaField<ArmPreset> {
    public final String name;
    public final double shoulder, elbow, wrist;

    public ArmPreset(String name, double s, double e, double w) { ... }

    @Override public String getIdentifier() { return "armPreset"; }
    @Override public Class<?>[] getParamTypes() { 
        return new Class[]{String.class, double.class, double.class, double.class}; 
    }
}
```

**Register:**
```java
MetaFieldRegistry.registerType(new ArmPreset("", 0, 0, 0));
MetaFieldRegistry.registerField("armIntake", ArmPreset.class, new ArmPreset("INTAKE", 45, 90, 0));
```

**Use in text:**
```ini
armIntake = armPreset("INTAKE", 45, 90, 0)
MOVE.ARM(armIntake)
```

---

## Best Practices

| Practice | Why |
|----------|-----|
| Register all important variables in Java | Ensures defaults, type safety, Java access |
| Use suppliers for sensor data | Always reads live value |
| Use condition suppliers for logic-critical sensors | Cannot be accidentally overwritten |
| Override in ACTIVE file, not GeneralRobotSettings | Per-match configuration |
| Put runtime assignments in Big Actions | Clear execution timing |
| Name poses descriptively | `blueScorePose` not `pose1` |

---

## Quick Reference

| Scenario | Java Registration | Text File Usage |
|----------|-------------------|-----------------|
| Tunable constant | `registerField("kP", Double.class, 0.5)` | `kP = 0.6` |
| Default pose | `registerField("start", Pose2d.class, new Pose2d(0,0,0))` | `Starting = pose2d(-24,0,0)` |
| Live sensor | `registerField("dist", Double.class, () -> sensor.getDistance())` | `if (dist < 10) { ... }` |
| Logic condition | `registerCondition("near", () -> sensor.getDistance() < 10)` | `if (near) { ... }` |
| Runtime variable change | N/A | Inside Big Action: `x = 5` |