# RoadRunner Integration

InstantAuto's TeamCode layer integrates with **RoadRunner 1.0** for all pathing, localization, and trajectory execution. The `MecanumDrive` class is the central hub for movement.

---

## Architecture

```
Text Files → AutoParser → UserActionRegistry (MiniActions)
                                    ↓
                           ActionManager (RoadRunner factories)
                                    ↓
                           ActionUtils (merge, adapt, wrap)
                                    ↓
                           AutonomousBase.start() → Actions.runBlocking()
```

---

## MecanumDrive Initialization

The `Starting` pose from the text file initializes `MecanumDrive`:

```java
// In AutonomousBase.init()
parser.parseAutoConfig(autoFile);  // Phase 1: populates MetaFieldRegistry

Pose2d startPose = (Pose2d) MetaFieldRegistry.getEntry("Starting").getValue();
MecanumDrive drive = new MecanumDrive(hardwareMap, startPose.getRRPose2d());
```

> **Critical**: `parseAutoConfig()` must run **before** `MecanumDrive` creation to get the `Starting` pose.

---

## ActionManager: Registering RoadRunner Primitives

In `ActionManager.init(MecanumDrive drive, Telemetry telemetry)`:

```java
public void init(MecanumDrive drive, Telemetry telemetry) {
    this.drive = drive;
    this.telemetry = telemetry;

    // Movement primitives
    UserActionRegistry.register(new MiniAction("STRAFE.TO", this::strafeToFactory));
    UserActionRegistry.register(new MiniAction("SPLINE.TO", this::splineToFactory));

    // Control flow
    UserActionRegistry.register(new MiniAction("WAIT", params -> {
        double[] d = ActionUtils.asDoubles(params, 1);
        return d != null ? ActionUtils.wrap(new SleepAction(d[0])) : null;
    }));

    UserActionRegistry.register(new MiniAction("PARALLEL", params -> {
        List<Action> actions = ActionUtils.asActions(params, drive);
        List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
            .map(a -> ActionUtils.adapt(a, telemetry)).collect(Collectors.toList());
        return ActionUtils.wrap(new ParallelAction(rrActions));
    }));

    UserActionRegistry.register(new MiniAction("RACE", params -> {
        List<Action> actions = ActionUtils.asActions(params, drive);
        List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
            .map(a -> ActionUtils.adapt(a, telemetry)).collect(Collectors.toList());
        return ActionUtils.wrap(new RaceAction(rrActions));
    }));

    // Utility
    UserActionRegistry.register(new MiniAction("PRINT", obj -> ActionUtils.wrap(new PrintAction(obj))));
    UserActionRegistry.register(new MiniAction("HELLO.WORLD", p -> ActionUtils.wrap(new PrintAction("Hello World!"))));
}
```

---

## Trajectory Factories (with Caching)

**Caching is essential** for `if/else` branches — without it, trajectories rebuild every loop.

```java
private Action strafeToFactory(Object params) {
    // Case 1: Variable reference (e.g., STRAFE.TO(scorePose))
    if (params instanceof String) {
        String varName = (String) params;
        ConfigEntry<?> entry = MetaFieldRegistry.getEntry(varName);
        if (entry != null && entry.getValue() instanceof Pose2d) {
            final Pose2d p = (Pose2d) entry.getValue();
            return createCachedBuilderAction(builder -> 
                builder.strafeToSplineHeading(new Vector2d(p.x, p.y), Math.toRadians(p.heading))
            );
        }
    }

    // Case 2: Literal parameters (e.g., STRAFE.TO(30, 0, 0))
    double[] d = ActionUtils.asDoubles(params, 3);
    if (d != null) {
        return createCachedBuilderAction(builder -> 
            builder.strafeToSplineHeading(new Vector2d(d[0], d[1]), Math.toRadians(d[2]))
        );
    }
    return null;
}

private Action splineToFactory(Object params) {
    if (params instanceof String) {
        String s = (String) params;
        String[] parts = s.split(",");

        // Case 1: 5 literals: x, y, heading, startTan, endTan
        if (parts.length == 5) {
            double[] d = ActionUtils.asDoubles(s, 5);
            if (d != null) {
                return createCachedBuilderAction(builder -> builder
                    .setTangent(Math.toRadians(d[3]))
                    .splineToSplineHeading(new Pose2d(d[0], d[1], Math.toRadians(d[2])), Math.toRadians(d[4]))
                );
            }
        }

        // Case 2: poseVar, startTan, endTan
        if (parts.length == 3) {
            String poseName = parts[0].trim();
            ConfigEntry<?> entry = MetaFieldRegistry.getEntry(poseName);
            if (entry != null && entry.getValue() instanceof Pose2d) {
                Pose2d p = (Pose2d) entry.getValue();
                try {
                    double startTan = Double.parseDouble(parts[1].trim());
                    double endTan = Double.parseDouble(parts[2].trim());
                    return createCachedBuilderAction(builder -> builder
                        .setTangent(Math.toRadians(startTan))
                        .splineToSplineHeading(new Pose2d(p.x, p.y, Math.toRadians(p.heading)), Math.toRadians(endTan))
                    );
                } catch (NumberFormatException ignored) {}
            }
        }
    }
    return null;
}
```

### Caching Helper

```java
private Action createCachedBuilderAction(BuilderAction delegate) {
    return new Action() {
        private com.acmerobotics.roadrunner.Action cachedAction;

        @Override public boolean run() {
            if (cachedAction == null) {
                // Build ONCE on first run — uses CURRENT robot pose
                cachedAction = delegate.apply(
                    drive.actionBuilder(drive.localizer.getPose())
                ).build();
            }
            return cachedAction.run(new TelemetryPacket());
        }
    };
}
```

> **Why cache?** Actions inside `if/else` aren't fused by top-level `merge()`. Caching builds the trajectory at **branch entry time** (current pose), not parse time.

---

## ActionUtils: The Glue Layer

### BuilderAction Interface
```java
public interface BuilderAction extends Action {
    TrajectoryActionBuilder apply(TrajectoryActionBuilder builder);
}
```
Dual purpose: runs standalone (caches trajectory) OR fuses via `.apply(builder)`.

### Core Utilities

```java
// Wrap RR Action → InstantAuto Action
public static Action wrap(com.acmerobotics.roadrunner.Action rrAction) {
    return new WrappedRRAction(rrAction);
}

// Adapt InstantAuto Action → RR Action
public static com.acmerobotics.roadrunner.Action adapt(Action action, Telemetry telemetry) {
    if (action instanceof WrappedRRAction) return ((WrappedRRAction) action).getRRAction();
    return new com.acmerobotics.roadrunner.Action() {
        @Override public boolean run(TelemetryPacket packet) { return action.run(); }
    };
}

// Parse params with variable resolution: "30,0,0" OR "myPose"
public static double[] asDoubles(Object params, int count) { ... }

// Parse action string recursively: "ACTION1, ACTION2" → List<Action>
public static List<Action> asActions(Object params, MecanumDrive drive) { ... }

// Top-level merge: fuse consecutive BuilderActions
public static List<Action> merge(List<Action> actions, MecanumDrive drive) { ... }

// Deep merge: recursively fuse nested actions (if/else, parallel, race)
public static List<Action> mergeNestedActions(List<Action> actions, MecanumDrive drive) { ... }

// Fuse a group of BuilderActions into one trajectory
private static Action fuse(List<BuilderAction> group, MecanumDrive drive) {
    TrajectoryActionBuilder builder = drive.actionBuilder(drive.localizer.getPose());
    for (BuilderAction ba : group) builder = ba.apply(builder);
    return wrap(builder.build());
}
```

---

## AutonomousBase Execution Flow

```java
@Override
public void start() {
    // 1. Re-parse with MecanumDrive for fusion
    List<Action> merged = ActionUtils.asActions(parser.getActionContent(), drive);
    
    // 2. Deep merge (handles if/else branches, parallel, race)
    merged = ActionUtils.mergeNestedActions(merged, drive);
    
    // 3. Adapt each to RR Action
    List<com.acmerobotics.roadrunner.Action> rrActions = merged.stream()
        .map(a -> ActionUtils.adapt(a, telemetry))
        .collect(Collectors.toList());
    
    // 4. Execute via RR
    Actions.runBlocking(
        new RaceAction(  // Runs all simultaneously
            new SequentialAction(rrActions)  // But our list runs sequentially
        )
    );
}
```

> **Why re-parse in `start()`?** `init()` parses without `MecanumDrive` (not ready yet). `start()` has `drive` → can fuse trajectories using actual pose.

---

## Key Integration Points

| Component | Responsibility |
|-----------|----------------|
| `ConfigManager` | Registers types, fields, suppliers, conditions |
| `ActionManager` | Registers MiniActions with RR factories |
| `ActionUtils` | Parses, merges, adapts, wraps actions |
| `AutonomousBase` | Orchestrates parse → init → execute |
| `MetaFieldRegistry` | Central variable store (static + suppliers) |
| `UserActionRegistry` | Action registry + condition evaluation |

---

## Common Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| Trajectory rebuilds every loop | Missing `createCachedBuilderAction` | Wrap factory return with caching |
| Wrong pose in if/else branch | Cached at parse time | Cache uses `drive.localizer.getPose()` at **first run** |
| Actions don't fuse | `mergeNestedActions` not called | Call in `start()` after `asActions()` |
| Supplier captures old OpMode | Not cleared in `stop()` | `MetaFieldRegistry.clear()` + `UserActionRegistry.clear()` |

---

## Next: [Variables, Conditions & Hardware →](variables-conditions.md)