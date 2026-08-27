# Conditions

InstantAuto supports conditional logic in autonomous scripts using `if`, `else if`, and `else` blocks. This allows your robot to make decisions at runtime based on sensor data, configuration variables, or match state.

---

## Syntax

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
- **Braces `{ }` required** — even for single actions
- `else if` and `else` must be on the **same line** as the closing `}`
- **Nesting supported** — `if` inside `if`
- Actions separated by commas or newlines

```ini
# Correct
if (isBlue) {
    STRAFE.TO(bluePose)
} else {
    STRAFE.TO(redPose)
}

# Wrong - else on new line
if (isBlue) {
    STRAFE.TO(bluePose)
}
else {        // ERROR
    STRAFE.TO(redPose)
}
```

---

## Condition Evaluation

Conditions are evaluated **once at runtime** when the `if` block is reached (lazy evaluation). The result determines which branch executes.

### Priority Order (Highest to Lowest)

| Priority | Source | Example | Overwritable? |
|----------|--------|---------|---------------|
| 1 | Literal `true` / `false` | `if (true)` | N/A |
| 2 | **Registered BooleanSupplier** | `if (withinDistance)` | **No** |
| 3 | Boolean variable (`MetaFieldRegistry`) | `if (isBlue)` | Yes (but static init) |
| 4 | Undefined / missing | `if (unknown)` | Defaults to `false` |

---

## Condition Sources

### 1. Literals
```ini
if (true) { ... }      # Always executes
if (false) { ... }     # Never executes
```

### 2. Registered Boolean Suppliers (Highest Priority)
Registered in Java via `UserActionRegistry.registerCondition()`:

```java
// In ConfigManager.init()
UserActionRegistry.registerCondition("withinDistance", () -> 
    hardwareMap.get(DistanceSensor.class, "distance").getDistance(DistanceUnit.CM) < 10.0);

UserActionRegistry.registerCondition("isActive", () -> true);
```

**In text:**
```ini
if (withinDistance) {
    INTAKE.CLOSE
}
```

> **Cannot be overwritten** by text-file assignments. The supplier is called fresh every time the `if` is evaluated.

### 3. Boolean Variables
Registered in Java via `MetaFieldRegistry.registerField()` or defined in text files:

```java
// Java
MetaFieldRegistry.registerField("isBlue", Boolean.class, true);
MetaFieldRegistry.registerField("hasPreload", Boolean.class, false);
```

```ini
# Text file (top-level — static init)
isBlue = true
hasPreload = false

# In actions
if (isBlue) {
    STRAFE.TO(blueScorePose)
}

if (hasPreload) {
    SCORE.PRELOAD
}
```

> **Top-level assignments happen at init time** (before actions start). Even inside `if` blocks in the auto file, the assignment executes during parsing.

### 4. Undefined Conditions
If a condition name doesn't match a supplier or variable, it evaluates to `false`.

```ini
if (thisDoesNotExist) {   # false
    ...
} else {
    PRINT("Default path")  # This runs
}
```

---

## Examples

### Alliance-Based Path Selection
```ini
# ACTIVEAuto.txt
Starting = pose2d(-24, 0, 0)
isBlue = true

if (isBlue) {
    SPLINE.TO(blueScorePose, 0, 45)
} else {
    SPLINE.TO(redScorePose, 0, -45)
}
```

### Multi-Case Selection (Else If)
```ini
if (isBlue) {
    PRINT("Blue alliance")
} else if (isRed) {
    PRINT("Red alliance")
} else {
    PRINT("Unknown alliance")
}
```

### Nested Conditions
```ini
if (isBlue) {
    if (hasPreload) {
        SCORE.PRELOAD
    }
    SPLINE.TO(blueScorePose, 0, 45)
} else {
    if (hasPreload) {
        SCORE.PRELOAD
    }
    SPLINE.TO(redScorePose, 0, -45)
}
```

### Sensor-Based Decision
```java
// Java registration
UserActionRegistry.registerCondition("sampleDetected", () -> 
    visionPortal.getFrameCount() > 0 && detector.getDetections().size() > 0);
```

```ini
# Text file
if (sampleDetected) {
    VISION.ALIGN
    INTAKE.ON(1.0)
} else {
    SEARCH.PATTERN
}
```

---

## Advanced: Action Fusing Inside Branches

InstantAuto automatically **fuses consecutive movement actions** (like `STRAFE.TO` and `SPLINE.TO`) into a single continuous RoadRunner trajectory. This applies **even inside `if/else` branches**.

```ini
if (isBlue) {
    STRAFE.TO(10, 10, 0)
    STRAFE.TO(20, 20, 0)
    SPLINE.TO(scorePose, 45, -45)
}
```

**Result:** If `isBlue` is true, all three movements become **one smooth trajectory** — no stops between segments.

> [!NOTE]
> This fusion requires `UserActionRegistry.setActionMerger()` to be configured in `ActionManager.init()` (handled automatically in TeamCode).

---

## Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| Condition always `false` | Typo in condition name | Check spelling; use registered supplier name exactly |
| `else` not recognized | `else` on new line | Put `} else {` on same line |
| Branch not executing | Variable assigned in `if` block at top level | Top-level assignments are static — move to Big Action for runtime |
| Supplier not called | Overwrote with assignment | Don't assign to condition supplier names |
| Nested if fails | Missing braces | Every `if`/`else` needs `{ }` |

---

## MeepMeep Simulator Limitation

> [!WARNING]
> **If/else blocks do NOT work correctly in MeepMeepTestbed.**
> 
> The simulator parses them but lacks the `actionMerger` callback needed to fuse trajectories inside branches. Test conditional logic on the physical robot, or use separate test files per branch:
> 
> ```ini
> # testAuto_blue.txt
> Starting = pose2d(-24, 0, 0)
> SPLINE.TO(blueScorePose, 0, 45)
> 
> # testAuto_red.txt
> Starting = pose2d(-24, 0, 0)
> SPLINE.TO(redScorePose, 0, -45)
> ```

---

## Quick Reference

```ini
# Literals
if (true) { ... }
if (false) { ... }

# Boolean supplier (unchangeable, live)
if (withinDistance) { ... }

# Boolean variable (static init, overridable)
if (isBlue) { ... }

# Syntax
if (cond) { ACTION } else { ACTION }
if (cond) { ACTION } else if (cond2) { ACTION } else { ACTION }

# Nesting
if (cond1) {
    if (cond2) { ACTION }
}
```