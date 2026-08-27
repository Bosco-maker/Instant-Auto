# Action Reference (Complete)

Complete reference for all actions registered in the standard TeamCode `ActionManager`. These are the primitives available in every InstantAuto project using RoadRunner integration.

> **Note**: Action names are case-insensitive. `STRAFE.TO` = `strafe.to` = `Strafe.To`.

---

## Movement Actions

### `STRAFE.TO`

Moves robot to target pose using a straight-line strafe trajectory.

| Aspect | Details |
|--------|---------|
| **Syntax** | `STRAFE.TO(x, y, heading)` or `STRAFE.TO(poseVar)` |
| **Parameters** | 3 doubles: `x` (inches), `y` (inches), `heading` (degrees)<br>OR 1 variable: `Pose2d` reference |
| **Returns** | `BuilderAction` (fusable trajectory) |
| **Completes When** | Trajectory finished |
| **Fusion** | ✅ Fuses with consecutive `STRAFE.TO` / `SPLINE.TO` |

**Examples:**
```ini
STRAFE.TO(30, 0, 0)
STRAFE.TO(scorePose)
STRAFE.TO(10, 20, 180)
```

**Behavior:**
- Uses `MecanumDrive.actionBuilder().strafeToSplineHeading()`
- Caches trajectory on first run (current pose)
- Heading in **degrees** (converted to radians internally)

---

### `SPLINE.TO`

Moves robot along a spline path to target pose with tangent control.

| Aspect | Details |
|--------|---------|
| **Syntax** | `SPLINE.TO(x, y, heading, startTangent, endTangent)`<br>or `SPLINE.TO(poseVar, startTangent, endTangent)` |
| **Parameters** | 5 doubles: `x`, `y`, `heading`, `startTan`, `endTan` (all degrees)<br>OR 1 `Pose2d` variable + 2 doubles: `startTan`, `endTan` |
| **Returns** | `BuilderAction` (fusable trajectory) |
| **Completes When** | Trajectory finished |
| **Fusion** | ✅ Fuses with consecutive `STRAFE.TO` / `SPLINE.TO` |

**Examples:**
```ini
SPLINE.TO(30, 30, 90, 0, 90)
SPLINE.TO(scorePose, 45, -45)
SPLINE.TO(pickupPose, 180, 0)
```

**Tangent Continuity Rule:**
For smooth chaining: `next.startTangent = prev.endTangent ± 180°`

**Behavior:**
- Uses `builder.setTangent().splineToSplineHeading()`
- Caches trajectory on first run
- Tangents in **degrees**

---

## Control Flow Actions

### `WAIT`

Pauses execution for specified duration.

| Aspect | Details |
|--------|---------|
| **Syntax** | `WAIT(seconds)` |
| **Parameters** | 1 double: `seconds` |
| **Returns** | `WrappedRRAction` wrapping `SleepAction` |
| **Completes When** | Time elapsed |

**Examples:**
```ini
WAIT(1.5)
WAIT(0.5)
WAIT(waitTimeVar)
```

**Behavior:** Wraps RoadRunner's `SleepAction`. Returns `true` while waiting, `false` when done.

---

### `PARALLEL`

Executes multiple actions **simultaneously**. Completes when **ALL** sub-actions complete.

| Aspect | Details |
|--------|---------|
| **Syntax** | `PARALLEL(action1, action2, ...)` |
| **Parameters** | Comma-separated action calls (parsed recursively) |
| **Returns** | `WrappedRRAction` wrapping `ParallelAction` |
| **Completes When** | **All** sub-actions complete |
| **Nesting** | ✅ Supports nested actions |

**Examples:**
```ini
PARALLEL(
    STRAFE.TO(10, 0, 0),
    INTAKE.ON(1.0),
    ARM.SET(scoreHeight)
)

PARALLEL(SPLINE.TO(pose, 0, 45), PRINT("Driving"))
```

**Behavior:**
- Sub-actions parsed via `ActionUtils.asActions()`
- Each adapted to RR Action via `ActionUtils.adapt()`
- Wrapped in `ParallelAction`

---

### `RACE`

Executes multiple actions **simultaneously**. Completes when **ANY** sub-action completes.

| Aspect | Details |
|--------|---------|
| **Syntax** | `RACE(action1, action2, ...)` |
| **Parameters** | Comma-separated action calls (parsed recursively) |
| **Returns** | `WrappedRRAction` wrapping custom `RaceAction` |
| **Completes When** | **Any** sub-action completes |
| **Nesting** | ✅ Supports nested actions |

**Examples:**
```ini
# Timeout pattern
RACE(
    SPLINE.TO(farPose, 0, 45),
    WAIT(3.0)
)
STRAFE.TO(50, 0, 0)  # Critical: "catch" the robot!

# Wait for sensor or timeout
RACE(
    VISION.ALIGN,
    WAIT(2.0)
)
```

**⚠️ Critical Warning:**
When `RACE` ends early (timeout wins), the **other actions are cancelled but the drivebase keeps its velocity**. You **MUST** follow with a motion command to "catch" the robot:

```ini
RACE(DRIVE.TO(far), WAIT(2))
STRAFE.TO(currentX, currentY, currentHeading)  # Brake!
```

---

## Utility Actions

### `PRINT`

Displays message in telemetry/console. **Never completes** (returns `true` forever).

| Aspect | Details |
|--------|---------|
| **Syntax** | `PRINT("literal string")` or `PRINT(variableName)` |
| **Parameters** | 1 parameter: quoted string literal OR variable name |
| **Returns** | `WrappedRRAction` wrapping `PrintAction` |
| **Completes When** | **Never** (always returns `true`) |

**Examples:**
```ini
PRINT("Sequence started")
PRINT(scorePose)
PRINT(isBlue)
PRINT(distanceCm)
```

**⚠️ Critical Usage:**
Must be used inside `RACE` with a completing action:

```ini
# Correct - prints for 2 seconds
RACE(
    PRINT("Driving to score"),
    WAIT(2)
)

# WRONG - hangs forever!
PARALLEL(
    STRAFE.TO(10, 0, 0),
    PRINT("This will hang")
)
```

**Variable Resolution:**
- Quoted: `"text"` → literal string
- Unquoted: `myVar` → looks up in `MetaFieldRegistry`, calls `toString()`

---

### `HELLO.WORLD`

Diagnostic action that prints "Hello World!" to console and telemetry. **Never completes**.

| Aspect | Details |
|--------|---------|
| **Syntax** | `HELLO.WORLD` |
| **Parameters** | None |
| **Returns** | `WrappedRRAction` wrapping `PrintAction("Hello World!")` |
| **Completes When** | **Never** |

**Usage:**
```ini
RACE(
    HELLO.WORLD,
    WAIT(3)
)
```

---

## Subsystem Actions (Project-Specific)

These are **examples** of typical subsystem actions registered in `ActionManager`. Your project will have different names based on hardware.

| Action | Typical Params | Returns | Purpose |
|--------|----------------|---------|---------|
| `INTAKE.ON` | `[power]` (double) | `Action` | Start intake motor |
| `INTAKE.OFF` | none | `Action` | Stop intake motor |
| `INTAKE.OUTTAKE` | none | `Action` | Reverse intake |
| `INTAKE.OPEN` | none | `Action` | Open intake servo |
| `INTAKE.CLOSE` | none | `Action` | Close intake servo |
| `INTAKE.SERVO` | `position` (double) | `Action` | Set intake servo position |
| `ARM.HEIGHT` | `height` (double) | `Action` | Move arm to height (ticks/degrees) |
| `ARM.SCORE` | none | `Action` | Preset: scoring height |
| `ARM.INTAKE` | none | `Action` | Preset: intake height |
| `ARM.STOW` | none | `Action` | Preset: stowed position |
| `ARM.WRIST` | `position` (double) | `Action` | Set wrist servo |
| `VISION.ALIGN` | `[timeout]` (double) | `Action` | Align to AprilTag/sample |
| `SCORE.PRELOAD` | none | `Action` | Preload scoring sequence |

---

## Action Composition Table

| Composition | Syntax | Sub-Actions | Completion |
|-------------|--------|-------------|------------|
| Sequential | `A, B, C` (newlines or commas) | Runs in order | Each completes before next |
| Parallel | `PARALLEL(A, B, C)` | Runs simultaneously | All complete |
| Race | `RACE(A, B, C)` | Runs simultaneously | Any completes |
| Composite | `BIG_ACTION` | Defined in UserActionSettings | Sequential expansion |

---

## Parameter Resolution Rules

When an action parameter is parsed:

| Input Form | Resolution |
|------------|------------|
| `30, 0, 0` | Literal doubles `[30, 0, 0]` |
| `poseVar` | Looks up `poseVar` in `MetaFieldRegistry` → if `Pose2d`, passes object |
| `"hello"` | Literal string `"hello"` |
| `true` / `false` | Literal boolean |
| `myNumber` | Looks up `myNumber` → if `Double`/`Integer`, extracts value |

**Mixed Example:**
```ini
SPLINE.TO(scorePose, 45, -45)
# scorePose → Pose2d object
# 45, -45 → literal doubles
```

---

## Quick Reference Card

```
# Movement
STRAFE.TO(x, y, h)           # Linear move
STRAFE.TO(poseVar)           # Move to pose variable
SPLINE.TO(x, y, h, st, et)   # Spline with tangents
SPLINE.TO(poseVar, st, et)   # Spline to pose variable

# Control
WAIT(seconds)                # Sleep
PARALLEL(A, B, C)            # All must finish
RACE(A, B, C)                # Any finishes (add brake after!)

# Utility
PRINT("message")             # Telemetry (use with RACE)
PRINT(varName)               # Print variable value
HELLO.WORLD                  # Diagnostic (use with RACE)

# Subsystem (examples - vary by project)
INTAKE.ON([power])
INTAKE.OFF
ARM.HEIGHT(height)
ARM.SCORE / ARM.INTAKE / ARM.STOW
VISION.ALIGN([timeout])

# Composite
MY_BIG_ACTION                # Defined in UserActionSettings.txt

# Conditional
if (condition) { A, B } else { C }

# Assignment (runtime, inside composites)
myVar = newValue
```

---

## Version

**Reference Version**: 1.0  
**Compatible with**: TeamCode `ActionManager` standard implementation