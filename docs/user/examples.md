# Examples

### 1. Chaining Splines (`SPLINE.TO`)

Instant Auto automatically fuses consecutive movement actions. If you put two splines back-to-back, Roadrunner calculates them as one continuous, smooth path without stopping in the middle.

#### Example: Double Spline Path

```java
// Path 1: Curve from (0,0) to (24,24)
SPLINE.TO(24, 24, 0, 90, 0)

// Path 2: Curve from (24,24) to (48,0), the startTan should be the previous endTan - 180 degree(opposite of the circle)
SPLINE.TO(48, 0, -90, 180, -90)
```

**Desired Outcome:** The robot will follow a smooth "S-curve" then a "U-turn" in one fluid motion. It will not pause at the (24, 24) mark because the actions are merged into a single trajectory before the robot starts moving.

### 2. Concurrency: `PARALLEL` vs. `RACE`

#### The `PRINT` Behavior

In Instant Auto, the `PRINT` action is designed to be persistent. It returns `true` forever so that the message stays on the telemetry. This significantly changes how you use it with `PARALLEL` and `RACE`.

#### `PARALLEL` with Print (The "Hang")

```java
PARALLEL(
    STRAFE.TO(48, 0, 0),
    PRINT("This will hang the robot")
)
```

**Result:** The robot will drive to X=48 and then stop forever. Because `PARALLEL` waits for all actions to finish, and `PRINT` never finishes, the autonomous script will never move to the next line.

#### `RACE` with Print (The Correct Way)

```java
RACE(
    STRAFE.TO(48, 0, 0),
    PRINT("Driving to X=48...")
)
```

**Desired Outcome:** The robot drives to X=48 while the message appears on the screen. As soon as the robot reaches the target, the `STRAFE.TO` finishes. This "wins" the race, causing the `RACE` block to exit and move to the next command, effectively ending the `PRINT` as well.

### 3. Timeouts and Motion Persistence

Using `RACE(drive, WAIT)` is the standard way to implement a timeout, but you must be careful about the robot's momentum.

#### Example: The Timeout Trap

```java
RACE(
    STRAFE.TO(100, 0, 0),
    WAIT(2.0)
)
// If the timeout wins, the robot is still moving here!
PRINT("Timeout reached!")
```

**Behavior:** If the robot is at X=50 when the 2-second `WAIT` finishes, the `STRAFE.TO` action is cancelled. However, the drivebase will continue moving forward at its current velocity because no new instruction told it to stop.

#### The Correct Way: Timeout with "Brake"

```java
RACE(
    STRAFE.TO(100, 0, 0),
    WAIT(2.0)
)
// Immediately give a new instruction to "catch" the drivebase
STRAFE.TO(50, 0, 0)
```

**Desired Outcome:** If the 2-second timeout occurs, the robot is immediately given a new target (its current estimated position or a new safe spot). This forces Roadrunner to calculate a new trajectory, effectively bringing the robot under control again.
