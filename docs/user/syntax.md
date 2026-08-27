# Syntax Reference

InstantAuto uses a simple, text-based syntax to define robot behavior. Configuration and sequences are split across three main file types, all using `.txt` extension.

---

## File Types

| File | Naming Pattern | Purpose |
|------|----------------|---------|
| General Settings | `GeneralRobotSettings.txt` | Global configuration, loaded first |
| User Actions | `UserActionSettings.txt` | Reusable "Big Action" macros |
| Active Autonomous | `ACTIVE<Name>.txt` | Match routine (must start with `ACTIVE`) |

---

## General Syntax Rules

### Comments
```ini
// Line comment
# Also a line comment
```
Inline comments after code are supported.

### Case Sensitivity
- **Action names**: Case-insensitive (`STRAFE.TO` = `strafe.to`)
- **Variable names**: Case-insensitive (`isBlue` = `isblue`)
- **String values**: Case-sensitive

### Whitespace
- Extra spaces and newlines are ignored
- Use indentation for readability (not required)

---

## 1. GeneralRobotSettings.txt

Defines global configuration — poses, speeds, sensor defaults, alliance selection.

### Variable Assignment
```ini
key = value
```

### Supported Value Types

| Type | Syntax | Example |
|------|--------|---------|
| **Double** | `3.14`, `42.0` | `maxVelocity = 60.0` |
| **Integer** | `42` | `loopCount = 3` |
| **Boolean** | `true` / `false` | `isBlue = true` |
| **String** | `"hello"` or `hello` | `robotName = "Champion"` |
| **Pose2d** | `pose2d(x, y, heading)` | `scorePose = pose2d(48, 24, 90)` |
| **Custom MetaField** | `typeName(param1, param2, ...)` | `intakeSetting("NORMAL", true, 1.0)` |

> **Angles**: All headings and tangents are in **degrees** (not radians).

### Example
```ini
// Alliance
isBlue = true

// Speeds
maxVelocity = 60.0
maxAngularVelocity = 180.0

// Poses (inches, degrees)
scorePose = pose2d(48, 24, 90)
pickupPose = pose2d(-36, -60, 180)
parkPose = pose2d(12, 12, 0)

// Subsystem defaults
intakePower = 1.0
armScoreHeight = 300
```

---

## 2. UserActionSettings.txt

Defines **Big Actions** (macros) — reusable sequences of primitive actions.

### Syntax
```ini
ActionName = {
    subAction1,
    subAction2,
    ...
}
```

- Sub-actions separated by **commas** or **newlines**
- Braces `{ }` required
- Multi-line supported

### Examples

```ini
# Single-line (commas)
scoreSample = SPLINE.TO(scorePose, 0, 45), INTAKE.ON(1.0), WAIT(0.5)

# Multi-line (newlines)
park = {
    STRAFE.TO(parkPose),
    WAIT(0.2),
    PRINT("Parked!")
}

# With variable references
alignToAprilTag = {
    TURN.TO(tagHeading),
    STRAFE.TO(tagPose),
    VISION.ALIGN
}
```

### Rules
- **Only defined primitives** can be used as sub-actions (registered in Java `ActionManager`)
- **No nesting**: Cannot reference other Big Actions inside a definition
- **Validated at load time**: Unknown sub-actions produce load errors visible in telemetry

---

## 3. ACTIVE*.txt (Autonomous Routine)

The match-specific routine. Contains config overrides followed by action sequence.

### Required Field
```ini
Starting = pose2d(x, y, heading)
```
**Must be present** — sets the robot's initial pose for localization.

### Optional Fields
```ini
title = "Blue Far Side"     # Shows in Driver Station autonomous selector
isBlue = false              # Override alliance from GeneralRobotSettings
anyVariable = newValue      # Override any global variable
```

### Action Sequence
Actions execute **sequentially** (top to bottom). Each line is one action or control structure.

```ini
Starting = pose2d(-24, 0, 0)
title = "Blue Auto"

STRAFE.TO(30, 0, 0)
SPLINE.TO(scorePose, 0, 45)
INTAKE.ON(1.0)
WAIT(0.5)
parkAction
```

---

## Action Calls

### Primitive Actions (Registered in Java)
```ini
ACTION_NAME(param1, param2, ...)
```

| Parameter Form | Example |
|----------------|---------|
| Literal numbers | `STRAFE.TO(30, 0, 0)` |
| Variable reference | `STRAFE.TO(scorePose)` |
| Mixed | `SPLINE.TO(pickupPose, 45, -45)` |

### Big Actions (From UserActionSettings)
```ini
ActionName
```
No parentheses — just the name.

---

## Control Flow: If / Else If / Else

### Syntax
```ini
if (condition) {
    action1,
    action2
} else if (anotherCondition) {
    action3
} else {
    action4
}
```

### Rules
- **Braces `{ }` required** even for single actions
- `else if` and `else` must be on **same line** as closing `}`
- **Nesting supported** (if inside if)
- Conditions evaluated **once at runtime** when block is reached (lazy evaluation)

### Valid Conditions
| Condition Type | Example | Priority |
|----------------|---------|----------|
| Literal | `true`, `false` | Highest |
| Registered BooleanSupplier | `withinDistance` | High (unchangeable) |
| Boolean variable | `isBlue` | Medium |
| Undefined | `unknownVar` | Low (defaults to `false`) |

### Example
```ini
if (isBlue) {
    STRAFE.TO(blueScorePose),
    scoreSample
} else if (isRed) {
    STRAFE.TO(redScorePose),
    scoreSample
} else {
    PRINT("No alliance set!"),
    WAIT(2)
}
```

---

## Variable Resolution in Actions

Parameters can be **literals** or **variable references**:

```ini
# Literal
STRAFE.TO(30, 0, 0)

# Variable reference (looks up in MetaFieldRegistry)
STRAFE.TO(scorePose)

# Mixed
SPLINE.TO(pickupPose, 45, -45)
```

**Resolution order**:
1. Try to parse as literal numbers
2. If fails, look up variable name in `MetaFieldRegistry`
3. If variable not found → action error

---

## MetaField Types

Custom types registered via `MetaFieldRegistry.registerType()`.

### Built-in: `pose2d`
```ini
myPose = pose2d(10, 20, 90)
STRAFE.TO(myPose)
```
Parameters: `x` (double), `y` (double), `heading` (double, degrees)

### Example: `intakeSetting`
```ini
intakeConfig = intakeSetting("REVERSE", true, 0.8)
INTAKE.SET(intakeConfig)
```
Parameters: `mode` (String), `active` (boolean), `power` (double)

---

## Static vs Runtime Assignment

### Top-Level Assignments (Static — Init Time)
In `ACTIVE*.txt`, any `key = value` at the top level runs **during initialization**, before any action executes.

```ini
Starting = pose2d(-24, 0, 0)
isBlue = true          // Applied before first action
scorePose = pose2d(0, 0, 0)  // Override!

STRAFE.TO(scorePose)   // Uses overridden value
```

> **All top-level assignments happen before the action sequence starts**, regardless of position in file or `if` blocks.

### Inside Big Actions (Runtime — Execution Time)
In `UserActionSettings.txt`, assignments inside `{ }` are **executable actions** that run when the Big Action executes.

```ini
# UserActionSettings.txt
dynamicAction = {
    PRINT("Before: " + scorePose),
    scorePose = pose2d(100, 100, 0),  // Runs HERE, at runtime
    PRINT("After: " + scorePose),
    STRAFE.TO(scorePose)
}
```

---

## Common Pitfalls

| Pitfall | Wrong | Correct |
|---------|-------|---------|
| Missing `ACTIVE` prefix | `BlueAuto.txt` | `ACTIVEBlueAuto.txt` |
| Missing `Starting` | (no Starting line) | `Starting = pose2d(0,0,0)` |
| No braces on if/else | `if (x) ACTION` | `if (x) { ACTION }` |
| `else` on new line | `} \n else {` | `} else {` |
| PRINT without RACE | `PRINT("hi")` | `RACE(PRINT("hi"), WAIT(2))` |
| Unknown action name | `STRFE.TO(0,0,0)` | `STRAFE.TO(0,0,0)` |
| Radians instead of degrees | `pose2d(0,0,1.57)` | `pose2d(0,0,90)` |

---

## Quick Reference Card

```ini
# GeneralRobotSettings.txt
key = value                    # Global config

# UserActionSettings.txt
BigAction = {                  # Macro definition
    ACTION1(params),
    ACTION2(params)
}

# ACTIVEAuto.txt
Starting = pose2d(x, y, h)     # REQUIRED
title = "Name"                 # Optional
var = value                    # Overrides

ACTION(params)                 # Primitive action
BigAction                      # Macro call

if (cond) {                    # Conditional
    ACTION
} else {
    ACTION
}
```