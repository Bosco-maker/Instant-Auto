# Registered Actions in InstantAuto

These are the "Mini Actions" (primitives) currently registered in `ActionManager`. They can be used directly in your text-file autos or combined into "Big Actions" in `UserActionSettings.txt`.
Note: they are NOT case-sensitive.

## 1. Movement Actions

### `STRAFE.TO(x, y, heading)`
Moves the robot to the specified coordinates and heading using a linear path (strafing).

- **Parameters:**
  - `x`: X-coordinate in inches.
  - `y`: Y-coordinate in inches.
  - `heading`: Target heading in degrees.
- **Example:** `STRAFE.TO(30, 0, 0)`
- **Alternative:** Can also take a `Pose2d` variable name. `STRAFE.TO(scorePose)`
- **Warning:** May trigger Exception Displacement 0.0 out of bounds (0.0, 0,0) when the robot is being told to go to the same position before the execution of an action:
- `starting = pose2d(0,0,0) or STRAFE.TO(30,0,0)`
- `STRAFE.TO(starting) or STRAFE.TO(30,0,0) `

### `SPLINE.TO(...)`
Moves the robot along a spline path to the target.

- **Usage 1 (Literal):** `SPLINE.TO(x, y, heading, startTan, endTan)`
- **Usage 2 (Variable):** `SPLINE.TO(poseName, startTan, endTan)`
- **Example:** `SPLINE.TO(30, 30, 90, 0, 90)`
- **Warning:** May trigger Exception Displacement 0.0 out of bounds (0.0, 0,0) when the robot is being told to go to the same position before the execution of an action:

## 2. Control Flow Actions

### `WAIT(seconds)`
Pauses the execution of the routine for the specified time.

- **Parameters:**
  - `seconds`: Time to wait in seconds.
- **Example:** `WAIT(1.5)`

### `PARALLEL(action1, action2, ...)`
Executes multiple actions simultaneously. The parallel block finishes when all included actions are complete.

- **Example:** `PARALLEL(STRAFE.TO(10, 0, 0), PRINT("Moving"))`

### `RACE(action1, action2, ...)`
Executes multiple actions simultaneously. The block finishes as soon as any of the included actions completes.

- **Example:** `RACE(WAIT(5), FIND_SAMPLES())` // Stops the search if 5 seconds pass.

## 3. Utility Actions

### `PRINT(message)`
Displays a message in the telemetry or logs.

- **Parameters:**
  - `message`: A literal string in quotes (e.g., `"Scoring"`) or a registered variable name (e.g., `isBlue`).
- **Example:** `PRINT("Sequence Started")`
- **Warning:** 
- This action never ends by itself, so it has to be used like 
- `RACE(
  PRINT("Sequence Started"),
  WAIT(3)
  )`
- It also cannot be used with other print action, like:
- `if(withinDistance) {
    RACE(print("is blocked")
        print(distance),
        wait(5)
        )
    }//outcome: it will print "is blocked" and never ends`

- It is a "feature", don't question me ;) may be improved in later version.

### `HELLO.WORLD`
A simple diagnostic action that prints "Hello World!" to the console and telemetry.
**Warning:** This action never ends by itself, so it has to be used like
`RACE(
  HELLO.WORLD,
  OTHER.ACTIONS
  )`

---

## Variable Resolution

Most numeric parameters (like `x`, `y`, `heading`) can also accept variable names defined in `GeneralRobotSettings.txt` or at the top of your auto file.

**Example:**
```toml
targetX = 40
STRAFE.TO(targetX, 0, 0)
```

## How to Test

1. **Meep Meep Simulator:** Add one of these actions to `testAuto.txt` in the MeepMeepTestbed and run it. The console will log `Action: [Name]` as they execute. Note: ONLY STRAFE.TO(), SPLINE.TO() AND WAIT() ARE USABLE IN MEEP MEEP
2. **Robot:** During Init, check the telemetry. If an action is misspelled or has the wrong number of parameters, an **"ACTION ERROR"** will appear on the screen.
