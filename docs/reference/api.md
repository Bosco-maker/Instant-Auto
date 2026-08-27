# API Reference (Complete)

Complete API reference for core InstantAuto classes. All classes are in package `com.example.instantauto` unless noted.

---

## Package: `com.example.instantauto.configs`

### `MetaField<T>`

Interface defining a custom data type parsable from text files.

| Method | Signature | Description |
|--------|-----------|-------------|
| `getIdentifier` | `String getIdentifier()` | Text file identifier (e.g., `"pose2d"`, `"intakeSetting"`) |
| `getParamTypes` | `Class<?>[] getParamTypes()` | Parameter types in constructor order (e.g., `[double.class, double.class, double.class]`) |

**Implementations:**
- `org.firstinspires.ftc.teamcode.configs.Pose2d`
- `org.firstinspires.ftc.teamcode.configs.IntakeSetting`

---

### `MetaFieldRegistry`

Central registry for all configuration fields and type definitions.

#### Field Registration

| Method | Signature | Description |
|--------|-----------|-------------|
| `registerType` | `static void registerType(MetaField<?> type)` | Register a MetaField type definition |
| `registerField` | `static <T> void registerField(String name, Class<T> type, T defaultValue)` | Register field with static default value |
| `registerField` | `static <T> void registerField(String name, Class<T> type, Supplier<T> supplier)` | Register field with dynamic supplier |
| `registerSupplier` | `static <T> void registerSupplier(String name, Class<T> type, Supplier<T> supplier)` | Alias for `registerField` with supplier |

#### Field Access

| Method | Signature | Description |
|--------|-----------|-------------|
| `getEntry` | `static ConfigEntry<?> getEntry(String name)` | Get `ConfigEntry` by name (case-insensitive) |
| `getValue` | `static Object getValue(String name)` | Convenience: `getEntry(name).getValue()` |
| `getAllRegisteredFieldNames` | `static List<String> getAllRegisteredFieldNames()` | All registered field names |

#### Lifecycle

| Method | Signature | Description |
|--------|-----------|-------------|
| `clear` | `static void clear()` | Clear all entries and type definitions (call in `stop()`) |

---

### `MetaFieldRegistry.ConfigEntry<T>`

Wrapper for a registered field supporting static values or dynamic suppliers.

| Constructor | Signature | Mode |
|-------------|-----------|------|
| Static | `ConfigEntry(String fieldName, Class<T> type, T defaultValue)` | Static value |
| Supplier | `ConfigEntry(String fieldName, Class<T> type, Supplier<T> supplier)` | Dynamic supplier |

| Method | Signature | Description |
|--------|-----------|-------------|
| `getValue` | `T getValue()` | Returns value (calls supplier if dynamic) |
| `setValue` | `void setValue(T newValue)` | Sets static value, clears supplier |
| `fieldName` | `String fieldName` | Public field: name |
| `type` | `Class<T> type` | Public field: declared type |

---

### `ConfigParser`

Parses configuration files (`key = value` format).

| Method | Signature | Description |
|--------|-----------|-------------|
| `parseConfig` | `void parseConfig(String filePath)` | Parse config file (GeneralRobotSettings.txt) |
| `handleConfigLine` | `void handleConfigLine(String line, int lineNumber)` | Parse single `key=value` line |
| `parseAutoConfig` | `void parseAutoConfig(File autoFile)` | Parse config section from ACTIVE file |
| `userUpdateStaticEntry` | `void userUpdateStaticEntry(String fieldName, Object newValue)` | Update field from dashboard/user input |
| `getLogs` | `List<String> getLogs()` | Parse warnings/errors |

---

## Package: `com.example.instantauto.actions`

### `Action`

Core action interface (matches RoadRunner semantics).

| Method | Signature | Description |
|--------|-----------|-------------|
| `run` | `boolean run()` | Execute one step. Returns `true` = continue, `false` = complete |

---

### `MetaAction`

Factory interface for creating `Action` instances.

| Method | Signature | Description |
|--------|-----------|-------------|
| `getIdentifier` | `String getIdentifier()` | Action name for text file lookup (e.g., `"STRAFE.TO"`) |
| `create` | `Action create(Object params)` | Create from resolved parameter object |
| `create` | `Action create(String params)` | Create from raw parameter string |

---

### `MiniAction`

Primitive action with parameterized factory.

| Constructor | Signature |
|-------------|-----------|
| `MiniAction` | `MiniAction(String identifier, Function<Object, Action> factory)` |

Implements `MetaAction`. Factory receives raw or resolved parameters.

---

### `UserAction`

Composite action defined in `UserActionSettings.txt`.

| Method | Signature | Description |
|--------|-----------|-------------|
| `create` | `Action create(Object params)` | Creates sequential action from sub-actions |

**Internal:** Stores list of sub-action strings, expands on first `create()` call.

---

### `UserActionRegistry`

Central registry for all actions and conditions.

#### Action Registration

| Method | Signature | Description |
|--------|-----------|-------------|
| `register` | `static void register(MetaAction action)` | Register MiniAction or UserAction |
| `loadSettings` | `static void loadSettings(String filePath)` | Parse `UserActionSettings.txt` |
| `clear` | `static void clear()` | Clear registry and conditions |

#### Action Creation

| Method | Signature | Description |
|--------|-----------|-------------|
| `createAction` | `static Action createAction(String line)` | Parse line → Action (assignments, if/else, primitives) |

#### Condition System

| Method | Signature | Description |
|--------|-----------|-------------|
| `registerCondition` | `static void registerCondition(String name, BooleanSupplier supplier)` | Register boolean supplier for `if` conditions |
| `evaluateCondition` | `static boolean evaluateCondition(String condition)` | Resolve condition (supplier > variable > false) |

#### Nested Action Merger

| Method | Signature | Description |
|--------|-----------|-------------|
| `setActionMerger` | `static void setActionMerger(Function<List<Action>, List<Action>> merger)` | Set callback for trajectory fusion |

#### Diagnostics

| Method | Signature | Description |
|--------|-----------|-------------|
| `getLoadErrors` | `static List<String> getLoadErrors()` | Errors from `loadSettings()` |
| `getRegisteredIdentifiers` | `static Set<String> getRegisteredIdentifiers()` | All registered action names |
| `splitByTopLevelCommas` | `static List<String> splitByTopLevelCommas(String str)` | Split respecting nesting |

---

### `AutoParser`

Orchestrates full parsing pipeline for autonomous files.

| Constructor | Signature |
|-------------|-----------|
| `AutoParser` | `AutoParser(String generalSettingsPath, String userActionSettingsPath)` |

| Method | Signature | Description |
|--------|-----------|-------------|
| `findActiveAutos` | `List<File> findActiveAutos(String directory)` | Find files starting with `ACTIVE` |
| `parseAutoConfig` | `void parseAutoConfig(File autoFile)` | **Phase 1**: Parse configs, store action content |
| `parseActions` | `void parseActions()` | **Phase 2**: Parse action strings → `List<Action>` |
| `getActions` | `List<Action> getActions()` | Parsed actions (after `parseActions()`) |
| `getActionContent` | `String getActionContent()` | Raw action string (for re-parsing with merge) |
| `getActionErrors` | `List<String> getActionErrors()` | Action parsing errors |
| `getConfigLogs` | `List<String> getConfigLogs()` | Config parsing logs |

---

### `BuilderAction` (Interface)

Trajectory-building action supporting fusion.

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `TrajectoryActionBuilder apply(TrajectoryActionBuilder builder)` | Chain segment into trajectory |
| `run` | `boolean run()` | Inherited from `Action` |

> Located in `com.example.instantauto.actions.BuilderAction`

---

### `WrappedRRAction` (TeamCode)

Wraps RoadRunner Action as InstantAuto Action.

| Constructor | Signature |
|-------------|-----------|
| `WrappedRRAction` | `WrappedRRAction(com.acmerobotics.roadrunner.Action rrAction)` |

| Method | Signature | Description |
|--------|-----------|-------------|
| `run` | `boolean run()` | Delegates to `rrAction.run(new TelemetryPacket())` |
| `getRRAction` | `com.acmerobotics.roadrunner.Action getRRAction()` | Get wrapped action |

---

## Package: `org.firstinspires.ftc.teamcode.action` (TeamCode)

### `ActionUtils`

Utility methods for action parsing, merging, and adaptation.

| Method | Signature | Description |
|--------|-----------|-------------|
| `wrap` | `static Action wrap(com.acmerobotics.roadrunner.Action rrAction)` | RR Action → InstantAuto Action |
| `adapt` | `static com.acmerobotics.roadrunner.Action adapt(Action action, Telemetry telemetry)` | InstantAuto Action → RR Action |
| `asDoubles` | `static double[] asDoubles(Object params, int count)` | Parse params with variable resolution |
| `asActions` | `static List<Action> asActions(Object params, MecanumDrive drive)` | Parse action string → `List<Action>` |
| `asString` | `static String asString(Object obj)` | Format value for telemetry |
| `merge` | `static List<Action> merge(List<Action> actions, MecanumDrive drive)` | Fuse consecutive BuilderActions (flat) |
| `mergeNestedActions` | `static List<Action> mergeNestedActions(List<Action> actions, MecanumDrive drive)` | Deep merge (if/else, parallel, race) |

---

### `PrintAction` (TeamCode)

Simple action that prints message and never completes.

| Constructor | Signature |
|-------------|-----------|
| `PrintAction` | `PrintAction(Object obj)` |

| Method | Signature | Description |
|--------|-----------|-------------|
| `run` | `boolean run()` | Prints message, returns `true` (never completes) |

---

## Package: `org.firstinspires.ftc.teamcode.configs` (TeamCode)

### `Pose2d`

MetaField implementation for 2D poses (inches, degrees).

| Constructor | Signature |
|-------------|-----------|
| `Pose2d` | `Pose2d(double x, double y, double heading)` |

| Method | Signature | Description |
|--------|-----------|-------------|
| `getIdentifier` | `String getIdentifier()` | Returns `"pose2d"` |
| `getParamTypes` | `Class<?>[] getParamTypes()` | Returns `[double, double, double]` |
| `getRRPose2d` | `com.acmerobotics.roadrunner.Pose2d getRRPose2d()` | Convert to RR Pose2d (radians) |

**Fields:** `x`, `y`, `heading` (public final double)

---

### `IntakeSetting`

Example custom MetaField type.

| Constructor | Signature |
|-------------|-----------|
| `IntakeSetting` | `IntakeSetting(String mode, boolean isActive, double power)` |

| Method | Signature | Description |
|--------|-----------|-------------|
| `getIdentifier` | `String getIdentifier()` | Returns `"intakeSetting"` |
| `getParamTypes` | `Class<?>[] getParamTypes()` | Returns `[String, boolean, double]` |

**Fields:** `mode`, `isActive`, `power` (public final)

---

## Package: `org.firstinspires.ftc.teamcode.opmodes` (TeamCode)

### `AutonomousBase`

Base OpMode for InstantAuto autonomous routines.

| Method | Signature | Description |
|--------|-----------|-------------|
| `init` | `void init()` | Parse configs, init hardware, register actions |
| `start` | `void start()` | Re-parse with merge, execute via RR |
| `stop` | `void stop()` | Clear registries |

**Protected fields:**
- `autoParser`: `AutoParser`
- `drive`: `MecanumDrive`
- `actionManager`: `ActionManager`
- `actions`: `List<Action>` (RR Actions for execution)

---

## Package: `com.example.meepmeeptestbed.simulation` (MeepMeepTestbed)

### `SimPose2d`

Simulation Pose2d (degrees, implements `MetaField`).

| Constructor | Signature |
|-------------|-----------|
| `SimPose2d` | `SimPose2d(double x, double y, double heading)` |

| Method | Signature | Description |
|--------|-----------|-------------|
| `getIdentifier` | `String getIdentifier()` | Returns `"pose2d"` |
| `getParamTypes` | `Class<?>[] getParamTypes()` | Returns `[double, double, double]` |
| `getRRPose2d` | `com.acmerobotics.roadrunner.Pose2d getRRPose2d()` | Convert to RR Pose2d (radians) |

---

### `SimulationActionManager`

Registers MiniActions for MeepMeep simulation.

| Method | Signature | Description |
|--------|-----------|-------------|
| `init` | `void init(RoadRunnerBotEntity bot)` | Register STRAFE.TO, SPLINE.TO, WAIT, PARALLEL, PRINT |

---

### `SimulationActionUtils`

Action conversion utilities for simulation.

| Method | Signature | Description |
|--------|-----------|-------------|
| `asActions` | `static List<Action> asActions(Object params, RoadRunnerBotEntity bot)` | Parse → List<Action> |
| `adapt` | `static com.acmerobotics.roadrunner.Action adapt(Action action)` | InstantAuto → RR Action |
| `merge` | `static List<Action> merge(List<Action> actions, RoadRunnerBotEntity bot)` | Fuse trajectories |
| `wrap` | `static Action wrap(com.acmerobotics.roadrunner.Action rrAction)` | RR → InstantAuto Action |

---

### `SimulationConfigManager`

Registers types, fields, conditions for simulation.

| Method | Signature | Description |
|--------|-----------|-------------|
| `init` | `static void init()` | Register SimPose2d, defaults, `is_active` condition |

---

## Key Integration Points

### AutonomousBase Lifecycle

```java
@Override
public void init() {
    ConfigManager.init(this);                              // 1. Java config
    parser = new AutoParser(genSettings, userActionSettings);
    File autoFile = parser.findActiveAutos("textfiles").get(0);
    parser.parseAutoConfig(autoFile);                      // 2. Parse configs
    
    Pose2d start = (Pose2d) MetaFieldRegistry.getEntry("Starting").getValue();
    drive = new MecanumDrive(hardwareMap, start.getRRPose2d()); // 3. Init hardware
    
    actionManager.init(drive, telemetry, hardwareMap);     // 4. Register actions
    UserActionRegistry.setActionMerger(a -> ActionUtils.mergeNestedActions(a, drive));
    parser.parseActions();                                  // 5. Parse actions
}

@Override
public void start() {
    List<Action> merged = ActionUtils.asActions(parser.getActionContent(), drive);
    merged = ActionUtils.mergeNestedActions(merged, drive); // 6. Deep merge
    List<com.acmerobotics.roadrunner.Action> rrActions = merged.stream()
        .map(a -> ActionUtils.adapt(a, telemetry)).collect(Collectors.toList());
    
    Actions.runBlocking(new RaceAction(new SequentialAction(rrActions))); // 7. Execute
}

@Override
public void stop() {
    MetaFieldRegistry.clear();      // Critical: prevent supplier leaks
    UserActionRegistry.clear();
}
```

---

## Version

**API Version**: 1.0  
**Core Module**: `instantauto` (no external dependencies)  
**TeamCode**: Requires RoadRunner 1.0, FTC SDK