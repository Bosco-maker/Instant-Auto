# Contributing

## Project Structure

```
Instant-Auto/
├── instantauto/              # Core module (no dependencies)
│   ├── src/main/java/com/example/instantauto/
│   │   ├── actions/          # Action, MiniAction, UserAction, Registries, AutoParser
│   │   └── configs/          # MetaField, MetaFieldRegistry, ConfigParser, ConfigEntry
│   ├── src/test/             # Unit & integration tests
│   └── build.gradle.kts
├── TeamCode/                 # Robot-specific layer (RoadRunner + FTC SDK)
│   ├── configs/              # ConfigManager, MetaField types (Pose2d, IntakeSetting)
│   ├── action/               # ActionManager, ActionUtils, BuilderAction impls
│   ├── subsystems/           # Hardware subsystems (Intake, Arm, etc.)
│   ├── opmodes/              # AutonomousBase, TeleOp
│   ├── roadrunner/           # MecanumDrive (generated)
│   └── build.gradle.kts
├── MeepMeepTestbed/          # Simulation module
│   ├── simulation/           # SimPose2d, SimulationActionManager, etc.
│   ├── textfiles/            # Test configs
│   └── build.gradle
├── docs/                     # MkDocs documentation
│   ├── user/                 # User Guide
│   ├── programmer/           # Programmer Guide
│   ├── developer/            # Developer Guide
│   ├── reference/            # Reference
│   └── simulator/            # Web simulator
├── pureJava/                 # Development-only reference implementation
│   └── src/main/java/com/example/purejava/
└── mkdocs.yml
```

---

## Architecture Decision Records (ADRs)

### ADR-001: Pathing-Agnostic Core
**Status**: Accepted  
**Context**: InstantAuto should support RoadRunner, PedroPathing, and future pathing libraries.  
**Decision**: Core `instantauto` module has **zero dependencies** on pathing libraries. All pathing-specific code lives in TeamCode layer.  
**Consequences**: Clean separation, multiple pathing support, but requires TeamCode implementation per library.

### ADR-002: Two-Phase Parsing
**Status**: Accepted  
**Context**: Need `Starting` pose before hardware init, but action parsing needs registered MiniActions.  
**Decision**: `AutoParser` has `parseAutoConfig()` (config phase) and `parseActions()` (action phase).  
**Consequences**: `AutonomousBase.init()` calls both phases separately with hardware init between.

### ADR-003: Supplier-Based Dynamic Variables
**Status**: Accepted  
**Context**: Sensor readings, gamepad inputs must be live.  
**Decision**: `MetaFieldRegistry.ConfigEntry` supports `Supplier<T>` evaluated on every `getValue()`.  
**Consequences**: Live data, but requires `clear()` in `stop()` to prevent memory leaks.

### ADR-004: Trajectory Fusion via BuilderAction
**Status**: Accepted  
**Context**: Consecutive path segments should be smooth, not stop-and-go.  
**Decision**: `BuilderAction` interface with `apply(TrajectoryActionBuilder)` + caching + `merge()` utility.  
**Consequences**: Smooth trajectories, but adds complexity to action factories.

### ADR-005: If/Else Runtime Evaluation
**Status**: Accepted  
**Context**: Conditions (alliance, sensors) must be evaluated at runtime, not parse time.  
**Decision**: If/else creates an `Action` that evaluates condition on first `run()` (lazy).  
**Consequences**: Supports dynamic conditions, but requires `mergeNestedActions()` for trajectory fusion in branches.

---

## Adding a New Pathing Library (e.g., PedroPathing)

### 1. Core Module (Unchanged)
`instantauto` module requires **no changes** — it's pathing-agnostic.

### 2. TeamCode Layer (New Implementation)

Create new module or package: `TeamCode/src/main/java/.../pedropathing/`

#### Required Components:

| Component | RoadRunner Implementation | PedroPathing Equivalent |
|-----------|--------------------------|------------------------|
| **Drive Class** | `MecanumDrive` | `PedroDrive` (or similar) |
| **Trajectory Builder** | `TrajectoryActionBuilder` | `PathChainBuilder` / `BezierPathBuilder` |
| **Action Interface** | `com.acmerobotics.roadrunner.Action` | `com.pedropathing.Action` |
| **Execution** | `Actions.runBlocking()` | `PedroRunner.runAction()` |
| **Pose Type** | `com.acmerobotics.roadrunner.Pose2d` | `com.pedropathing.geometry.Pose2d` |

#### Implementation Checklist:

1. **Drive Wrapper**
```java
public class PedroDrive {
    public PedroDrive(HardwareMap hw, Pose2d startPose) { ... }
    public Pose2d getPose() { ... }
    public PathChainBuilder actionBuilder(Pose2d pose) { ... }
    public PathChainBuilder actionBuilder(PathChain chain) { ... }
}
```

2. **BuilderAction Implementation**
```java
public interface PedroBuilderAction extends Action {
    PathChainBuilder apply(PathChainBuilder builder);
}

private Action createCachedBuilderAction(PedroBuilderAction delegate) {
    return new Action() {
        private com.pedropathing.Action cached;
        @Override public boolean run() {
            if (cached == null) {
                cached = delegate.apply(drive.actionBuilder(drive.getPose())).build();
            }
            return cached.run();
        }
    };
}
```

3. **ActionUtils Adaptation**
```java
public static com.pedropathing.Action adapt(Action action, Telemetry telemetry) { ... }
public static List<com.pedropathing.Action> mergeNestedActions(List<Action> actions, PedroDrive drive) { ... }
```

4. **ActionManager Factories**
```java
UserActionRegistry.register(new MiniAction("STRAFE.TO", params -> {
    // Convert to PedroPathing path
    return createCachedBuilderAction(builder -> builder
        .lineTo(new Vector2d(x, y))
        .setHeading(heading)
    );
}));
```

5. **AutonomousBase**
```java
PedroDrive drive = new PedroDrive(hardwareMap, startPose);
actionManager.init(drive, telemetry, hardwareMap);
UserActionRegistry.setActionMerger(a -> ActionUtils.mergeNestedActions(a, drive));

// In start():
Actions.runBlocking( // or PedroRunner.runAction(
    new SequentialAction(adaptedActions)
);
```

### 3. MetaField Types (Reusable)

Your existing `Pose2d` and custom types work unchanged — they implement `MetaField<T>` from core.

### 4. Text Files (Unchanged)

```ini
# Same text files work with PedroPathing!
Starting = pose2d(-24, 0, 0)
STRAFE.TO(30, 0, 0)
SPLINE.TO(scorePose, 45, -45)
```

---

## Code Style & Conventions

### Java
- **Java 11** (FTC SDK requirement)
- **Interfaces** over abstract classes for extensibility
- **Functional interfaces** for factories (`Function<Object, Action>`)
- **Null checks** on all factory returns (return `null` on invalid params)
- **Static utility classes** (`ActionUtils`, `ConfigParser`) for stateless operations

### Text File Syntax
- **Comments**: `//` or `#`
- **Assignments**: `key = value`
- **Actions**: `ACTION_NAME(param1, param2, ...)`
- **Braces**: Required for composites and if/else
- **Angles**: Always **degrees** in text files (converted to radians in Java)

### Naming Conventions
| Element | Convention | Example |
|---------|------------|---------|
| Action names | UPPERCASE with dots | `STRAFE.TO`, `INTAKE.ON` |
| Variable names | camelCase | `scorePose`, `maxVelocity` |
| MetaField types | camelCase | `pose2d`, `intakeSetting` |
| Java classes | PascalCase | `ActionManager`, `Pose2d` |
| Methods | camelCase | `strafeToFactory`, `createCachedBuilderAction` |

---

## Testing

### Unit Tests (instantauto module)
```bash
./gradlew :instantauto:test
```

### Integration Test (instantauto module)
```bash
./gradlew :instantauto:test --tests "com.example.instantauto.integration.IntegrationTest"
```

### MeepMeep Simulation
```bash
./gradlew :MeepMeepTestbed:run
```

### Physical Robot
Deploy `AutonomousBase` subclass to Robot Controller.

---

## Release Process

1. Update version in `gradle/libs.versions.toml`
2. Update `CHANGELOG.md`
3. Tag release: `git tag vX.Y.Z`
4. Build: `./gradlew :instantauto:build`
5. Publish to Maven Local: `./gradlew :instantauto:publishToMavenLocal`

---

## Resources

- [RoadRunner 1.0 Docs](https://rr.brott.dev/docs/v1-0/introduction/)
- [FTC SDK Docs](https://ftc-docs.firstinspires.org/)
- [MkDocs Material](https://squidfunk.github.io/mkdocs-material/)
- [GitHub Repository](https://github.com/Bosco-Maker/Instant-Auto)