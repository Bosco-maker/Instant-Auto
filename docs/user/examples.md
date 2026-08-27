# Examples

Real autonomous routines tested on physical robots. All examples use `.txt` files and RoadRunner actions (`STRAFE.TO`, `SPLINE.TO`).

---

## 1. Chaining Splines (`SPLINE.TO`)

InstantAuto automatically fuses consecutive movement actions into a single continuous trajectory.

### Double Spline Path

```ini
# Path 1: Curve from (0,0) to (24,24)
SPLINE.TO(24, 24, 0, 90, 0)

# Path 2: Curve from (24,24) to (48,0)
SPLINE.TO(48, 0, -90, 180, -90)
```

**Pro Tip:** The `startTangent` of the next spline should be the previous `endTangent ± 180°` (opposite direction) for smooth continuity.

**Result:** Robot follows a smooth "S-curve" then "U-turn" in one fluid motion — no pause at (24, 24).

---

## 2. Concurrency: `PARALLEL` vs `RACE`

### The `PRINT` Behavior

`PRINT` **never completes** (returns `true` forever) to keep messages on telemetry. This changes how you use it with concurrency.

#### `PARALLEL` with Print — The Hang
```ini
PARALLEL(
    STRAFE.TO(48, 0, 0),
    PRINT("This will hang the robot")
)
```
**Result:** Robot drives to X=48 then stops **forever**. `PARALLEL` waits for ALL actions; `PRINT` never finishes.

#### `RACE` with Print — Correct
```ini
RACE(
    STRAFE.TO(48, 0, 0),
    PRINT("Driving to X=48...")
)
```
**Result:** Robot drives while message shows. When `STRAFE.TO` completes, it "wins the race" and the `RACE` exits, ending the `PRINT` too.

---

## 3. Timeouts and Motion Persistence

`RACE(drive, WAIT)` implements timeouts, but the drivebase keeps its velocity when cancelled.

### The Timeout Trap
```ini
RACE(
    STRAFE.TO(100, 0, 0),
    WAIT(2.0)
)
PRINT("Timeout reached!")
```
**Problem:** If timeout wins at X=50, robot continues coasting — no command told it to stop.

### Correct: Timeout with Brake
```ini
RACE(
    STRAFE.TO(100, 0, 0),
    WAIT(2.0)
)
# Immediately "catch" the drivebase
STRAFE.TO(50, 0, 0)
```
**Result:** New trajectory brings robot under control instantly.

---

## 4. Alliance-Based Routine

```ini
# ACTIVEBlueRedAuto.txt
Starting = pose2d(-24, 0, 0)
title = "Blue/Red Auto"
isBlue = true

if (isBlue) {
    SPLINE.TO(blueScorePose, 0, 45),
    INTAKE.ON(1.0),
    WAIT(0.5),
    INTAKE.OFF
} else {
    SPLINE.TO(redScorePose, 0, -45),
    INTAKE.ON(1.0),
    WAIT(0.5),
    INTAKE.OFF
}

# Common park
STRAFE.TO(parkPose)
```

---

## 5. Sensor-Guided Approach

```java
// Java: ConfigManager.init()
UserActionRegistry.registerCondition("sampleNear", () ->
    hardwareMap.get(DistanceSensor.class, "distance").getDistance(DistanceUnit.CM) < 15.0);
```

```ini
# ACTIVEAuto.txt
Starting = pose2d(-24, 0, 0)

# Drive toward sample
RACE(
    SPLINE.TO(approachPose, 0, 90),
    WAIT(3.0)  # Safety timeout
)
STRAFE.TO(50, 0, 0)  # Brake

# Conditional intake
if (sampleNear) {
    INTAKE.ON(1.0),
    WAIT(0.8),
    INTAKE.OFF
} else {
    PRINT("No sample detected"),
    SEARCH.PATTERN
}
```

---

## 6. Parallel Subsystem + Drive

Drive and actuate simultaneously:

```ini
PARALLEL(
    SPLINE.TO(scorePose, 0, 45),
    ARM.SET(scoreHeight),
    INTAKE.ON(1.0)
)
WAIT(0.3)  # Ensure all complete
ARM.SET(stowHeight)
```

---

## 7. Reusable Big Actions

```ini
# UserActionSettings.txt
scoreSample = {
    SPLINE.TO(scorePose, 0, 45),
    INTAKE.ON(intakePower),
    WAIT(0.5),
    INTAKE.OFF
}

park = {
    STRAFE.TO(parkPose),
    WAIT(0.2)
}

# ACTIVEAuto.txt
Starting = pose2d(-24, 0, 0)
scoreSample
park
```

---

## 8. Dynamic Variable in Big Action

```ini
# UserActionSettings.txt
adaptiveScore = {
    PRINT("Approaching: " + currentTarget),
    currentTarget = pose2d(adjustedX, adjustedY, 90),  # Runtime assignment!
    SPLINE.TO(currentTarget, 0, 45),
    INTAKE.ON(1.0)
}
```

---

## 9. Multi-Stage Auto with Conditions

```ini
# ACTIVEComplexAuto.txt
Starting = pose2d(-24, 0, 0)
title = "Complex Auto"
isBlue = true
hasPreload = true

# Stage 1: Preload
if (hasPreload) {
    SCORE.PRELOAD
}

# Stage 2: Alliance-specific intake
if (isBlue) {
    SPLINE.TO(blueIntakePose, 0, 90)
} else {
    SPLINE.TO(redIntakePose, 0, -90)
}

# Stage 3: Score cycle (repeated via Big Action)
scoreCycle
scoreCycle

# Stage 4: Park
parkAction
```

---

## Key Patterns Summary

| Pattern | Use Case | Template |
|---------|----------|----------|
| **Smooth chaining** | Continuous paths | `SPLINE.TO(...)` → `SPLINE.TO(...)` |
| **Timeout + brake** | Safety stop | `RACE(drive, WAIT)` → `STRAFE.TO(current)` |
| **Print with duration** | Telemetry | `RACE(PRINT(...), WAIT(sec))` |
| **Parallel subsystem** | Drive + arm/intake | `PARALLEL(drive, ARM.SET, INTAKE.ON)` |
| **Alliance branch** | Blue/Red paths | `if (isBlue) { ... } else { ... }` |
| **Sensor branch** | Reactive behavior | `if (sensorCondition) { ... } else { ... }` |
| **Reusable macro** | DRY principle | `BigAction = { ... }` in UserActionSettings |