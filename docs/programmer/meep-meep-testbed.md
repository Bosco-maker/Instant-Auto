# MeepMeepTestbed

## Overview

The **MeepMeepTestbed** is a simulation module that allows testing InstantAuto autonomous routines using **MeepMeep** (a 2D RoadRunner simulator) without requiring physical robot hardware. It executes the exact same text-file-based autonomous configurations that run on the robot, enabling rapid iteration and debugging.

**Key Benefit**: Write once, test in simulation, deploy to robot with identical text files.

---

## Architecture

### Module Structure

```
MeepMeepTestbed/
├── build.gradle                    # Dependencies: MeepMeep + instantauto
├── src/main/java/com/example/meepmeeptestbed/
│   ├── MeepMeepTestbed.java        # Main entry point - runs InstantAuto in sim
│   ├── VanillaMeepMeep.java        # Baseline MeepMeep for comparison
│   ├── textfiles/                  # Config files (same format as robot)
│   │   ├── GeneralRobotSettings.txt
│   │   ├── UserActionSettings.txt
│   │   └── testAuto.txt
│   └── simulation/                 # Simulation-specific implementations
│       ├── SimPose2d.java          # Pose2d for sim (degrees, not radians)
│       ├── SimulationActionManager.java  # Registers MiniActions for sim
│       ├── SimulationActionUtils.java    # Action conversion utilities
│       └── SimulationConfigManager.java  # Config registration for sim
```

### Execution Flow

```
MeepMeepTestbed.main()
    │
    ├─► 1. Parse Config (Phase 1)
    │      AutoParser.parseAutoConfig(autoFile)
    │      → Extracts "Starting" pose from MetaFieldRegistry
    │
    ├─► 2. Initialize MeepMeep
    │      new MeepMeep(800)
    │      DefaultBotBuilder → RoadRunnerBotEntity
    │      → Sets constraints (vel, accel, angVel, angAccel, trackWidth)
    │      → Sets start pose from config
    │
    ├─► 3. Initialize Actions
    │      SimulationActionManager.init(bot)
    │      → Registers MiniActions: STRAFE.TO, SPLINE.TO, WAIT, PARALLEL, PRINT
    │
    ├─► 4. Parse Actions (Phase 2)
    │      AutoParser.parseActions()
    │      → Parses action lines from text file
    │
    ├─► 5. Convert Actions
    │      SimulationActionUtils.asActions() → List<Action>
    │      SimulationActionUtils.adapt() → RoadRunner Actions
    │      merge() fuses consecutive BuilderActions into single trajectory
    │
    └─► 6. Run Simulation
           myBot.runAction(new SequentialAction(rrActions))
           meepMeep.start()
```

### Two-Phase Parsing (Same as Robot)

| Phase | Purpose | Requires |
|-------|---------|----------|
| **Config** | Get `Starting` pose, validate types | `MetaFieldRegistry`, `UserActionRegistry` |
| **Actions** | Build trajectory actions | `RoadRunnerBotEntity` (for `actionBuilder`) |

---

## Available Actions

### MiniActions (Registered in `SimulationActionManager`)

| Action | Syntax | Description |
|--------|--------|-------------|
| **STRAFE.TO** | `STRAFE.TO(x, y, heading)`<br>`STRAFE.TO(poseVar)` | Strafe to position with heading. Accepts literal `x,y,heading` (degrees) or variable reference to `SimPose2d`. |
| **SPLINE.TO** | `SPLINE.TO(x, y, heading, startTan, endTan)`<br>`SPLINE.TO(poseVar, startTan, endTan)` | Spline to pose with tangent control. 5 params: `x, y, heading, startTangent, endTangent` (all degrees). Or variable pose + 2 tangents. |
| **WAIT** | `WAIT(seconds)` | Sleep action. Single double parameter. |
| **PARALLEL** | `PARALLEL(ACTION1, ACTION2, ...)` | Runs sub-actions in parallel (RoadRunner `ParallelAction`). Sub-actions parsed by top-level commas. |
| **PRINT** | `PRINT("literal")`<br>`PRINT(varName)` | Prints to console. Quoted string = literal. Unquoted = variable lookup in `MetaFieldRegistry`. |

### Composite Actions (Defined in `UserActionSettings.txt`)

```ini
# Example: Define once, use anywhere
MY_COMPOSITE = {
    STRAFE.TO(10, 20, 90),
    WAIT(0.5),
    SPLINE.TO(targetPose, 45, -45)
}
```

### Control Flow (Parsed by `UserActionRegistry`)

| Construct | Syntax | Notes |
|-----------|--------|-------|
| **Variable Assignment** | `myPose = pose2d(10, 20, 90)` | Updates `MetaFieldRegistry` at runtime |
| **If/Else** | `if (cond) { A, B } else { C }` | Evaluated **once** on first run. Supports `else if` chains. |
| **Conditions** | Registered via `registerCondition()` | BooleanSuppliers (unchangeable) > Boolean variables > `false` |

### Registered Conditions (in `SimulationConfigManager`)

```java
registerCondition("is_active", () -> true);  // Always true for testing
```

---

## ⚠️ Limitation: If/Else Does NOT Work in MeepMeepTestbed

### The Problem

**If/else blocks are parsed and created correctly, but they do not execute properly in the simulation.**

```ini
# This will PARSE but not execute correctly in MeepMeepTestbed
if (withinDistance) {
    RACE(
        SPLINE.TO(0, 0, 0, 90, 270)
        SPLINE.TO(40, 0, 0, 270, 90)
    ),
    PRINT(sysTime)
} else {
    STRAFE.TO(-24, 0, 0)
}
```

### Root Cause

1. **No `actionMerger` set**: The `UserActionRegistry.setActionMerger()` callback is never configured in `MeepMeepTestbed`. This callback is responsible for:
    - Fusing consecutive `BuilderAction` trajectories (for smooth motion)
    - **Recursively merging nested actions inside if/else branches**

2. **If/else actions are created but not merged**: In the robot code (`ActionManager.init()`), `UserActionRegistry.setActionMerger()` is called with `ActionUtils.mergeNestedActions()`, which uses reflection to find and merge `trueActions`/`targetActions` fields inside the if/else `Action` objects. Without this, trajectories inside branches are never fused.

3. **No dynamic pose for branch entry**: On the robot, cached trajectories in if/else branches are built from `mecanumDrive.localizer.getPose()` at branch entry time. In MeepMeep, there's no equivalent live pose source for branch entry.

### Workaround

**Test if/else logic on the physical robot or restructure for simulation:**

```ini
# Option 1: Separate test files per branch
# testAuto_blue.txt
Starting = pose2d(-24, 0, 0)
SPLINE.TO(0, 0, 0, 90, 270)
SPLINE.TO(40, 0, 0, 270, 90)

# testAuto_red.txt  
Starting = pose2d(-24, 0, 0)
STRAFE.TO(-24, 0, 0)
```

```ini
# Option 2: Use variables to select path (no if/else)
pathChoice = "bluePath"  # or "redPath"
# Then define both paths as UserActions and call the selected one
```

### Tracking Issue

This is a known limitation. To fix, `SimulationActionUtils` would need:
1. A `mergeNestedActions` implementation for `RoadRunnerBotEntity`
2. Reflection-based recursive merging of if/else branch actions (same as `ActionUtils.mergeNestedActions`)
3. A way to get current bot pose for trajectory building at branch entry

---

## Setup Instructions

### Prerequisites

- Java 11+
- Gradle (wrapper included in project root)
- Project built: `./gradlew :instantauto:build`

### Run MeepMeepTestbed

```bash
# From project root
./gradlew :MeepMeepTestbed:run
```

Or in Android Studio:
1. Open Gradle panel (right side)
2. Navigate: `MeepMeepTestbed` → `Tasks` → `application` → `run`
3. Double-click `run`

### Configure Text Files

Edit files in `MeepMeepTestbed/src/main/java/com/example/meepmeeptestbed/textfiles/`:

| File | Purpose |
|------|---------|
| `GeneralRobotSettings.txt` | Global config (currently empty, uses `SimulationConfigManager` defaults) |
| `UserActionSettings.txt` | Define composite actions (currently empty) |
| `testAuto.txt` | Main autonomous routine to test |

### Example `testAuto.txt`

```ini
Title = My Test Autonomous
Starting = pose2d(-24, 0, 0)

STRAFE.TO(-30, 0, 0)
WAIT(0.5)
SPLINE.TO(0, 0, 0, 90, 270)
SPLINE.TO(40, 0, 0, 270, 90)
PRINT("Auto complete!")
```

### SimulationConfigManager Defaults

```java
registerField("Title", String.class, "");
registerField("Starting", SimPose2d.class, new SimPose2d(0,0,0));
registerField("redGoalPose", SimPose2d.class, new SimPose2d(-72, 48, 0));
registerField("blueGoalPose", SimPose2d.class, new SimPose2d(72, 48, 0));
registerField("autoTimer", Double.class, 0.0);
registerCondition("is_active", () -> true);
```

Add custom fields here for your autonomous.

---

## VanillaMeepMeep (Comparison Baseline)

### Purpose

`VanillaMeepMeep.java` provides a **pure MeepMeep + RoadRunner** reference implementation without InstantAuto. Use it to:

1. **Verify MeepMeep works** - Rule out simulator issues
2. **Compare trajectories** - Ensure InstantAuto produces equivalent paths
3. **Prototype paths** - Quick iteration on spline parameters before committing to text files

### VanillaMeepMeep Example

```java
public class VanillaMeepMeep {
    public static void main(String[] args) {
        MeepMeep meepMeep = new MeepMeep(800);

        RoadRunnerBotEntity myBot = new DefaultBotBuilder(meepMeep)
                .setConstraints(60, 60, Math.toRadians(180), Math.toRadians(180), 15)
                .build();

        myBot.runAction(myBot.getDrive().actionBuilder(new Pose2d(-24, 0, 0))
                .setTangent(Math.toRadians(90))
                .splineToSplineHeading(new Pose2d(0, 0, 0), Math.toRadians(270))
                .setTangent(Math.toRadians(270))
                .splineToSplineHeading(new Pose2d(40, 0, 0), Math.toRadians(90))
                .build());

        meepMeep.setBackground(MeepMeep.Background.FIELD_INTO_THE_DEEP_JUICE_DARK)
                .setDarkMode(true)
                .setBackgroundAlpha(0.95f)
                .addEntity(myBot)
                .start();
    }
}
```

### Run VanillaMeepMeep

```bash
./gradlew :MeepMeepTestbed:run --args="VanillaMeepMeep"
```

Or in Android Studio: Run `VanillaMeepMeep.main()` directly.

### Key Differences

| Aspect | MeepMeepTestbed | VanillaMeepMeep |
|--------|-----------------|-----------------|
| **Input** | Text files (InstantAuto format) | Hardcoded Java |
| **Actions** | STRAFE.TO, SPLINE.TO, WAIT, PARALLEL, PRINT | Raw RoadRunner builders |
| **Config** | `GeneralRobotSettings.txt`, `UserActionSettings.txt` | None |
| **If/Else** | Parsed but broken | N/A |
| **Use Case** | Test full autonomous logic | Baseline, path prototyping |

---

## Adding Custom Actions for Simulation

### 1. Create MiniAction Factory

```java
// In SimulationActionManager.init()
UserActionRegistry.register(new MiniAction("MY.ACTION", this::myActionFactory));
```

### 2. Implement Factory

```java
private Action myActionFactory(Object params) {
    // Resolve params (String literal or variable reference)
    // Return Action (BuilderAction for trajectories, or wrapped RR Action)
    return new SimulationActionUtils.BuilderAction() {
        @Override
        public TrajectoryActionBuilder apply(TrajectoryActionBuilder builder) {
            return builder.strafeTo(new Vector2d(x, y));
        }
        @Override
        public boolean run() {
            return apply(bot.getDrive().actionBuilder(bot.getPose())).build().run(new TelemetryPacket());
        }
    };
}
```

### 3. Register Config Types (if needed)

```java
// In SimulationConfigManager.init()
registerType(new MyCustomType());
registerField("myVar", MyCustomType.class, defaultValue);
```

---

## Debugging Tips

### Enable Verbose Logging

```java
// In MeepMeepTestbed.main()
List<String> configLogs = autoParser.getConfigLogs();
List<String> actionErrors = autoParser.getActionErrors();
List<String> loadErrors = UserActionRegistry.getLoadErrors();
// All printed automatically
```

### Visual Debugging

- MeepMeep window shows: Robot pose, trajectory path, field background
- Dark mode + field background enabled by default
- Click-drag to pan, scroll to zoom

### Common Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| "Starting pose missing" | `Starting` not in config or invalid | Add `Starting = pose2d(x, y, h)` to auto file |
| Action not found | MiniAction not registered | Add to `SimulationActionManager.init()` |
| Trajectory looks wrong | Degrees vs radians confusion | `SimPose2d` uses **degrees**; `getRRPose2d()` converts |
| If/else ignored | Known limitation | See workaround above |

---

## Integration with CI/CD

```yaml
# Example GitHub Actions step
- name: Run MeepMeep Simulation
  run: ./gradlew :MeepMeepTestbed:run --no-daemon
  # Note: MeepMeep requires display; use xvfb-run on headless CI
```

For headless CI, consider:
- `xvfb-run ./gradlew :MeepMeepTestbed:run`
- Or use `MeepMeepTestbed` as integration test with assertions on final pose

---

## File Reference

| File | Purpose |
|------|---------|
| `MeepMeepTestbed.java` | Main simulation entry point |
| `VanillaMeepMeep.java` | Baseline RoadRunner-only simulation |
| `SimPose2d.java` | Pose type (degrees, implements `MetaField`) |
| `SimulationActionManager.java` | Registers simulation MiniActions |
| `SimulationActionUtils.java` | Converts InstantAuto Actions → RoadRunner Actions, merges trajectories |
| `SimulationConfigManager.java` | Registers types, fields, conditions for sim |
| `textfiles/*.txt` | Config files (same format as robot) |