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
```bash
git clone https://github.com/Bosco-Maker/Instant-Auto-Roadrunner-QuickStart.git
cd Instant-Auto-Roadrunner-QuickStart
# Open in Android Studio, let Gradle sync
```
This gives you a complete project with `TeamCode`, `instantauto` module, and example text files already wired up.

### Option 2: Add as Gradle Module
1. Copy the `instantauto` directory into your FTC project root
2. Add to `settings.gradle.kts`:
   ```kotlin
   include(":instantauto")
   ```
3. Add dependency in `TeamCode/build.gradle.kts`:
   ```kotlin
   implementation(project(":instantauto"))
   ```
4. Sync Gradle

---

## Create the Required Text Files

Create three files in your project's `textfiles/` directory (or `TeamCode/src/main/assets/textfiles/`):

### 1. GeneralRobotSettings.txt
```ini
# Global robot configuration
# These are registered in Java via ConfigManager.init()

# Required: starting pose placeholder (overridden per-auto)
Starting = pose2d(0, 0, 0)

# Example poses
scorePose = pose2d(48, 24, 90)
parkPose = pose2d(12, 12, 0)

# Tuning parameters
maxVelocity = 60.0
intakePower = 1.0

# Alliance selector (set per-match in ACTIVE file)
isBlue = true
```

### 2. UserActionSettings.txt
```ini
# Reusable "Big Actions" - macros composed of primitives
# Syntax: ActionName = { subAction1, subAction2, ... }

# Score a sample
scoreSample = {
    SPLINE.TO(scorePose, 0, 45),
    INTAKE.ON(intakePower),
    WAIT(0.5),
    INTAKE.OFF
}

# Park at end of auto
park = {
    STRAFE.TO(parkPose),
    WAIT(0.2)
}
```

### 3. ACTIVEMyFirstAuto.txt
> **Critical**: File **must** start with `ACTIVE` prefix to be detected.

```ini
# Match-specific routine
# Overrides from GeneralRobotSettings go here

Starting = pose2d(-24, 0, 0)
title = "My First Auto"
isBlue = true

# Action sequence - runs top to bottom
STRAFE.TO(30, 0, 0)
scoreSample
park
```

---

## Deploy to Robot Controller

1. Connect to Robot Controller via USB or WiFi
2. In Android Studio: **Build → Make Project** (or press the green hammer)
3. Press the **Play** button (or `Shift+F10`) to deploy
4. On Driver Station: **Select Autonomous** → You should see "My First Auto"

---

## Select and Run

1. On Driver Station, tap **Select Autonomous**
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

## File Location Reference

| File | Typical Location |
|------|------------------|
| `GeneralRobotSettings.txt` | `TeamCode/src/main/assets/textfiles/` |
| `UserActionSettings.txt` | `TeamCode/src/main/assets/textfiles/` |
| `ACTIVE*.txt` | `TeamCode/src/main/assets/textfiles/` |

> [!TIP]
> On the Robot Controller web interface (http://192.168.43.1:8080/), you can edit `ACTIVE*.txt` files directly in **OnBot Java** → **Text File** mode. Changes take effect on next deploy — no Java recompile!