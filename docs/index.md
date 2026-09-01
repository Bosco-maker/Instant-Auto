# InstantAuto

**Text-based autonomous programming framework for FIRST Tech Challenge.**

Go here to [Get Started](user/getting-started.md).

---

## Why InstantAuto?

InstantAuto lets you **write and modify autonomous routines entirely through text files** — no Java recompilation needed. This means:

- **Zero-compile iteration**: Change a pose, add a wait, swap a path — just edit the text file and redeploy. Critical during competition when every minute counts.
- **Drive-team accessibility**: Non-programmers can tune autonomous routines by editing readable text configuration. The drive team can adjust starting positions, scoring poses, and timing without touching Java code.
- **Clean separation**: Java defines *what the robot can do* (hardware, primitives, sensors). Text defines *what the robot does* (sequence, logic, parameters).

---

## Before / After

### Before: Traditional Java Autonomous
```java
// Every change requires: Edit Java → Compile → Deploy → Test (2-5 min cycle)
public class BlueAuto extends LinearOpMode {
    @Override
    public void runOpMode() {
        MecanumDrive drive = new MecanumDrive(hardwareMap, new Pose2d(-24, 0, 0));
        waitForStart();
        
        drive.actionBuilder(drive.pose)
            .strafeTo(new Vector2d(30, 0))
            .build().runBlocking();
        
        intake.setPower(1.0);
        sleep(500);
        
        drive.actionBuilder(drive.pose)
            .splineToSplineHeading(new Pose2d(48, 24, Math.toRadians(90)), Math.toRadians(45))
            .build().runBlocking();
    }
}
```

### After: InstantAuto Text Configuration
```ini
# GeneralRobotSettings.txt (robot capabilities - Java registers these)
scorePose = pose2d(48, 24, 90)
intakePower = 1.0

# UserActionSettings.txt (reusable macros)
scoreSample = {
    SPLINE.TO(scorePose, 0, 45),
    INTAKE.ON(intakePower),
    WAIT(0.5)
}

# ACTIVEBlueAuto.txt (match routine - editable trackside!)
Starting = pose2d(-24, 0, 0)
title = "Blue Auto"

STRAFE.TO(30, 0, 0)
scoreSample
```

**Result**: Trackside changes take **seconds**, not minutes. The drive team can edit `ACTIVEBlueAuto.txt` on the Robot Controller web interface.

---

## Quick Links

| Audience | Start Here |
|----------|------------|
| **Write autonomous routines** (no Java) | [User Guide → Getting Started](user/getting-started.md) |
| **Integrate into FTC project** (Java) | [Programmer Guide → Introduction](programmer/index.md) |
| **Modify InstantAuto itself** | [Developer Guide → Introduction](developer/index.md) |
| **Syntax & API lookup** | [Reference → Syntax](reference/syntax.md) |
| **Test in browser** | [Simulator](simulator/index.html) |
| **Source code** | [GitHub Repository](https://github.com/Bosco-Maker/Instant-Auto) |

---

## Project Status

> [!NOTE]
> **Alpha** — Developed by one person over the summer. Feedback / Contribution is much appreciated. Core parsing, action system, and RoadRunner integration are functional. MeepMeep simulator if/else support is limited. API may change between versions.

---

## Documentation Structure

- **User Guide** — Write autonomous routines using text files only
- **Programmer Guide** — Integrate InstantAuto into your FTC SDK project, register custom actions/sensors
- **Developer Guide** — Internal architecture: parser, registries, action system, execution pipeline
- **Reference** — Authoritative syntax grammar and complete API reference
- **Simulator** — Browser-based testing (limited feature set)