# Custom Actions

Create new primitive actions by registering `MiniAction` factories in `ActionManager`. This page covers all action types from simple to RoadRunner-integrated.

---

## MiniAction Factory Pattern

```java
UserActionRegistry.register(new MiniAction("ACTION.NAME", params -> {
    // params: String (literals) or resolved object (variable lookup)
    // Return: Action implementation
    return new Action() { ... };
}));
```

The factory receives:
- **String**: Raw parameter text (e.g., `"30, 0, 0"`)
- **Resolved object**: If parameter is a variable name, the resolved value (e.g., `Pose2d`)

---

## Type 1: Simple InstantAuto Action

No RoadRunner — pure logic, servo, motor, etc.

```java
UserActionRegistry.register(new MiniAction("SERVO.SET", params -> {
    double[] d = ActionUtils.asDoubles(params, 2);  // port, position
    if (d == null) return null;
    
    int port = (int) d[0];
    double pos = d[1];
    
    return new Action() {
        @Override public boolean run() {
            hardwareMap.get(Servo.class, "servo" + port).setPosition(pos);
            return false;  // Instant complete
        }
    };
}));
```

**Text usage:** `SERVO.SET(1, 0.5)` or `SERVO.SET(servoPort, servoPosVar)`

---

## Type 2: RoadRunner Action via `ActionUtils.wrap()`

Wrap any `com.acmerobotics.roadrunner.Action`:

```java
UserActionRegistry.register(new MiniAction("DRIVE.TO", params -> {
    double[] d = ActionUtils.asDoubles(params, 3);
    if (d == null) return null;
    
    com.acmerobotics.roadrunner.Action rrAction = drive.actionBuilder(drive.localizer.getPose())
        .strafeTo(new Vector2d(d[0], d[1]))
        .build();
    
    return ActionUtils.wrap(rrAction);
}));
```

---

## Type 3: BuilderAction (Fusible Trajectory)

**Essential for `STRAFE.TO`/`SPLINE.TO` style actions** — enables trajectory fusion.

```java
UserActionRegistry.register(new MiniAction("CUSTOM.DRIVE", params -> {
    double[] d = ActionUtils.asDoubles(params, 3);
    if (d == null) return null;
    
    // Return BuilderAction WITH caching for if/else safety
    return createCachedBuilderAction(builder -> 
        builder.strafeTo(new Vector2d(d[0], d[1]))
               .turn(Math.toRadians(d[2]))
    );
}));
```

### Caching Helper (Required for if/else)

```java
private Action createCachedBuilderAction(BuilderAction delegate) {
    return new Action() {
        private com.acmerobotics.roadrunner.Action cachedAction;
        
        @Override public boolean run() {
            if (cachedAction == null) {
                cachedAction = delegate.apply(
                    drive.actionBuilder(drive.localizer.getPose())
                ).build();
            }
            return cachedAction.run(new TelemetryPacket());
        }
    };
}
```

> **Without caching**: Trajectory rebuilds every loop → stuttering, wrong paths in `if/else`.

---

## Type 4: Composite Actions (PARALLEL/RACE Style)

Combine multiple sub-actions recursively:

```java
UserActionRegistry.register(new MiniAction("SEQUENTIAL", params -> {
    List<Action> actions = ActionUtils.asActions(params, drive);
    if (actions == null) return null;
    
    List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
        .map(a -> ActionUtils.adapt(a, telemetry))
        .collect(Collectors.toList());
    
    return ActionUtils.wrap(new SequentialAction(rrActions));
}));

UserActionRegistry.register(new MiniAction("PARALLEL", params -> {
    List<Action> actions = ActionUtils.asActions(params, drive);
    if (actions == null) return null;
    
    List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
        .map(a -> ActionUtils.adapt(a, telemetry))
        .collect(Collectors.toList());
    
    return ActionUtils.wrap(new ParallelAction(rrActions));
}));

UserActionRegistry.register(new MiniAction("RACE", params -> {
    List<Action> actions = ActionUtils.asActions(params, drive);
    if (actions == null) return null;
    
    List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
        .map(a -> ActionUtils.adapt(a, telemetry))
        .collect(Collectors.toList());
    
    return ActionUtils.wrap(new RaceAction(rrActions));
}));
```

**Custom RaceAction** (ends when ANY completes):
```java
public static class RaceAction implements com.acmerobotics.roadrunner.Action {
    private final List<com.acmerobotics.roadrunner.Action> actions;
    public RaceAction(List<com.acmerobotics.roadrunner.Action> actions) { this.actions = actions; }
    
    @Override public boolean run(TelemetryPacket packet) {
        boolean anyRunning = false;
        for (com.acmerobotics.roadrunner.Action a : actions) {
            if (a.run(packet)) anyRunning = true;
        }
        return anyRunning && !actions.isEmpty();
    }
}
```

---

## Parameter Parsing Utilities

### `ActionUtils.asDoubles(params, count)`
- Parses `"30, 0, 0"` → `[30, 0, 0]`
- Resolves variable: `"myPose"` → looks up `Pose2d` → `[x, y, heading]`
- Returns `null` on failure

### `ActionUtils.asActions(params, drive)`
- Parses `"ACTION1(p1), ACTION2(p2)"` → `List<Action>`
- Recursively calls `UserActionRegistry.createAction()`
- Returns `null` on failure

### `ActionUtils.asString(value)`
- Formats primitives for telemetry: `1.23`, `42`, `true`

---

## Complete Custom Action Checklist

| Step | Code |
|------|------|
| 1. Choose type | Simple / RR wrap / BuilderAction / Composite |
| 2. Parse params | `ActionUtils.asDoubles(params, N)` |
| 3. Handle variable refs | `asDoubles` does this automatically |
| 4. Return `Action` | Implement `run()` or extend `BuilderAction` |
| 5. Cache if BuilderAction | Use `createCachedBuilderAction()` |
| 6. Register in `ActionManager.init()` | `UserActionRegistry.register(new MiniAction(...))` |

---

## Example: Vision Alignment Action

```java
UserActionRegistry.register(new MiniAction("VISION.ALIGN", params -> {
    // No params needed, but support optional timeout
    double[] d = ActionUtils.asDoubles(params, 1);
    double timeout = d != null ? d[0] : 3.0;
    
    return new Action() {
        private double startTime;
        private boolean aligned = false;
        
        @Override public boolean run() {
            if (startTime == 0) startTime = System.currentTimeMillis() / 1000.0;
            
            // Check vision
            List<Detection> detections = detector.getDetections();
            if (!detections.isEmpty()) {
                Detection best = detections.get(0);
                double error = best.getX() - 320;  // Center offset
                
                if (Math.abs(error) < 10) {
                    aligned = true;
                    drive.setDrivePower(new PoseVelocity2d(new Vector2d(0,0), 0));
                    return false;  // Done
                }
                
                // Proportional correction
                drive.setDrivePower(new PoseVelocity2d(
                    new Vector2d(0, -error * 0.01), 0));
            }
            
            // Timeout
            if (System.currentTimeMillis() / 1000.0 - startTime > timeout) {
                drive.setDrivePower(new PoseVelocity2d(new Vector2d(0,0), 0));
                return false;
            }
            
            return true;  // Continue aligning
        }
    };
}));
```

**Text usage:** `VISION.ALIGN` or `VISION.ALIGN(5.0)` for 5-second timeout.

---

## Error Handling

Always validate params and return `null` on failure:

```java
UserActionRegistry.register(new MiniAction("REQUIRE.TWO", params -> {
    double[] d = ActionUtils.asDoubles(params, 2);
    if (d == null) {
        telemetry.addLine("ERROR: REQUIRE.TWO needs 2 numbers");
        return null;  // Creates no-op action that completes instantly
    }
    // ...
}));
```

AutoParser will log: `Action line X: Unknown Action -> REQUIRE.TWO(...)`

---

## Next: [Complete Example →](example.md)