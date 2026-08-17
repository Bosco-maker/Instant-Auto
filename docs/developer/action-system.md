# Action System (MiniAction, UserAction & UserActionRegistry)

## Overview

The **Action System** is the executable heart of InstantAuto. It transforms parsed text into runnable `Action` objects that integrate with RoadRunner's `Action` interface. The system supports three layers:

1. **Primitive Actions** (`Action` interface) — Basic runnable units
2. **MiniActions** (`MiniAction`) — Java-defined parameterized primitives (e.g., `STRAFE.TO`, `WAIT`)
3. **UserActions** (`UserAction`) — Text-file-defined composites of MiniActions (e.g., `SCORE_SAMPLE`)

All are unified under the `MetaAction` interface and managed by `UserActionRegistry`.

---

## Core Interfaces

### Action (`com.example.instantauto.actions.Action`)

```java
public interface Action {
    /** 
     * Executes one step of the action.
     * @return true if action should continue running next loop; false if complete
     */
    boolean run();
}
```

- **RoadRunner-compatible**: Return `true` = continuing, `false` = done
- **Stateless by design**: State must be encapsulated in the implementation
- **Single method**: Simple, functional, composable

---

### MetaAction (`com.example.instantauto.actions.MetaAction`)

Factory interface for creating `Action` instances from parameters:

```java
public interface MetaAction {
    /** @return Identifier used in text files (e.g., "STRAFE.TO") */
    String getIdentifier();

    /** Create Action from parsed parameter object */
    Action create(Object params);

    /** Create Action from raw parameter string */
    Action create(String params);
}
```

- **Two create() overloads**: String (from text parsing) and Object (from variable resolution)
- **Registry key**: `getIdentifier().toUpperCase()` used for lookup

---

## MiniAction (`com.example.instantauto.actions.MiniAction`)

### Purpose
Java-defined primitive actions with parameterized factories. The building blocks for text-file compositions.

### Implementation

```java
public class MiniAction implements MetaAction {
    private final String id;
    private final Function<Object, Action> factory;

    public MiniAction(String id, Function<Object, Action> factory) {
        this.id = id;
        this.factory = factory;
    }

    @Override public String getIdentifier() { return id; }

    @Override public Action create(String params) {
        return factory.apply(params);
    }

    @Override public Action create(Object params) {
        return factory.apply(params);
    }
}
```

### Registration (in `ActionManager.init()`)

```java
UserActionRegistry.register(new MiniAction("STRAFE.TO", this::strafeToFactory));
UserActionRegistry.register(new MiniAction("SPLINE.TO", this::splineToFactory));
UserActionRegistry.register(new MiniAction("PRINT", obj -> ActionUtils.wrap(new PrintAction(obj))));
UserActionRegistry.register(new MiniAction("WAIT", params -> {
    double[] d = ActionUtils.asDoubles(params, 1);
    return d != null ? ActionUtils.wrap(new SleepAction(d[0])) : null;
}));
UserActionRegistry.register(new MiniAction("PARALLEL", params -> {
    List<Action> actions = ActionUtils.asActions(params, mecanumDrive);
    List<com.acmerobotics.roadrunner.Action> rrActions = actions.stream()
        .map(a -> ActionUtils.adapt(a, telemetry)).collect(Collectors.toList());
    return ActionUtils.wrap(new ParallelAction(rrActions));
}));
```

### Factory Pattern

Factories receive the **raw parameter object** (String or resolved variable) and return an `Action`:

```java
private Action strafeToFactory(Object params) {
    // Case 1: Variable reference (String containing variable name)
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

    // Case 2: Literal parameters "x, y, heading"
    final double[] d = ActionUtils.asDoubles(params, 3);
    if (d != null) {
        return createCachedBuilderAction(builder -> 
            builder.strafeToSplineHeading(new Vector2d(d[0], d[1]), Math.toRadians(d[2]))
        );
    }
    return null;
}
```

### Caching Trajectories (Critical for if/else)

```java
private Action createCachedBuilderAction(BuilderAction delegate) {
    return new Action() {
        private com.acmerobotics.roadrunner.Action cachedAction;

        @Override public boolean run() {
            if (cachedAction == null) {
                // Build ONCE on first run
                cachedAction = delegate.apply(
                    mecanumDrive.actionBuilder(mecanumDrive.localizer.getPose())
                ).build();
            }
            return cachedAction.run(new TelemetryPacket());
        }
    };
}
```

> **Why cache?** Actions inside `if/else` blocks aren't fused by `merge()`. Without caching, the trajectory will be divided into individual motions instead of a single continuous one.

---

## UserAction (`com.example.instantauto.actions.UserAction`)

### Purpose
Text-file-defined composite actions ("Big Actions") composed of multiple MiniActions/UserActions.

### Definition in UserActionSettings.txt

```ini
# Syntax: ACTION_NAME = { ACTION1(params), ACTION2(params), ... }
SCORE_SAMPLE = {
    SPLINE.TO(scorePose, 90, -90),
    WAIT(0.3),
    INTAKE.CLOSE,
    STRAFE.TO(0, 0, 0)
}

PARK = STRAFE.TO(parkPose), WAIT(1.0)
```

### Parsing (`UserActionRegistry.loadSettings()`)

1. Reads file line by line
2. Detects `NAME = { ... }` patterns (handles multi-line with brace counting)
3. Extracts content between outermost braces
4. Splits by top-level commas (respects parentheses, braces, quotes)
5. Validates each sub-action via `createAction()`
6. Creates `UserAction` with list of sub-action strings

### Execution

```java
@Override
public Action create(Object params) {
    if (hasError) return failingAction;

    // Convert sub-action strings to Actions ONCE at creation
    List<Action> actions = new ArrayList<>();
    for (String line : subActionLines) {
        Action a = UserActionRegistry.createAction(line);
        if (a != null) actions.add(a);
    }

    return new Action() {
        private int currentIndex = 0;

        @Override
        public boolean run() {
            if (currentIndex >= actions.size()) return false;
            
            Action current = actions.get(currentIndex);
            if (current == null || !current.run()) {
                currentIndex++;  // Advance when sub-action completes
            }
            return currentIndex < actions.size();  // Continue if more actions
        }
    };
}
```

### Sequential Execution Semantics

- Actions run **sequentially** (not parallel)
- Each sub-action runs to completion (`run()` returns `false`) before next starts
- `currentIndex` tracks progress across loop iterations

---

## UserActionRegistry (`com.example.instantauto.actions.UserActionRegistry`)

### Central Registry

```java
private static final Map<String, MetaAction> registry = new HashMap<>();
private static final Map<String, BooleanSupplier> conditionSuppliers = new HashMap<>();
private static final List<String> loadErrors = new ArrayList<>();
private static Function<List<Action>, List<Action>> actionMerger = actions -> actions;
```

### Key Methods

| Method | Purpose |
|--------|---------|
| `register(MetaAction)` | Register MiniAction or UserAction |
| `loadSettings(String)` | Parse UserActionSettings.txt |
| `createAction(String)` | Parse line → Action (assignments, if/else, primitives) |
| `evaluateCondition(String)` | Resolve condition for if/else |
| `setActionMerger(Function)` | Set nested action merger (for trajectory fusion) |
| `splitByTopLevelCommas(String)` | Utility: split respecting nesting |
| `getLoadErrors()` | Diagnostic errors from settings parse |

### Action Creation Pipeline (`createAction(String line)`)

```
Input: "STRAFE.TO(30, 0, 0)"
         │
         ▼
┌─────────────────────────────────────┐
│ 1. Variable Assignment? (var = val) │
└─────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│ 2. If/Else Block?                   │
└─────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│ 3. Standard Action: NAME(params)    │
│    → Lookup in registry             │
│    → meta.create(params)            │
└─────────────────────────────────────┘
```

### Variable Assignment Actions

```ini
scorePose = pose2d(-72, -67, 0)
intakeActive = intakeSetting("REVERSE", false, 0.5)
```

Creates an `Action` that on `run()`:
1. Parses value via `parseValue()`
2. Updates `MetaFieldRegistry` entry via `setValue()`
3. Returns `false` (instant completion)

### If/Else Actions

```ini
if (isBlue) {
    STRAFE.TO(blueGoalPose),
    SCORE
} else {
    STRAFE.TO(redGoalPose),
    SCORE
}
```

Creates an `Action` that:
1. Evaluates condition **once** on first `run()` (lazy initialization)
2. Selects true/false branch action list
3. Executes selected branch sequentially
4. Supports `else if` chains via recursive `createAction()`

### Condition Evaluation (`evaluateCondition`)

```java
public static boolean evaluateCondition(String condition) {
    // 1. Registered BooleanSupplier (highest priority, unchangeable)
    BooleanSupplier supplier = conditionSuppliers.get(condition.toLowerCase());
    if (supplier != null) return supplier.getAsBoolean();

    // 2. Boolean variable from MetaFieldRegistry
    ConfigEntry<?> entry = MetaFieldRegistry.getEntry(condition);
    if (entry != null && entry.getValue() instanceof Boolean) {
        return (Boolean) entry.getValue();
    }
    return false;
}
```

**Priority**: Registered conditions > Variables > `false`

### Registering Conditions (in ConfigManager)

```java
// Static condition (always true)
UserActionRegistry.registerCondition("is_active", () -> true);

// Dynamic sensor condition
UserActionRegistry.registerCondition("withinDistance", () -> 
    opMode.hardwareMap.get(DistanceSensor.class, "distanceSensor")
        .getDistance(DistanceUnit.CM) <= 20.0
);
```

---

## Action String Syntax Reference

### Primitive Action Call
```
ACTION_NAME(param1, param2, ...)
STRAFE.TO(30, 0, 0)
PRINT("Hello World")
WAIT(0.5)
```

### Variable Assignment
```
variableName = value
myPose = pose2d(10, 20, 90)
speed = 1.5
enabled = true
```

### Conditional Block
```
if (condition) {
    ACTION1(params),
    ACTION2(params)
} else {
    ACTION3(params)
}
```

### Composite Action (UserAction)
```
COMPOSITE_NAME
SCORE_SAMPLE
```

### Parameter Resolution
| Input | Resolved To |
|-------|-------------|
| `30` | Literal double |
| `"text"` | Literal String |
| `true` | Literal boolean |
| `myVariable` | `MetaFieldRegistry.getEntry("myVariable").getValue()` |
| `pose2d(0,0,0)` | Parsed MetaField object |

---

## Nested Action Merging (Trajectory Fusion)

### Problem
Consecutive `BuilderAction` (trajectory-building) actions should be fused into **one continuous trajectory** for smooth motion.

### Solution: `actionMerger` Callback

```java
// In AutonomousBase.init() - AFTER ActionManager.init()
UserActionRegistry.setActionMerger(actions -> 
    ActionUtils.mergeNestedActions(actions, mecanumDrive)
);
```

### Merge Process (`ActionUtils.mergeNestedActions`)

```java
public static List<Action> mergeNestedActions(List<Action> actions, MecanumDrive drive) {
    // 1. Recursively process each action for nested actions
    // 2. Group consecutive BuilderActions
    // 3. Fuse each group into single trajectory
    // 4. Return merged list
}

private static Action fuse(List<BuilderAction> group, MecanumDrive drive) {
    TrajectoryActionBuilder builder = drive.actionBuilder(drive.localizer.getPose());
    for (BuilderAction ba : group) {
        builder = ba.apply(builder);  // Chain trajectory segments
    }
    return wrap(builder.build());  // Wrap as single Action
}
```

### Recursive Merging
Handles nested actions in:
- Top-level action list
- `if/else` branches (`trueActions`, `targetActions` fields via reflection)
- `PARALLEL` / `RACE` sub-actions
- Nested `UserAction` expansions

---

## Files Reference

| File | Location |
|------|----------|
| `Action.java` | `instantauto/src/main/java/com/example/instantauto/actions/` |
| `MetaAction.java` | `instantauto/src/main/java/com/example/instantauto/actions/` |
| `MiniAction.java` | `instantauto/src/main/java/com/example/instantauto/actions/` |
| `UserAction.java` | `instantauto/src/main/java/com/example/instantauto/actions/` |
| `UserActionRegistry.java` | `instantauto/src/main/java/com/example/instantauto/actions/` |
| `ActionManager.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/action/` |
| `ActionUtils.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/action/` |