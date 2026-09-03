# Getting Started

Complete end-to-end tutorial: from zero to your first working autonomous routine.

---

## Prerequisites

- **FTC SDK** installed (Android Studio with FTC plugin)
- **Robot Controller** (Control Hub or Android phone)
- **Driver Station** paired with Robot Controller
- Basic familiarity with FTC project structure

---

## Installation

Two ways to add InstantAuto to your project:

### Option 1: Clone the QuickStart Repository (Recommended)

Here is the [repo](https://github.com/Esquimalt-Atom-Smashers/instant-auto-roadrunner-quickstart)

Clone according to this [official instruction](https://docs.github.com/en/repositories/creating-and-managing-repositories/cloning-a-repository)
```bash
#Example
git clone https://github.com/Esquimalt-Atom-Smashers/instant-auto-roadrunner-quickstart.git
cd Instant-Auto-Roadrunner-QuickStart
# Open in Android Studio, let Gradle sync
```
This gives you a complete project with `TeamCode`, `instantauto` module, and example text files already wired up.

### Option 2: Installing into an Existing Project

Even if you have an existing project, it may be easier to start with the [quickstart](https://github.com/Esquimalt-Atom-Smashers/instant-auto-roadrunner-quickstart) and copy your other files over.

If you're migrating from Road Runner 0.5.x, start by removing all references to Road Runner in your Gradle files and elsewhere in your project. Road Runner 1.0.x is not backwards compatible.

1. Open the `TeamCode/build.gradle` file and add:

   ```groovy
   repositories {
       maven {
           url = 'https://maven.brott.dev/'
       }
   }
   ```

   between the `android` and `dependencies` blocks. Also put:

   ```groovy
   implementation "com.acmerobotics.roadrunner:ftc:0.1.25"
   implementation "com.acmerobotics.roadrunner:core:1.0.1"
   implementation "com.acmerobotics.roadrunner:actions:1.0.1"
   implementation "com.acmerobotics.dashboard:dashboard:0.5.1"
   implementation "io.github.bosco-maker:instantauto:1.0.0"
   ```

   at the end of the `dependencies` block.

2. Run a Gradle sync.

3. [Clone or download the quickstart](https://github.com/Esquimalt-Atom-Smashers/instant-auto-roadrunner-quickstart).

4. Navigate to the teamcode folder in the quickstart (`TeamCode/src/main/java/org/firstinspires/ftc/teamcode`) and copy the following to the teamcode folder of your existing project:
   - `action/` directory
   - `configs/` directory
   - `opmodes/` directory
   - `roadrunners/` directory
   - `TextFileLocationBook.java`

You're done! Time to continue on to [Roadrunner Tuning](https://rr.brott.dev/docs/v1-0/tuning/).

---

## Create the Required Text Files

Create three files in your robot's onBot java within web interface (http://192.168.43.1:8080/) / (http://192.168.49.1:8080/ on a phone controller)

### 1. GeneralRobotSettings.txt
```ini
# Global robot configuration
# Example poses
scorePose = pose2d(48, 24, 90)
parkPose = pose2d(12, 12, 0)

testDouble = 41.7
```

### 2. UserActionSettings.txt
```ini
# Reusable "Big Actions" - macros composed of primitives
# Syntax: ActionName = { subAction1, subAction2, ... }

S_path = {
   STRAFE.TO(parkPose),
   SPLINE.TO(32, 12, 0, 90, 270)
   SPLINE.TO(52, 12, 180, 270, 90)
}
```

### 3. ACTIVEMyFirstAuto.txt
> **Critical**: File **must** start with `ACTIVE` prefix to be detected.

```ini
# Match-specific routine
# Overrides from GeneralRobotSettings go here

Starting = pose2d(-24, 0, 0)
title = "My First Auto" #Optional
isBlue = true

# Action sequence - runs top to bottom
STRAFE.TO(0,0,0)
S_path
```

---

## Deploy to Robot Controller

1. Connect to Robot Controller via USB or WiFi
2. In Android Studio: **Build → Make Project** (or press the green hammer)
3. Press the **Play** button (or `Shift+F10`) to deploy
4. On Driver Station: **Select Autonomous** → You should see "My First Auto"

---

## Select and Run

1. On Driver Station, **Select Autonomous**
2. Choose **"My First Auto"** (from the `title` field)
3. Press **INIT** → Robot initializes, parses text files
4. Press **START** → Autonomous runs!

---

## Troubleshooting Common Failures

| Symptom | Cause | Fix |
|---------|-------|-----|
| **"CRITICAL ERROR: No active autonomous files found"** | No file starts with `ACTIVE` | Rename file to `ACTIVE<Name>.txt` |
| **"Required 'Starting' field is missing"** | `Starting = pose2d(...)` not in auto file | Add `Starting` line to ACTIVE file |
| **"Unknown Action -> STRAFE.TO(...)"** | Action not registered in Java | Check `ActionManager.init()` registers `STRAFE.TO` |
| **"Parameter count mismatch for pose2d"** | Wrong number of args to `pose2d()` | Use `pose2d(x, y, heading)` — 3 numbers |
| **Robot doesn't move** | `PRINT` or `HELLO.WORLD` without `RACE` | Wrap in `RACE(PRINT("msg"), WAIT(2))` |
| **Telemetry shows "ACTION ERROR"** | Misspelled action or wrong params | Check Driver Station telemetry during INIT |

---

## Next Steps

- **[Syntax](syntax.md)** — Full file format reference
- **[Actions](actions.md)** — All available actions with parameters
- **[Variables](variables.md)** — Variable types, sensors, suppliers
- **[Conditions](conditions.md)** — `if/else` logic in autonomous
- **[Examples](examples.md)** — Real competition routines

---

> [!TIP]
> On the Robot Controller web interface (http://192.168.43.1:8080/), you can edit `ACTIVE*.txt` files directly in **OnBot Java** → **Text File** mode. Changes take effect on next deploy — no Java recompile!
