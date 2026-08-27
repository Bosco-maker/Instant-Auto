# Execution (Page 2): Actions & RoadRunner Adaptation

## Overview

This page details how **InstantAuto Actions** integrate with **RoadRunner 1.0** — from primitive action factories to trajectory fusion, caching, and the adapters that bridge both ecosystems.

---

## Action Hierarchy

```
Text File                          Java Code
──────────────────────────────────────────────────────────
STRAFE.TO(30,0,0)      ──►  MiniAction("STRAFE.TO", factory)
                              │
                              ▼
                    Factory returns Action
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
       BuilderAction    WrappedRRAction    SimpleAction
       (fusable)        (wraps RR Action)  (instant)
```

---

## Action Types

### 1. BuilderAction (Trajectory Actions)

**Purpose**: Drive actions that build RoadRunner trajectories and support **fusion** (chaining).

```java
public interface BuilderAction extends Action {
    /** Apply this segment to a trajectory builder */
    TrajectoryActionBuilder apply(TrajectoryActionBuilder builder);
}
```

**Dual nature**: 
- **Standalone**: `run()` → builds & caches trajectory on first call
- **Fused**: `apply(builder)` → chains segment into larger trajectory

### 2. WrappedRRAction (RoadRunner Actions)

**Purpose**: Wraps any `com.acmerobotics.roadrunner.Action` as an InstantAuto `Action`.

```java
public class WrappedRRAction implements Action {
    private final com.acmerobotics.roadrunner.Action rrAction;
    
    public WrappedRRAction(com.acmerobotics.roadrunner.Action action) {
        this.rrAction = action;
    }
    
    @Override public boolean run() {
        return rrAction.run(new TelemetryPacket());
    }
    
    public com.acmerobotics.roadrunner.Action getRRAction() { return rrAction; }
}
```

### 3. Simple Action (Instant Complete)

**Purpose**: Non-trajectory actions that complete in one loop.

```java
public class PrintAction implements Action {
    private final String message;
    public PrintAction(Object obj) { this.message = ActionUtils.asString(obj); }
    
    @Override public boolean run() {
        System.out.println(message);
        return false;  // Instant complete
    }
}
```

---

## Caching: Critical for if/else Branches

### Problem

Actions inside `if/else` branches are **not fused** by top-level `merge()`. Without caching, they execute as individual motions (stuttering).

### Solution: Cache on First Run

```java
private Action createCachedBuilderAction(BuilderAction delegate) {
    return new Action() {
        private com.acmerobotics.roadrunner.Action cachedAction;

        @Override public boolean run() {
            if (cachedAction == null) {
                // Build ONCE using CURRENT robot pose
                cachedAction = delegate.apply(
                    mecanumDrive.actionBuilder(mecanumDrive.localizer.getPose())
                ).build();
            }
            return cachedAction.run(new TelemetryPacket());
        }
    };
}
```

**Usage in factory:**
```java
private Action strafeToFactory(Object params) {
    // Resolve params...
    return createCachedBuilderAction(builder -> 
        builder.strafeToSplineHeading(new Vector2d(x, y), Math.toRadians(heading))
    );
}
```

> **Key**: `mecanumDrive.localizer.getPose()` gives the **live pose at branch entry**, not parse time.

---

## Trajectory Fusion (merge / mergeNestedActions)

### Top-Level Merge (`ActionUtils.merge`)

Fuses **consecutive** `BuilderAction` instances in a flat list:

```java
public static List<Action> merge(List<Action> actions, MecanumDrive drive) {
    List<Action> result = new ArrayList<>();
    List<BuilderAction> currentGroup = new ArrayList<>();

    for (Action action : actions) {
        if (action instanceof BuilderAction) {
            currentGroup.add((BuilderAction) action);
        } else {
            if (!currentGroup.isEmpty()) {
                result.add(fuse(currentGroup, drive));  // Fuse group
                currentGroup.clear();
            }
            result.add(action);
        }
    }
    if (!currentGroup.isEmpty()) result.add(fuse(currentGroup, drive));

    return result;
}

private static Action fuse(List<BuilderAction> group, MecanumDrive drive) {
    TrajectoryActionBuilder builder = drive.actionBuilder(drive.localizer.getPose());
    for (BuilderAction ba : group) builder = ba.apply(builder);
    return wrap(builder.build());
}
```

### Deep Merge (`ActionUtils.mergeNestedActions`)

Recursively merges inside **nested actions** — if/else branches, PARALLEL, RACE, UserAction expansions:

```java
public static List<Action> mergeNestedActions(List<Action> actions, MecanumDrive drive) {
    // 1. Process each action for nested actions via reflection
    // 2. Recursively merge nested lists
    // 3. Fuse consecutive BuilderActions at each level
    // 4. Return fully merged action tree
}

// Reflection targets:
if (action.getClass().getName().contains("IfElseAction")) {
    // Look for trueActions, falseActions, targetActions fields
    // Recursively mergeNestedActions on each
}
if (action instanceof ParallelAction || action instanceof RaceAction) {
    // Merge subActions list
}
```

---

## Adaptation Layer (ActionUtils)

### `wrap()` - RR Action → InstantAuto Action

```java
public static Action wrap(com.acmerobotics.roadrunner.Action rrAction) {
    return new WrappedRRAction(rrAction);
}
```

### `adapt()` - InstantAuto Action → RR Action

```java
public static com.acmerobotics.roadrunner.Action adapt(Action action, Telemetry telemetry) {
    if (action instanceof WrappedRRAction) {
        return ((WrappedRRAction) action).getRRAction();
    }
    return new com.acmerobotics.roadrunner.Action() {
        @Override public boolean run(TelemetryPacket packet) {
            return action.run();  // Delegate to InstantAuto action
        }
    };
}
```

### `asActions()` - Parse Action String → List<Action>

```java
public static List<Action> asActions(Object params, MecanumDrive drive) {
    if (params instanceof String) {
        String content = (String) params;
        List<String> subActionStrings = UserActionRegistry.splitByTopLevelCommas(content);
        List<Action> actions = new ArrayList<>();
        for (String sub : subActionStrings) {
            Action a = UserActionRegistry.createAction(sub);
            if (a != null) actions.add(a);
        }
        return actions;
    }
    return null;
}
```

### `asDoubles()` - Parse Parameters with Variable Resolution

```java
public static double[] asDoubles(Object params, int count) {
    if (params instanceof String) {
        String s = (String) params;
        String[] parts = s.split(",");
        if (parts.length != count) return null;
        
        double[] result = new double[count];
        try {
            for (int i = 0; i < count; i++) {
                String part = parts[i].trim();
                // Try literal double
                try { result[i] = Double.parseDouble(part); continue; }
                catch (NumberFormatException ignored) {}
                
                // Try variable lookup
                ConfigEntry<?> entry = MetaFieldRegistry.getEntry(part);
                if (entry != null && entry.getValue() instanceof Pose2d) {
                    // Special handling for Pose2d in multi-param context
                    // (usually handled at factory level, not here)
                }
            }
        } catch (Exception e) { return null; }
    }
    return null;
}
```

---

## Complete ActionManager Factory Patterns

### Movement Primitives (BuilderAction with Caching)

```java
// STRAFE.TO
private Action strafeToFactory(Object params) {
    // Variable reference
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
    // Literal params
    double[] d = ActionUtils.asDoubles(params, 3);
    if (d != null) {
        return createCachedBuilderAction(builder -> 
            builder.strafeToSplineHeading(new Vector2d(d[0], d[1]), Math.toRadians(d[2]))
        );
    }
    return null;
}

// SPLINE.TO
private Action splineToFactory(Object params) {
    if (params instanceof String) {
        String[] parts = ((String) params).split(",");
        
        // 5 params: x, y, heading, startTan, endTan
        if (parts.length == 5) {
            double[] d = ActionUtils.asDoubles(params, 5);
            if (d != null) {
                return createCachedBuilderAction(builder -> builder
                    .setTangent(Math.toRadians(d[3]))
                    .splineToSplineHeading(new Pose2d(d[0], d[1], Math.toRadians(d[2])), Math.toRadians(d[4]))
                );
            }
        }
        
        // 3 params: poseVar, startTan, endTan
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

### Control Flow (PARALLEL, RACE, WAIT)

```java
// WAIT
UserActionRegistry.register(new MiniAction("WAIT", params -> {
    double[] d = ActionUtils.asDoubles(params, 1);
    return d != null ? ActionUtils.wrap(new SleepAction(d[0])) : null;
}));

// PARALLEL
UserActionRegistry.register(new MiniAction("PARALLEL", params -> {
    List<Action> actions = ActionUtils.asActions(params, drive);
    List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
        .map(a -> ActionUtils.adapt(a, telemetry)).collect(Collectors.toList());
    return ActionUtils.wrap(new ParallelAction(rrActions));
}));

// RACE
UserActionRegistry.register(new MiniAction("RACE", params -> {
    List<Action> actions = ActionUtils.asActions(params, drive);
    List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
        .map(a -> ActionUtils.adapt(a, telemetry)).collect(Collectors.toList());
    return ActionUtils.wrap(new RaceAction(rrActions));
}));
```

---

## Execution Pipeline (AutonomousBase.start())

```java
@Override
public void start() {
    // 1. Re-parse with MecanumDrive for trajectory building
    List<Action> merged = ActionUtils.asActions(autoParser.getActionContent(), mecanumDrive);
    
    // 2. Deep merge (fuses nested actions in if/else, PARALLEL, RACE)
    merged = ActionUtils.mergeNestedActions(merged, mecanumDrive);
    
    // 3. Adapt each to RoadRunner Action
    List<com.acmerobotics.roadrunner.Action> rrActions = merged.stream()
        .map(a -> ActionUtils.adapt(a, telemetry))
        .collect(Collectors.toList());
    
    // 4. Execute sequentially via RR
    Actions.runBlocking(
        new RaceAction(new SequentialAction(rrActions))
    );
}
```

---

## Common Issues & Fixes

| Issue | Cause | Fix |
|-------|-------|-----|
| Trajectory rebuilds every loop | Missing `createCachedBuilderAction` | Wrap factory return with caching |
| Wrong pose in if/else branch | Cached at parse time | Cache uses `localizer.getPose()` at first run |
| Actions don't fuse | `mergeNestedActions` not called | Call in `start()` after `asActions()` |
| Stuttering at segment boundaries | Not using `BuilderAction.apply()` | Return `BuilderAction` from factory |
| Supplier captures old OpMode | Not cleared in `stop()` | `MetaFieldRegistry.clear()` + `UserActionRegistry.clear()` |

---

## Files Reference

| File | Location |
|------|----------|
| `ActionUtils.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/action/` |
| `ActionManager.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/action/` |
| `AutonomousBase.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/` |
| `BuilderAction` | `com.example.instantauto.actions.BuilderAction` |