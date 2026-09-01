# Actions Reference

These are the **primitive actions** registered in `ActionManager` (TeamCode layer). They are the building blocks for your autonomous routines.

> [!NOTE]
> Action names are **case-insensitive**. `STRAFE.TO` = `strafe.to` = `Strafe.To`.

---

## Movement Actions

### `STRAFE.TO(x, y, heading)`
Moves the robot to the specified coordinates and heading using a linear path (strafe).

**Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `x` | double | X-coordinate in inches |
| `y` | double | Y-coordinate in inches |
| `heading` | double | Target heading in degrees |

**Alternative:** Accepts a `Pose2d` variable name.
```ini
STRAFE.TO(30, 0, 0)
STRAFE.TO(scorePose)
```

**Completion:** Returns `true` while trajectory is executing, `false` when complete.

**Warning:** May trigger "Displacement 0.0 out of bounds" if told to go to the same position it's already at (before action executes).

---

### `SPLINE.TO(x, y, heading, startTangent, endTangent)`
Moves the robot along a spline path to the target pose with tangent control.

**Parameters (5 doubles):**

| Param | Type | Description                                  |
|-------|------|----------------------------------------------|
| `x` | double | Target X in inches                           |
| `y` | double | Target Y in inches                           |
| `heading` | double | Target heading in degrees                    |
| `startTangent` | double | Starting tangent angle of the path (degrees) |
| `endTangent` | double | Ending tangent angle of the path (degrees)   |

**Alternative:** Accepts a `Pose2d` variable + 2 tangents.
```ini
SPLINE.TO(30, 30, 90, 0, 90)
SPLINE.TO(scorePose, 45, -45)
```

**Completion:** Returns `true` while trajectory is executing, `false` when complete.

**Tangent Tip:** For smooth chaining, `startTangent` of next spline = `endTangent` of previous ± 180°.

---

## Control Flow Actions

### `WAIT(seconds)`
Pauses execution for the specified time.

**Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `seconds` | double | Time to wait in seconds |

```ini
WAIT(1.5)
WAIT(0.5)
```

**Completion:** Returns `true` while waiting, `false` when time elapses.

---

### `PARALLEL(action1, action2)`
Executes multiple actions **simultaneously**. The block finishes when **all** included actions complete.

**Parameters:** Comma-separated action calls (parsed recursively).

```ini
PARALLEL(
    STRAFE.TO(10, 0, 0),
    INTAKE.ON(1.0)
)
```

**Completion:** Returns `true` while any sub-action is running, `false` when all complete.

---

### `RACE(action1, action2)`
Executes multiple actions **simultaneously**. The block finishes as soon as **any** included action completes.

**Parameters:** Comma-separated action calls (parsed recursively).

```ini
# Timeout pattern: stop driving after 3 seconds
RACE(
    STRAFE.TO(100, 0, 0),
    WAIT(3.0)
)
# Immediately follow with a "catch" action to stop the robot
STRAFE.TO(50, 0, 0)
```

**Completion:** Returns `true` while all sub-actions are running, `false` when **any** completes.

> [!IMPORTANT]
> When `RACE` ends early (timeout wins), the other actions are **cancelled but the drivebase keeps its velocity**. Always follow a `RACE` with a motion command to "catch" the robot.

---

## Utility Actions

### `PRINT(message)`
Displays a message in telemetry/console. **Never completes on its own** (returns `true` forever).

**Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `message` | String or variable | Literal string in quotes, or variable name |

```ini
PRINT("Sequence started")
PRINT(scorePose)
PRINT(isBlue)
```

**Critical:** Must be used inside `RACE` with a completing action:
```ini
# Correct - prints for 2 seconds then continues
RACE(
    PRINT("Driving to score"),
    WAIT(2)
)

# Wrong - hangs forever!
PARALLEL(
    STRAFE.TO(10, 0, 0),
    PRINT("This will hang")
)
```

> [!WARNING]
> Multiple `PRINT` actions in the same `RACE`/`PARALLEL` block will only show the first one (known limitation).

---

### `HELLO.WORLD`
Diagnostic action that prints "Hello World!" to console and telemetry. **Never completes** (returns `true` forever).

```ini
HELLO.WORLD
```

**Usage:** Must be in `RACE` with a completing action:
```ini
RACE(
    HELLO.WORLD,
    WAIT(3)
)
```

---

## Variable Resolution

Most numeric parameters accept **variable names** defined in `GeneralRobotSettings.txt` or at the top of your auto file.

```ini
# GeneralRobotSettings.txt
targetX = 40
scorePose = pose2d(48, 24, 90)

# ACTIVEAuto.txt
STRAFE.TO(targetX, 0, 0)
SPLINE.TO(scorePose, 0, 45)
```

**Resolution logic:** Parser tries to parse as literal → if fails, looks up in `MetaFieldRegistry`.

---

## Action Composition

### Big Actions (UserActionSettings.txt)
Combine primitives into reusable macros:
```ini
scoreSample = {
    SPLINE.TO(scorePose, 0, 45),
    INTAKE.ON(1.0),
    WAIT(0.5),
    INTAKE.OFF
}
```

### Inline Composition
`PARALLEL` and `RACE` accept nested actions:
```ini
RACE(
    PARALLEL(STRAFE.TO(10, 0, 0), ARM.UP),
    WAIT(2)
)
```

---

## Quick Reference Table

| Action | Syntax | Completes When | Common Use |
|--------|--------|----------------|------------|
| `STRAFE.TO` | `STRAFE.TO(x, y, h)` or `STRAFE.TO(var)` | Trajectory done | Linear movement |
| `SPLINE.TO` | `SPLINE.TO(x, y, h, st, et)` or `SPLINE.TO(var, st, et)` | Trajectory done | Curved paths |
| `WAIT` | `WAIT(seconds)` | Time elapsed | Delays, timing |
| `PARALLEL` | `PARALLEL(a1, a2)` | **All** done | Simultaneous subsystems |
| `RACE` | `RACE(a1, a2)` | **Any** done | Timeouts, fallbacks |
| `PRINT` | `PRINT("msg")` or `PRINT(var)` | **Never** | Telemetry (use with RACE) |
| `HELLO.WORLD` | `HELLO.WORLD` | **Never** | Diagnostics (use with RACE) |

---

## Testing Actions

### MeepMeep Simulator
Only these actions work in MeepMeepTestbed:
- `STRAFE.TO`
- `SPLINE.TO`
- `WAIT`
- `PARALLEL` (limited)
- `PRINT` (console only)

`RACE`, `HELLO.WORLD`, and custom subsystem actions require the robot.

### Robot Controller
During `INIT`, check telemetry:
- **Green**: All actions parsed successfully
- **Red "ACTION ERROR"**: Unknown action or wrong parameter count

---

## Common Patterns

### Timeout with Brake
```ini
RACE(
    SPLINE.TO(farPose, 0, 90),
    WAIT(2.5)
)
STRAFE.TO(50, 0, 0)  # Catch the robot
```

### Parallel Subsystem + Drive
```ini
PARALLEL(
    SPLINE.TO(scorePose, 0, 45),
    ARM.SET(scoreHeight)
)
```

### Conditional Path Selection
```ini
if (isBlue) {
    SPLINE.TO(blueScorePose, 0, 45)
} else {
    SPLINE.TO(redScorePose, 0, -45)
}
```

### Print with Timeout
```ini
RACE(
    PRINT("Aligning to AprilTag"),
    RACE(
        VISION.ALIGN,
        WAIT(3)
        )
)
```