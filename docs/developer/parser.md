# Parser (AutoParser & ConfigParser)

## Overview

The **Parser** subsystem is the entry point of the InstantAuto ecosystem. It transforms text files into executable autonomous routines by handling two distinct parsing phases:

1. **ConfigParser** (`ConfigParser`) - Parses key=value configuration lines into typed variables
2. **AutoParser** (`AutoParser`) - Orchestrates the full pipeline: finds active auto files, parses configs, and converts action strings into `Action` objects

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        AutoParser                               │
├─────────────────────────────────────────────────────────────────┤
│  findActiveAutos()  →  parseAutoConfig()  →  parseActions()    │
└──────────────────────┬──────────────────────┬───────────────────┘
                       │                      │
                       ▼                      ▼
            ┌──────────────────┐    ┌──────────────────┐
            │  ConfigParser    │    │ UserActionRegistry│
            │  (configs)       │    │  (actions)       │
            └──────────────────┘    └──────────────────┘
```

---

## ConfigParser (`com.example.instantauto.configs.ConfigParser`)

### Responsibilities
- Reads `GeneralRobotSettings.txt` and per-auto configuration sections
- Strips comments (`//` and `#`)
- Parses `key = value` lines
- Updates `MetaFieldRegistry` with parsed values
- Supports **local variables** (defined only in text files) and **registered fields** (defined in Java)

### Key Methods

| Method | Description |
|--------|-------------|
| `parseConfig(String filePath)` | Parses a config file (e.g., GeneralRobotSettings.txt) |
| `handleConfigLine(String line, int lineNumber)` | Processes a single `key=value` line |
| `parseAutoConfig(File autoFile)` | Called by AutoParser to parse config sections in auto file |
| `userUpdateStaticEntry(String fieldName, Object newValue)` | Updates a field from user input (e.g., dashboard) |

### Supported Value Types

| Type | Syntax Example | Notes |
|------|----------------|-------|
| `double` | `autoTimer = 15.5` | Parsed via `Double.parseDouble` |
| `int` | `loopCount = 3` | Parsed via `Integer.parseInt` |
| `boolean` | `enabled = true` | Case-insensitive `true`/`false` |
| `String` | `motorName = "leftMotor"` | Quotes optional for simple strings |
| **MetaField types** | `redGoalPose = pose2d(-72, 48, 0)` | Custom types registered via `MetaFieldRegistry.registerType()` |

### MetaField Type Parsing

When a value matches a registered `MetaField` type:
1. Identifies type by identifier prefix (e.g., `pose2d(...)`)
2. Extracts parameters inside parentheses
3. Splits by top-level commas (respects nested parentheses and quotes)
4. Converts each parameter to expected type
5. Instantiates the type via reflection using constructor matching `getParamTypes()`

```java
// Example: Pose2d registration
registerType(new Pose2d(0, 0, 0)); // Registers "pose2d" with 3 double params

// In text file:
redGoalPose = pose2d(-72, 48, 0)
```

### Local Variables (Implicit Registration)

If a `key` in a config file is **not pre-registered** in `MetaFieldRegistry`, `ConfigParser` creates a **local variable**:
1. Attempts boolean parse
2. Attempts integer parse
3. Attempts double parse
4. Checks if value contains a known MetaField identifier (e.g., `pose2d(...)`)
5. Falls back to String

---

## AutoParser (`com.example.instantauto.actions.AutoParser`)

### Responsibilities
- Discovers active autonomous files (prefixed with `ACTIVE`)
- Two-phase parsing: **Config Phase** → **Action Phase**
- Validates required fields (`Starting`, `Title`)
- Loads UserAction definitions from `UserActionSettings.txt`
- Converts action strings into executable `Action` objects

### Two-Phase Design

```java
// Phase 1: Parse configs (can run before hardware init)
autoParser.parseAutoConfig(autoFile);
// → Populates MetaFieldRegistry with "Starting" pose
// → Must run BEFORE MecanumDrive initialization

// Phase 2: Parse actions (requires ActionManager registered primitives)
autoParser.parseActions();
// → Loads UserActionSettings.txt
// → Converts action strings to Action objects
// → Must run AFTER ActionManager.init() registers MiniActions
```

### Key Methods

| Method | Description |
|--------|-------------|
| `findActiveAutos(String directoryPath)` | Returns `List<File>` of files starting with `ACTIVE` |
| `parseAutoConfig(File autoFile)` | Phase 1: parses configs, stores action content as raw string |
| `parseActions()` | Phase 2: converts stored action content to `List<Action>` |
| `getActions()` | Returns parsed `List<Action>` |
| `getActionContent()` | Returns raw action string (for re-parsing with merging) |
| `getConfigLogs()` / `getActionErrors()` | Diagnostic output |

### File Structure

**GeneralRobotSettings.txt** (base config, loaded first):
```ini
autoTimer = 0.0
redGoalPose = pose2d(-72, 48, 0)
blueGoalPose = pose2d(72, 48, 0)
```

**ACTIVE_TestAuto.txt** (per-auto config + actions):
```ini
Title = Test Autonomous
Starting = pose2d(0, 0, 0)

STRAFE.TO(30, 0, 0)
STRAFE.TO(30, 30, 90)
```

**UserActionSettings.txt** (composite action definitions):
```ini
// Composite "Big Action" definition
SCORE_SAMPLE = SPLINE.TO(pose2d(0,0,0), 90, -90), WAIT(0.5), INTAKE.OPEN
```

### Error Handling & Validation

- **Required fields**: `Starting` (Pose2d) and `Title` (String) are validated in `parseAutoConfig()`
- **Config logs**: Warnings for unknown keys, type mismatches, local variable creation
- **Action errors**: Unknown action identifiers, malformed syntax
- **Load errors**: From `UserActionSettings.txt` parsing (missing braces, unknown sub-actions)

---

## Action String Syntax

### Basic Actions
```
ACTION_NAME(param1, param2, ...)
```

### Variable Assignment
```
variableName = value
```

### Conditionals (if/else)
```
if (condition) {
    ACTION1(params),
    ACTION2(params)
} else {
    ACTION3(params)
}
```

### Composite Actions (from UserActionSettings)
```
COMPOSITE_ACTION_NAME
```

### Parameter Types in Action Strings
| Parameter | Example | Resolution |
|-----------|---------|------------|
| Literal number | `30, 0, 0` | Direct parse |
| Variable reference | `redGoalPose` | Lookup in MetaFieldRegistry |
| String literal | `"Hello World"` | Quoted string |
| Boolean | `true` / `false` | Case-insensitive |

---

## Integration Flow

```java
// In AutonomousBase.init():
ConfigManager.init(this);                    // 1. Register Java-side fields/types
autoParser.parseAutoConfig(autoFile);        // 2. Parse configs → get Starting pose

mecanumDrive = new MecanumDrive(..., pose);  // 3. Init hardware with parsed pose
actionManager.init(mecanumDrive, telemetry); // 4. Register MiniActions (STRAFE.TO, etc.)

UserActionRegistry.setActionMerger(...);     // 5. Set nested action merger
autoParser.parseActions();                   // 6. Parse action strings → Action objects

// In AutonomousBase.start():
actions = ActionUtils.asActions(autoParser.getActionContent(), mecanumDrive);
actions = ActionUtils.mergeNestedActions(actions, mecanumDrive);
Actions.runBlocking(new SequentialAction(adaptedActions));
```

---

## Extending the Parser

### Adding New Config Types
1. Create class implementing `MetaField<T>`
2. Register in `ConfigManager.init()`: `registerType(new MyType(...))`
3. Use in text files: `myField = mytype(param1, param2)`

### Adding New Action Syntax
Modify `UserActionRegistry.createAction(String line)` to handle new patterns before the standard action lookup.

---

## Files Reference

| File | Location |
|------|----------|
| `ConfigParser.java` | `instantauto/src/main/java/com/example/instantauto/configs/` |
| `AutoParser.java` | `instantauto/src/main/java/com/example/instantauto/actions/` |
| `MetaFieldRegistry.java` | `instantauto/src/main/java/com/example/instantauto/configs/` |
| `UserActionRegistry.java` | `instantauto/src/main/java/com/example/instantauto/actions/` |