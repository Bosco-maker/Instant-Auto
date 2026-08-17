# Contribution Guide

## Reporting Bugs

### Before Submitting
1. **Check existing issues** - Search GitHub issues for similar problems
2. **Reproduce minimally** - Create a minimal test case (text file + Java snippet)
3. **Gather logs** - Include:
    - ConfigParser logs (`autoParser.getConfigLogs()`)
    - UserActionRegistry errors (`UserActionRegistry.getLoadErrors()`)
    - Action errors (`autoParser.getActionErrors()`)
    - Stack traces (if any)

### Bug Report Template

```markdown
## Bug Description
Clear description of the issue.

## Environment
- InstantAuto version/commit: 
- FTC SDK version: (e.g., 11.0.0)
- RoadRunner version: (e.g., 1.0.1)
- Robot hardware: (Control Hub, Expansion Hub, etc.)

## Steps to Reproduce
1. Configure GeneralRobotSettings.txt with...
2. Create ACTIVE_test.txt with...
3. Run AutonomousBase...

## Expected Behavior
What should happen.

## Actual Behavior
What actually happens (include telemetry/logs).

## Relevant Files
```
GeneralRobotSettings.txt:
...

ACTIVE_test.txt:
...

UserActionSettings.txt:
...
```

## Additional Context
Any other information, screenshots, or videos.
```

---

## Code Contributions

### Development Setup

```bash
# Clone
git clone https://github.com/your-org/instant-auto-roadrunner-quickstart.git
cd instant-auto-roadrunner-quickstart

# Open in Android Studio
# Let Gradle sync

# Run tests
./gradlew :instantauto:test
./gradlew :TeamCode:assembleDebug
```

### Project Structure

```
instant-auto-roadrunner-quickstart/
├── instantauto/                    # Core library (publishable)
│   └── src/main/java/com/example/instantauto/
│       ├── actions/                # Action system
│       └── configs/                # Configuration system
├── TeamCode/                       # Robot-specific implementation
│   └── src/main/java/org/firstinspires/ftc/teamcode/
│       ├── action/                 # ActionManager, ActionUtils
│       ├── configs/                # Pose2d, IntakeSetting, ConfigManager
│       ├── opmodes/                # AutonomousBase, TextFileAutos
│       └── roadrunner/             # MecanumDrive, localizers
├── textfiles/                      # Example configs
│   ├── GeneralRobotSettings.txt
│   ├── UserActionSettings.txt
│   └── testAuto.txt
└── doc/instantauto/                # This documentation
```

### Coding Standards

#### Java Style
- Follow Google Java Style Guide
- Use meaningful names: `strafeToFactory` not `stf`
- Document public APIs with Javadoc
- Prefer composition over inheritance

#### InstantAuto Conventions

**MiniAction Factories:**
```java
// ✅ Good: Clear name, handles both variable refs and literals, caches trajectories
private Action strafeToFactory(Object params) { ... }

// ❌ Bad: Cryptic, no variable resolution, no caching
private Action stf(Object p) { ... }
```

**MetaField Implementations:**
```java
// ✅ Good: Immutable, implements MetaField, has getRRPose2d() for RR interop
public class Pose2d implements MetaField<Pose2d> {
    public final double x, y, heading;
    // ...
    public com.acmerobotics.roadrunner.Pose2d getRRPose2d() { ... }
}
```

**Supplier Registration:**
```java
// ✅ Good: Live sensor reading
registerField("distance", Double.class, () -> sensor.getDistance(DistanceUnit.CM));

// ❌ Bad: Stale value
registerField("distance", Double.class, sensor.getDistance(DistanceUnit.CM));
```

### Pull Request Process

1. **Fork** the repository
2. **Create branch**: `git checkout -b feature/amazing-feature`
3. **Commit** with conventional messages:
   ```
   feat: add SPLINE.TO tangent control
   fix: resolve supplier leak in stop()
   docs: update action-system documentation
   refactor: extract trajectory caching to util
   test: add ConfigParser parameter splitting tests
   ```
4. **Test locally**:
    - Run unit tests: `./gradlew :instantauto:test`
    - Verify on robot (or MeepMeep simulation)
5. **Push** and open PR with description linking to issue

### Testing

#### Unit Tests (instantauto module)
```java
// instantauto/src/test/java/com/example/instantauto/configs/ConfigParserTest.java
@Test
public void testPose2dParsing() {
    ConfigParser parser = new ConfigParser();
    parser.parseConfig("test-config.txt");
    // Assert MetaFieldRegistry entries
}
```

#### Integration Tests (MeepMeepTestbed)
```java
// MeepMeepTestbed/src/main/java/.../MyAutoTest.java
@Test
public void testFullAutonomous() {
    // Use MeepMeep to simulate full auto
}
```

---

## Adapting InstantAuto for PedroPathing

PedroPathing is an alternative pathing library. Here's how to adapt InstantAuto:

### 1. Core Library (instantauto) - No Changes Needed

The core `instantauto` module is **pathing-agnostic**:
- `Action` interface: `boolean run()` - works with any executor
- `MetaFieldRegistry`: Pure configuration - no pathing dependency
- `UserActionRegistry`: Action composition - no pathing dependency
- `ConfigParser`/`AutoParser`: Text parsing - no pathing dependency

### 2. TeamCode Layer - Replace RoadRunner Integration

#### Replace ActionManager

```java
// NEW: PedroActionManager.java
public class PedroActionManager {
    PedroDrive pedroDrive;
    Telemetry telemetry;

    public void init(PedroDrive drive, Telemetry telemetry) {
        this.pedroDrive = drive;
        this.telemetry = telemetry;

        // Register PedroPathing primitives
        UserActionRegistry.register(new MiniAction("FOLLOW.PATH", this::followPathFactory));
        UserActionRegistry.register(new MiniAction("TURN.TO", this::turnToFactory));
        UserActionRegistry.register(new MiniAction("WAIT", params -> {
            double[] d = ActionUtils.asDoubles(params, 1);
            return d != null ? ActionUtils.wrap(new PedroSleepAction(d[0])) : null;
        }));
        // ... other primitives
    }

    private Action followPathFactory(Object params) {
        // Resolve path from variable or parse parameters
        // Return Action that executes PedroPathing path
    }
}
```

#### Replace ActionUtils

```java
// NEW: PedroActionUtils.java
public class PedroActionUtils {
    // No BuilderAction fusion needed if PedroPathing handles it differently
    
    public static PedroAction adapt(Action action) {
        return new PedroAction() {
            @Override public boolean run() {
                return action.run();
            }
        };
    }

    public static Action wrap(PedroAction pedroAction) {
        return new Action() {
            @Override public boolean run() {
                return pedroAction.run();
            }
        };
    }

    // Keep: asDoubles, asActions, asString (pathing-agnostic)
}
```

#### Replace AutonomousBase

```java
public class PedroAutonomousBase extends OpMode {
    private AutoParser autoParser;
    private PedroActionManager actionManager;
    private File autoFile;
    private List<PedroAction> actions;
    private PedroDrive pedroDrive;

    @Override
    public void init() {
        ConfigManager.init(this);
        actionManager = new PedroActionManager();
        // ... parse config, init pedroDrive with Starting pose
        actionManager.init(pedroDrive, telemetry);
        autoParser.parseActions();
    }

    @Override
    public void start() {
        actions.clear();
        List<Action> merged = PedroActionUtils.asActions(autoParser.getActionContent(), pedroDrive);
        for (Action a : merged) {
            actions.add(PedroActionUtils.adapt(a));
        }
        // Execute via PedroPathing runner
        PedroActions.runBlocking(new PedroSequentialAction(actions));
    }
}
```

### 3. Text Files - Compatible

**No changes needed** to text file format:
```ini
# Same config format
Title = PedroPathing Auto
Starting = pose2d(0, 0, 0)

# Same action syntax (different primitive names)
FOLLOW.PATH(myPath)
TURN.TO(90)
WAIT(0.5)
if (isBlue) { ... }
```

### 4. MetaField Types - Compatible

**No changes needed** - `Pose2d`, `IntakeSetting`, custom types work identically.

### 5. Key Differences to Implement

| RoadRunner | PedroPathing Equivalent |
|------------|------------------------|
| `MecanumDrive` | `PedroDrive` |
| `TrajectoryActionBuilder` | `PathBuilder` / `PedroPath` |
| `Actions.runBlocking()` | `PedroActions.runBlocking()` |
| `ParallelAction` | `PedroParallelAction` |
| `SequentialAction` | `PedroSequentialAction` |
| `SleepAction` | `PedroSleepAction` |
| `TelemetryPacket` | Pedro equivalent or custom |
| `BuilderAction` fusion | May not be needed (Pedro may fuse internally) |

### 6. Migration Checklist

- [ ] Create `PedroActionManager` with Pedro primitives
- [ ] Create `PedroActionUtils` with adaptation layer
- [ ] Create `PedroAutonomousBase`
- [ ] Register Pedro primitives (FOLLOW.PATH, TURN.TO, etc.)
- [ ] Implement path-following MiniAction factories
- [ ] Test with existing text files (update primitive names)
- [ ] Verify MetaFieldRegistry integration works
- [ ] Test if/else, variable assignments, suppliers
- [ ] Benchmark performance vs RoadRunner

---

## Architecture Decision Records (ADRs)

### ADR-001: Two-Phase Parsing (Config → Actions)
**Status**: Accepted
**Reason**: Hardware initialization requires `Starting` pose from config before `MecanumDrive` creation, but action parsing needs `MecanumDrive` for trajectory fusion.

### ADR-002: Supplier-Based Dynamic Fields
**Status**: Accepted
**Reason**: Sensors/gamepads need live values. Suppliers evaluated on `getValue()` avoid stale data.

### ADR-003: BuilderAction Fusion for Trajectory Continuity
**Status**: Accepted
**Reason**: Consecutive trajectory segments must be fused for smooth motion; `merge()` chains `TrajectoryActionBuilder` calls.

### ADR-004: Cached Trajectories for Conditional Branches
**Status**: Accepted
**Reason**: if/else branches execute at runtime; trajectory must be built from **current pose** at branch entry, not at parse time.

### ADR-005: Pathing-Agnostic Core Library
**Status**: Accepted
**Reason**: `instantauto` module has zero dependencies on RoadRunner; TeamCode layer handles integration.

---

## Resources

- **RoadRunner Docs**: https://rr.brott.dev/docs/v1-0/
- **FTC SDK Docs**: https://ftctechnh.github.io/ftc_app/doc/javadoc/
- **PedroPathing**: https://github.com/pedropathing/pedropathing
- **MeepMeep Simulation**: Included in `MeepMeepTestbed/` module

---

## License

This project is licensed under the MIT License - see [LICENSE](../../LICENSE) for details.