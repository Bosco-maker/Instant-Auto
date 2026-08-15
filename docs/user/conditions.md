# Conditions in Instant Auto

Instant Auto supports conditional logic in autonomous scripts using `if`, `else if`, and `else` blocks. This allows your robot to make decisions at runtime based on the state of the match, sensor data, or configuration variables.

## Syntax

Conditions follow a standard C-style syntax:

```kotlin
if (condition) {
    Action1
    Action2
} else if (anotherCondition) {
    Action3
} else {
    Action4
}
```

*   Actions inside the blocks are separated by new lines or commas.
*   Braces `{}` are required for blocks, even if there is only one action.
*   Nesting is supported (you can put an `if` inside another `if`).

## Condition Evaluation

The condition inside the parentheses is evaluated at runtime. It can be one of the following:

### 1. Literals
You can use `true` or `false` directly.
*   **Example:** `if (true) { RACE(PRINT("Always runs"), WAIT(3)) }`

### 2. Configuration Variables
Any boolean variable defined in your robot settings (`RobotSettings.txt`) or at the top of your autonomous file.
*   **Example:**
    ```kotlin
    // At top of .txt file
    isBlue = true

    // Later in the script
    if (isBlue) {
        RACE(
            PRINT("Running Blue path"),
            WAIT(3)
        )
    }
    ```

### 3. Registered Sensor/State Conditions
Pre-defined conditions registered in the robot's Java code (usually in `ConfigManager.java`). These are dynamic and updated every time the `if` statement is reached.

Available in this project:
*   `is_active`: Always returns `true` (placeholder).

**Example:**
```text
if (withinDistance) {
    RACE(
        PRINT("Object detected! Stopping."),
        WAIT(3)
    )
} else {
    STRAFE.TO(30, 0, 0)
}//withinDistance is registered in java code, getting data from a distance sensor

```

## Examples & Outcomes

### 1. Alliance-Based Decision
**Script:**
```text
if (isBlue) {
    STRAFE.TO(10, 48, 0)
    RACE(
        PRINT("Running Blue Alliance Path"),
        WAIT(3)
    )
} else {
    STRAFE.TO(10, -48, 0)
    RACE(
        PRINT("Running Red Alliance Path"),
        WAIT(3)
    )
}
```
**Outcome:** If `isBlue` is set to `true`, the robot strafes to positive Y (Blue side). If `false` (or not defined), it strafes to negative Y (Red side).

### 2. Multi-Case Selection (Else If)
**Script:**
```text
if (isBlue) {
    RACE(
        PRINT("Blue"),
        WAIT(3)
    )
} else if (isRed) {
    RACE(
        PRINT("Red"),
        WAIT(3)
    )
} else {
    RACE(
        PRINT("Neutral"),
        WAIT(3)
    )
}//Note: the ending } has to be at the same line as "else if" or else, like above. Not like:
//}
//else {  
```
**Outcome:** Evaluates `isBlue` first. If `false`, evaluates `isRed`. If both are false, prints "Neutral".

### 3. Nested Conditions
**Script:**
```kotlin
if (isBlue) {
    if (doPreload) {
        SCORE.PRELOAD
    }
    STRAFE.TO(30, 0, 0)
}
```
**Outcome:** If `isBlue` is `true`, it then checks `doPreload`. If `doPreload` is also `true`, it runs `SCORE.PRELOAD` and then `STRAFE.TO`. If `doPreload` is `false`, it only runs `STRAFE.TO`.

## Advanced: Action Fusing

Instant Auto automatically attempts to "fuse" consecutive movement actions (like `STRAFE.TO` and `SPLINE.TO`) into a single continuous Roadrunner trajectory. This applies even inside `if/else` branches.

**For example:**
```kotlin
if (isBlue) {
    STRAFE.TO(10, 10, 0)
    STRAFE.TO(20, 20, 0)
}
```
**Outcome:** If `isBlue` is `true`, both `STRAFE.TO` actions will be combined into a single smooth motion rather than stopping between them.

## Troubleshooting

*   **Malformed Blocks:** Ensure every opening brace `{` has a matching closing brace `}`.
*   **Unknown Conditions:** If you use a variable name that hasn't been defined, it will evaluate to `false` by default.
*   **Semicolons:** Instant Auto uses commas or newlines to separate actions; semicolons are not required.
