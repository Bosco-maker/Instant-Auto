# Syntax Reference (Authoritative)

This is the **complete, authoritative syntax specification** for InstantAuto text files. All parsers, simulators, and tools must conform to this specification.

---

## File System

| File Type | Pattern | Purpose | Load Order |
|-----------|---------|---------|------------|
| General Settings | `GeneralRobotSettings.txt` | Global defaults | 1st (by ConfigParser) |
| User Actions | `UserActionSettings.txt` | Composite action definitions | 2nd (by AutoParser) |
| Active Autonomous | `ACTIVE*.txt` | Match routine (config + actions) | 3rd (by AutoParser) |

> **Requirement**: Exactly one `ACTIVE*.txt` file must exist (picked by `findActiveAutos()`).

---

## Lexical Structure

### Comments
```ini
// Line comment (C++ style)
# Line comment (shell style)
```

- Comments stripped before parsing
- Inline comments supported: `key = value // comment`

### Whitespace
- Spaces and tabs: insignificant (except inside quoted strings)
- Newlines: separate statements
- Multiple statements per line: separated by commas (in action sequences)

### Identifiers
```
identifier = letter { letter | digit | '_' }
```
- Case-insensitive for lookup (converted to lowercase)
- Must start with letter or underscore
- Variable names, action names, condition names

### Literals

| Type | Regex | Example |
|------|-------|---------|
| Integer | `-?\d+` | `42`, `-3` |
| Double | `-?\d+(\.\d+)?` | `3.14`, `-0.5`, `60` |
| Boolean | `true\|false` (case-insensitive) | `true`, `FALSE` |
| String | `("[^"]*")\|('[^']*')` | `"hello"`, `'world'` |
| Unquoted String | `[^,\s]+` | `motorName`, `blueAlliance` |

---

## Grammar (EBNF)

```ebnf
file = configSection? , actionSection? ;

configSection = { configLine } ;

configLine = assignment | emptyLine | comment ;

assignment = identifier , '=' , value ;

value = literal | metaFieldLiteral | identifier ;

literal = boolean | integer | double | string ;

metaFieldLiteral = identifier , '(' , paramList? , ')' ;

paramList = value , { ',' , value } ;

actionSection = { actionLine } ;

actionLine = emptyLine | comment | assignment | ifBlock | actionCall ;

actionCall = identifier , '(' , paramList? , ')' ;

ifBlock = 'if' , '(' , condition , ')' , '{' , actionSequence , '}' 
          [ 'else' , 'if' , '(' , condition , ')' , '{' , actionSequence , '}' ]
          [ 'else' , '{' , actionSequence , '}' ] ;

condition = identifier | 'true' | 'false' ;

actionSequence = actionItem , { ',' , actionItem } ;

actionItem = actionCall | identifier ;  // identifier = UserAction (Big Action)

emptyLine = '\n' | '\r\n' ;
comment = '//' , { anyChar - '\n' } , '\n' 
       | '#' , { anyChar - '\n' } , '\n' ;
```

---

## Configuration Files

### GeneralRobotSettings.txt

```ini
# Required fields (for AutoParser validation)
Starting = pose2d(0, 0, 0)
Title = ""

# Alliance
isBlue = true

# Poses
scorePose = pose2d(48, 24, 90)
parkPose = pose2d(12, 12, 0)

# Tuning
maxVelocity = 60.0
intakePower = 1.0

# Custom MetaField types
intakeConfig = intakeSetting("NORMAL", true, 0.8)
```

### UserActionSettings.txt

```ini
# Single-line
scoreSample = SPLINE.TO(scorePose, 0, 45), INTAKE.ON(1.0), WAIT(0.5)

# Multi-line (braces required)
park = {
    STRAFE.TO(parkPose),
    WAIT(0.2)
}

# Nested composites not supported
# But can reference other UserActions:
fullAuto = {
    scoreSample,
    park
}
```

### ACTIVE*.txt (Autonomous Routine)

```ini
# Config section (top-level, any order)
Starting = pose2d(-24, 0, 0)   # REQUIRED
Title = "Blue Auto"            # Optional (shows in DS)
isBlue = true                  # Overrides GeneralRobotSettings
scorePose = pose2d(50, 25, 90) # Override for this match

# Action section (sequential execution)
STRAFE.TO(30, 0, 0)
SPLINE.TO(scorePose, 0, 45)
INTAKE.ON(1.0)
WAIT(0.5)

# Conditional
if (isBlue) {
    STRAFE.TO(blueIntakePose)
} else {
    STRAFE.TO(redIntakePose)
}

# Composite action
scoreSample

# Runtime assignment (executes when reached)
targetPose = pose2d(100, 100, 0)
STRAFE.TO(targetPose)
```

---

## Action Calls

### Primitive Actions (MiniActions)
Registered in Java via `UserActionRegistry.register(new MiniAction(...))`.

```
ACTION_NAME(param1, param2, ...)
```

**Parameter resolution:**
1. Try literal parse (number, boolean, string)
2. Try variable lookup in `MetaFieldRegistry`
3. If single param matches `Pose2d` variable → pass as object

### Composite Actions (UserActions)
Defined in `UserActionSettings.txt`, called without parentheses:

```
COMPOSITE_NAME
```

---

## Variable Assignments

### Static (Top-Level)
```ini
variableName = value
```
- Executed during **config parsing** (init time)
- Available to all subsequent actions
- In ACTIVE file: before action sequence starts
- In UserActionSettings: NOT allowed at top level (only in composites)

### Runtime (Inside Composites)
```ini
MY_ACTION = {
    PRINT("Before: " + myVar),
    myVar = 42,                    # Assignment action
    PRINT("After: " + myVar),
    STRAFE.TO(myVar)               # Uses updated value
}
```
- Executed as **action** when composite runs
- Creates/updates `MetaFieldRegistry` entry
- Type inferred from value

---

## Conditionals (if/else)

### Syntax
```ini
if (condition) {
    action1,
    action2
} else if (condition2) {
    action3
} else {
    action4
}
```

### Rules
- **Braces `{ }` mandatory** — even for single actions
- `else if` / `else` must be on **same line** as closing `}`
- **Nesting allowed**
- **Condition evaluated once** on first `run()` (lazy)

### Condition Types & Priority

| Priority | Source | Example | Overwritable |
|----------|--------|---------|--------------|
| 1 | Literal | `if (true)` | N/A |
| 2 | **Condition Supplier** | `if (withinDistance)` | **NO** |
| 3 | Boolean Variable | `if (isBlue)` | Yes (at init) |
| 4 | Undefined | `if (unknown)` | Default `false` |

### Condition Suppliers (Java)
```java
UserActionRegistry.registerCondition("withinDistance", () -> 
    hardwareMap.get(DistanceSensor.class, "sensor").getDistance(DistanceUnit.CM) < 10.0
);
```

---

## MetaField Types

### Built-in: `pose2d`

```ini
myPose = pose2d(x, y, heading)
```
- Parameters: `double, double, double` (x, y, heading in **degrees**)
- Registered in Java: `registerType(new Pose2d(0,0,0))`

### Example Custom: `intakeSetting`

```ini
myIntake = intakeSetting("REVERSE", true, 0.8)
```
- Parameters: `String, boolean, double`
- Registered in Java: `registerType(new IntakeSetting("", false, 0))`

### Custom MetaField Implementation

```java
public class MyType implements MetaField<MyType> {
    public final String name;
    public final double value;

    public MyType(String name, double value) { ... }

    @Override public String getIdentifier() { return "myType"; }
    @Override public Class<?>[] getParamTypes() { 
        return new Class[]{String.class, double.class}; 
    }
}
```

**Registration:**
```java
MetaFieldRegistry.registerType(new MyType("", 0));
MetaFieldRegistry.registerField("myField", MyType.class, new MyType("default", 1.0));
```

---

## Operators & Expressions

> **Note**: InstantAuto does **NOT** support arithmetic expressions in text files.
> Use Java-side suppliers for computed values.

```ini
# NOT SUPPORTED
speed = maxSpeed * 0.5
target = pose1 + pose2

# SUPPORTED: Java supplier
MetaFieldRegistry.registerField("halfSpeed", Double.class, 
    () -> MetaFieldRegistry.getEntry("maxSpeed").getValue() * 0.5);
```

---

## Special Rules

### Action Merging (Trajectory Fusion)
Consecutive trajectory-building actions (`STRAFE.TO`, `SPLINE.TO`) are **automatically fused** into single continuous trajectories:

```ini
SPLINE.TO(pose1, 0, 45)
SPLINE.TO(pose2, 45, -45)    # Fused with previous → one smooth path
```

**Requires**: `UserActionRegistry.setActionMerger()` configured in `AutonomousBase`.

### Variable Scope
- **Global**: All variables in `MetaFieldRegistry` (Java + text files)
- **No local scope**: Assignments always update global registry
- **Shadowing**: Text file assignment overrides Java default for remainder of run

### If/Else Trajectory Fusion
Trajectories inside `if/else` branches are fused **if** `mergeNestedActions()` is called. The cached trajectory is built at **branch entry time** (current robot pose).

---

## Complete Example

```ini
# GeneralRobotSettings.txt
isBlue = true
maxVelocity = 60.0
scorePose = pose2d(48, 24, 90)
parkPose = pose2d(12, 12, 0)

# UserActionSettings.txt
scoreSample = {
    SPLINE.TO(scorePose, 0, 45),
    INTAKE.ON(1.0),
    WAIT(0.5),
    INTAKE.OFF
}

# ACTIVEBlueAuto.txt
Starting = pose2d(-24, 0, 0)
Title = "Blue Auto - Score + Park"
isBlue = true

# Sequence
STRAFE.TO(30, 0, 0)
SPLINE.TO(scorePose, 0, 45)
INTAKE.ON(1.0)
WAIT(0.5)

# Conditional
if (isBlue) {
    STRAFE.TO(blueIntakePose)
} else {
    STRAFE.TO(redIntakePose)
}

# Composite
scoreSample

# Park
STRAFE.TO(parkPose)
```

---

## Version

**Syntax Version**: 1.0  
**Compatible with**: InstantAuto core v1.x, RoadRunner 1.0