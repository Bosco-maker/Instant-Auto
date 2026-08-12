# Syntax

Instant Auto uses a simple, text-based syntax to define robot behavior. The configuration and sequences are split across three main types of files.

## 1. GeneralRobotSettings.txt
This file handles the robot's "global state"—variables and configuration constants that remain consistent across different autonomous routines.

*   **Syntax:**
    *   **Assignments:** `Key = Value`. No semicolon is needed.
    *   **Data Types:** Supports `int`, `double`, `boolean` (`true`/`false`), and `String` (optional quotes).
    * //TODO: change path
    *   **Custom Types:** Supports registered types like `pose2d(x, y, heading)`, how to register types in [Registering-variables](programmer/registering-variables.md).
    *   **Comments:** Use `//` or `#`.

*   **Example:**
    ```kotlin
    // Global variables
    isBlue = true
    maxVelocity = 40.0

    # Poses
    scorePose = pose2d(-72, -67, 0)
    ```

*   Warning: if a variable is not registered beforehand in java code [Registering-variables](programmer/registering-variables.md), the variable will be created as a "local variable", which will trigger  [ERROR] Line 1 Parsed in local double 'maxVelocity = 40.0'. While you may be able to invoke it in text file (like passing it as a parameter for an action), it does not has a default value like a properly registered variable and may trigger errors when robot code trying to invoke it.

## 2. UserActionSettings.txt
This file defines "Big Actions"—reusable macros composed of several "Mini Actions" (primitives like `STRAFE.TO`).

*   **Syntax:**
    *   **Definition:** `ActionName = { subAction1, subAction2, ... }`.
    *   **Separators:** Sub-actions can be separated by commas or newlines.

  *   **Example:**
      ```toml
      scoreAction={
        strafe.to(30,0,0)
        strafe.to(24,24,0)
      }
      ```
      OR
      ```toml
      scoreAction={
        strafe.to(30,0,0),
        strafe.to(24,24,0)
      }
      ```
*   **Warning**
  * **Defined Sub-actions Only:**
    ```toml
    errorAction = {
        spline.to(0,24,0, 0, 45),
        spline.to(24,24,0, 0, 0),
        un.defined.action
    //Triggers: (in lAction): Unknown sub-action -> un.defined.action
    }
    ```
  * **No Nesting:** You cannot include other Big Actions inside a definition.
    ```toml
    lAction = {
        spline.to(0,24,0, 0, 45),
        spline.to(24,24,0, 0, 0),
        lAction
    //Triggers: (in lAction): Unknown sub-action -> IAction
    }
    ```

## 3. Text-File Autos (Routine Files)
These are the specific sequences for a match (e.g., `ACTIVE_BlueSide.txt`).

*   **How to Make One Auto:**
    *   Navigate to http://192.168.43.1:8080/ (http://192.168.49.1:8080/ if you are using a phone controller), select onBot Java.
    *   Create a new file at org/firstinspires/ftc/teamcode
    *   Set File Type to Text File
    *   Set File Name to ACTIVE_[your-auto-name].txt, example: ACTIVE_BlueFarAuto.txt
    *   Restart Robot and you auto will appear at Select Autonomous
*   **Syntax:**
    *   **Config Overrides:** Any `Key = Value` at the top overrides the general settings for this specific auto.
    *   **Required Field:** `Starting = pose2d(...)` must be present.
    *   **Title (Optional):** `title = "..."` can be added to show the auto selected.
    *   **Execution Flow:** List actions in order. Supports `if/else` blocks.

*   **Example:**
    ```toml
    Starting=pose2d(-24,0,0)
    title="Blue Far Auto"
    RACE(
        if(withindistance) {
            strafe.to(-30,0,0) 
            strafe.to(-24,0,0)
        } else {
            strafe.to(30,0,0) 
            strafe.to(-24,0,0)
        },
        WAIT(30)
        )
    //this is how you do timeout!
    ```
