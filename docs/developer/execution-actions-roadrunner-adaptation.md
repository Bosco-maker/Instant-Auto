# Execution (Page 2): ActionManager, ActionUtils — Custom Actions & RoadRunner Adaptation

## Overview

This page covers the **TeamCode-side execution layer**: `ActionManager` (registers primitives) and `ActionUtils` (utilities for building, merging, and adapting actions). This is where InstantAuto meets RoadRunner.

---

## ActionManager (`org.firstinspires.ftc.teamcode.action.ActionManager`)

### Purpose
Initializes and registers all **MiniActions** (primitives) that text-file actions can call. Bridges Java/RoadRunner capabilities to the InstantAuto action system.

### Initialization

```java
public class ActionManager {
    MecanumDrive mecanumDrive;
    Telemetry telemetry;

    public void init(MecanumDrive drivebase, Telemetry telemetry) {
        this.mecanumDrive = drivebase;
        this.telemetry = telemetry;

        // Register primitives
        UserActionRegistry.register(new MiniAction("STRAFE.TO", this::strafeToFactory));
        UserActionRegistry.register(new MiniAction("SPLINE.TO", this::splineToFactory));
        UserActionRegistry.register(new MiniAction("PRINT", obj -> ActionUtils.wrap(new PrintAction(obj))));
        UserActionRegistry.register(new MiniAction("PARALLEL", params -> { ... }));
        UserActionRegistry.register(new MiniAction("RACE", params -> { ... }));
        UserActionRegistry.register(new MiniAction("WAIT", params -> { ... }));
        UserActionRegistry.register(new MiniAction("HELLO.WORLD", params -> ActionUtils.wrap(new PrintAction("Hello World!"))));
    }
}
```

### Primitive Factory Methods

#### STRAFE.TO Factory
```java
private Action strafeToFactory(Object params) {
    // Case 1: Variable reference
    if (params instanceof String) {
        String varName = (String) params;
        ConfigEntry<?> entry = MetaFieldRegistry.getEntry(varName);
        if (entry != null && entry.getValue() instanceof Pose2d) {
            final Pose2d p = (Pose2d) entry.getValue();
            BuilderAction ba = builder -> 
                builder.strafeToSplineHeading(new Vector2d(p.x, p.y), Math.toRadians(p.heading));
            return createCachedBuilderAction(ba);
        }
    }
    // Case 2: Literal "x, y, heading"
    double[] d = ActionUtils.asDoubles(params, 3);
    if (d != null) {
        BuilderAction ba = builder -> 
            builder.strafeToSplineHeading(new Vector2d(d[0], d[1]), Math.toRadians(d[2]));
        return createCachedBuilderAction(ba);
    }
    return null;
}
```

#### SPLINE.TO Factory
```java
private Action splineToFactory(Object params) {
    if (params instanceof String) {
        String s = (String) params;
        String[] parts = s.split(",");

        // Case 1: "x, y, heading, startTan, endTan" (5 params)
        if (parts.length == 5) {
            double[] d = ActionUtils.asDoubles(s, 5);
            if (d != null) {
                BuilderAction ba = builder -> builder
                    .setTangent(Math.toRadians(d[3]))
                    .splineToSplineHeading(new com.acmerobotics.roadrunner.Pose2d(d[0], d[1], Math.toRadians(d[2])), Math.toRadians(d[4]));
                return createCachedBuilderAction(ba);
            }
        }

        // Case 2: "poseName, startTan, endTan" (3 params)
        if (parts.length == 3) {
            String poseName = parts[0].trim();
            ConfigEntry<?> entry = MetaFieldRegistry.getEntry(poseName);
            if (entry != null && entry.getValue() instanceof Pose2d) {
                Pose2d p = (Pose2d) entry.getValue();
                try {
                    double startTan = Double.parseDouble(parts[1].trim());
                    double endTan = Double.parseDouble(parts[2].trim());
                    BuilderAction ba = builder -> builder
                        .setTangent(Math.toRadians(startTan))
                        .splineToSplineHeading(new com.acmerobotics.roadrunner.Pose2d(p.x, p.y, Math.toRadians(p.heading)), Math.toRadians(endTan));
                    return createCachedBuilderAction(ba);
                } catch (NumberFormatException ignored) {}
            }
        }
    }
    return null;
}
```

### Caching Helper (Essential for if/else)

```java
private Action createCachedBuilderAction(BuilderAction delegate) {
    return new Action() {
        private com.acmerobotics.roadrunner.Action cachedAction;

        @Override public boolean run() {
            if (cachedAction == null) {
                cachedAction = delegate.apply(
                    mecanumDrive.actionBuilder(mecanumDrive.localizer.getPose())
                ).build();
            }
            return cachedAction.run(new TelemetryPacket());
        }
    };
}
```

---

## ActionUtils (`org.firstinspires.ftc.teamcode.action.ActionUtils`)

### BuilderAction Interface

```java
public interface BuilderAction extends Action {
    /** Modifies the trajectory builder; called during fusion */
    TrajectoryActionBuilder apply(TrajectoryActionBuilder builder);
}
```

- **Dual purpose**:
    1. Can run standalone (builds trajectory on first run)
    2. Can be fused (`.apply(builder)` chains segments)

### Core Utilities

#### 1. Wrapping RoadRunner Actions

```java
// RR Action → InstantAuto Action
public static com.example.instantauto.actions.Action wrap(com.acmerobotics.roadrunner.Action rrAction) {
    return new WrappedRRAction(rrAction);
}

// InstantAuto Action → RR Action
public static com.acmerobotics.roadrunner.Action adapt(
        final com.example.instantauto.actions.Action action, 
        final Telemetry telemetry) {
    if (action instanceof WrappedRRAction) {
        return ((WrappedRRAction) action).getRRAction();
    }
    return new com.acmerobotics.roadrunner.Action() {
        @Override public boolean run(TelemetryPacket packet) {
            return action.run();
        }
    };
}
```

#### 2. Parsing Parameters with Variable Resolution

```java
// "30, 0, 0" → [30, 0, 0]
// "myPose" → resolves myPose (Pose2d) → [x, y, heading]
public static double[] asDoubles(Object params, int count) {
    if (params instanceof String) {
        String s = (String) params;
        String[] parts = s.split(",");
        if (parts.length != count) return null;
        double[] result = new double[count];
        for (int i = 0; i < count; i++) {
            String part = parts[i].trim();
            try {
                result[i] = Double.parseDouble(part);
            } catch (NumberFormatException e) {
                // Try variable lookup
                ConfigEntry<?> entry = MetaFieldRegistry.getEntry(part);
                if (entry != null && entry.getValue() instanceof Number) {
                    result[i] = ((Number) entry.getValue()).doubleValue();
                } else return null;
            }
        }
        return result;
    }
    return null;
}
```

#### 3. Parsing Action Strings Recursively

```java
// "ACTION1(p1), ACTION2(p2)" → List<Action>
public static List<Action> asActions(Object params, MecanumDrive drive) {
    if (params instanceof String) {
        List<String> subActions = UserActionRegistry.splitByTopLevelCommas((String) params);
        List<Action> actions = new ArrayList<>();
        for (String sub : subActions) {
            Action a = UserActionRegistry.createAction(sub);
            if (a != null) actions.add(a);
        }
        return merge(actions, drive);  // Fuse consecutive BuilderActions
    }
    return null;
}
```

#### 4. Merging (Trajectory Fusion)

```java
// Top-level merge
static List<Action> merge(List<Action> actions, MecanumDrive drive) {
    List<Action> merged = new ArrayList<>();
    List<BuilderAction> group = new ArrayList<>();
    for (Action a : actions) {
        if (a instanceof BuilderAction) group.add((BuilderAction) a);
        else {
            if (!group.isEmpty()) { merged.add(fuse(group, drive)); group.clear(); }
            merged.add(a);
        }
    }
    if (!group.isEmpty()) merged.add(fuse(group, drive));
    return merged;
}

// Recursive nested merge (handles if/else, parallel, race)
public static List<Action> mergeNestedActions(List<Action> actions, MecanumDrive drive) {
    // 1. Recursively process each action's nested actions via reflection
    // 2. Then merge at this level
    // 3. Returns fully fused action list
}
```

#### 5. Fusing BuilderActions

```java
private static Action fuse(List<BuilderAction> group, MecanumDrive drive) {
    TrajectoryActionBuilder builder = drive.actionBuilder(drive.localizer.getPose());
    for (BuilderAction ba : group) {
        builder = ba.apply(builder);  // Chain: builder = ba1.apply(ba2.apply(...))
    }
    return wrap(builder.build());  // Single RR Action wrapped as InstantAuto Action
}
```

---

## Creating Custom Actions

### Type 1: Simple InstantAuto Action (No RR)

```java
// In ActionManager.init():
UserActionRegistry.register(new MiniAction("MY.ACTION", params -> {
    return new Action() {
        private int state = 0;
        @Override public boolean run() {
            if (state == 0) { /* init */ state++; return true; }
            if (state == 1) { /* work */ state++; return true; }
            return false; // done
        }
    };
}));
```

**Text file usage:** `MY.ACTION()`

---

### Type 2: RoadRunner Action via Wrapping

```java
UserActionRegistry.register(new MiniAction("RR.ACTION", params -> {
    com.acmerobotics.roadrunner.Action rrAction = drive.actionBuilder(pose)
        .strafeTo(new Vector2d(10, 10))
        .build();
    return ActionUtils.wrap(rrAction);  // Wrap RR Action
}));
```

---

### Type 3: BuilderAction (Fusible Trajectory)

```java
UserActionRegistry.register(new MiniAction("FUSIBLE.ACTION", params -> {
    ActionUtils.BuilderAction ba = new ActionUtils.BuilderAction() {
        @Override public TrajectoryActionBuilder apply(TrajectoryActionBuilder builder) {
            return builder.strafeTo(new Vector2d(10, 10))
                          .turn(Math.toRadians(90));
        }
        @Override public boolean run() {
            // Standalone execution (with caching!)
            return apply(drive.actionBuilder(drive.localizer.getPose())).build()
                       .run(new TelemetryPacket());
        }
    };
    // Wrap with caching for if/else safety
    return createCachedBuilderAction(ba);
}));
```

**Key**: Implement `BuilderAction` + use `createCachedBuilderAction()` for caching.

---

### Type 4: Composite Action (PARALLEL/RACE style)

```java
UserActionRegistry.register(new MiniAction("SEQUENTIAL", params -> {
    List<Action> actions = ActionUtils.asActions(params, drive);
    if (actions == null) return null;
    List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
        .map(a -> ActionUtils.adapt(a, telemetry))
        .collect(Collectors.toList());
    return ActionUtils.wrap(new com.acmerobotics.roadrunner.SequentialAction(rrActions));
}));
```

---

## Adapting RoadRunner Actions into Ecosystem

### Direct RR Action Registration

```java
// Any RR Action can be wrapped and registered
UserActionRegistry.register(new MiniAction("DRIVE.TO", params -> {
    double[] d = ActionUtils.asDoubles(params, 3);
    if (d != null) {
        com.acmerobotics.roadrunner.Action rr = drive.actionBuilder(drive.localizer.getPose())
            .strafeTo(new Vector2d(d[0], d[1]))
            .build();
        return ActionUtils.wrap(rr);
    }
    return null;
}));
```

### Using RR Composite Actions

```java
UserActionRegistry.register(new MiniAction("PARALLEL", params -> {
    List<Action> actions = ActionUtils.asActions(params, drive);
    List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
        .map(a -> ActionUtils.adapt(a, telemetry))
        .collect(Collectors.toList());
    return ActionUtils.wrap(new com.acmerobotics.roadrunner.ParallelAction(rrActions));
}));

UserActionRegistry.register(new MiniAction("RACE", params -> {
    List<Action> actions = ActionUtils.asActions(params, drive);
    List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
        .map(a -> ActionUtils.adapt(a, telemetry))
        .collect(Collectors.toList());
    return ActionUtils.wrap(new RaceAction(rrActions));  // Custom RR Action
}));
```

### Custom RR Action (RaceAction Example)

```java
public static class RaceAction implements com.acmerobotics.roadrunner.Action {
    private final List<com.acmerobotics.roadrunner.Action> actions;
    public RaceAction(List<com.acmerobotics.roadrunner.Action> actions) { this.actions = actions; }

    @Override public boolean run(TelemetryPacket packet) {
        boolean allRunning = true;
        for (com.acmerobotics.roadrunner.Action a : actions) {
            if (!a.run(packet)) allRunning = false;
        }
        return allRunning && !actions.isEmpty();
    }
}
```

---

## Text File Usage Examples

```ini
# Simple primitives
STRAFE.TO(30, 0, 0)
SPLINE.TO(0, 0, 0, 90, -90)
WAIT(0.5)
PRINT("Hello")

# Variable references
STRAFE.TO(redGoalPose)
SPLINE.TO(scorePose, 45, -45)

# Composites (from UserActionSettings.txt)
SCORE_SAMPLE

# Control flow
if (isBlue) {
    STRAFE.TO(blueGoalPose)
} else {
    STRAFE.TO(redGoalPose)
}

# Parallel/Race
PARALLEL(STRAFE.TO(10, 0, 0), SPIN.TO(90))
RACE(STRAFE.TO(20, 0, 0), WAIT(2.0))
```

---

## Files Reference

| File | Location |
|------|----------|
| `ActionManager.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/action/` |
| `ActionUtils.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/action/` |
| `AutonomousBase.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/` |