# Execution (Page 3): Autonomous — How InstantAuto Actions Execute via Actions.runBlocking()

## Overview

This page explains the **critical integration point** where InstantAuto actions meet RoadRunner's execution engine. Understanding this flow is essential for debugging, performance tuning, and extending the system.

---

## The Big Picture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        AUTONOMOUS BASE (start())                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  1. Parse action content → List<Action> (InstantAuto)                  │
│         │                                                               │
│         ▼                                                               │
│  2. ActionUtils.asActions() → Parse + Merge (top-level)                │
│         │                                                               │
│         ▼                                                               │
│  3. ActionUtils.mergeNestedActions() → Deep fusion (if/else, etc.)     │
│         │                                                               │
│         ▼                                                               │
│  4. Adapt each: ActionUtils.adapt(action, telemetry)                   │
│         │                                                               │
│         ▼                                                               │
│  5. Actions.runBlocking(                                                │
│       new RaceAction(                                                   │
│         new SequentialAction(adaptedActions)                            │
│       )                                                                 │
│     )                                                                   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Step-by-Step Execution Flow

### Phase 1: Re-parse with Merging (`AutonomousBase.start()`)

```java
@Override
public void start() {
    // Clear previous actions
    actions.clear();
    
    // Re-parse action content WITH MecanumDrive for fusion
    List<Action> mergedActions = ActionUtils.asActions(autoParser.getActionContent(), mecanumDrive);
    
    // Deep merge nested actions (if/else branches, parallel, race)
    if (mergedActions != null) {
        mergedActions = ActionUtils.mergeNestedActions(mergedActions, mecanumDrive);
        
        // Adapt each InstantAuto Action → RoadRunner Action
        for (Action action : mergedActions) {
            actions.add(ActionUtils.adapt(action, telemetry));
        }
    }
    
    // Execute via RR
    Actions.runBlocking(
        new RaceAction(
            new SequentialAction(actions)
        )
    );
}
```

> **Why re-parse in start()?** 
> - `init()` parses without `MecanumDrive` (not initialized yet)
> - `start()` has `mecanumDrive` → can fuse trajectories using actual pose
> - `getActionContent()` returns raw string from `init()` parse

---

### Phase 2: Adaptation (`ActionUtils.adapt()`)

```java
public static com.acmerobotics.roadrunner.Action adapt(
        final com.example.instantauto.actions.Action action, 
        final Telemetry telemetry) {
    
    // If already wrapped RR action, unwrap
    if (action instanceof WrappedRRAction) {
        return ((WrappedRRAction) action).getRRAction();
    }
    
    // Otherwise wrap in RR Action interface
    return new com.acmerobotics.roadrunner.Action() {
        @Override public boolean run(TelemetryPacket packet) {
            return action.run();  // Call InstantAuto's run()
        }
    };
}
```

**Key Points:**
- `TelemetryPacket` is created fresh per `runBlocking` iteration
- InstantAuto `Action.run()` takes **no args**; RR `Action.run()` takes `TelemetryPacket`
- Adapter discards packet (InstantAuto actions use `telemetry` field directly)

---

### Phase 3: RoadRunner Execution (`Actions.runBlocking()`)

```java
// From com.acmerobotics.roadrunner.ftc.Actions
public static void runBlocking(Action action) {
    TelemetryPacket packet = new TelemetryPacket();
    while (action.run(packet)) {
        packet = new TelemetryPacket();  // New packet each iteration
        // Loop runs at ~60Hz (opmode loop rate)
    }
}
```

**With RaceAction + SequentialAction:**

```java
Actions.runBlocking(
    new RaceAction(          // Runs ALL actions simultaneously
        new SequentialAction( // But sequentially runs our list
            action1, action2, action3, ...
        )
    )
);
```

**Execution semantics:**
1. `RaceAction.run(packet)` calls `run()` on **all** child actions
2. `SequentialAction.run(packet)` runs children **in order**, advancing when one returns `false`
3. `RaceAction` returns `true` while **any** child returns `true`
4. Loop continues until **all** actions complete

---

## How Different Action Types Execute

### 1. BuilderAction (Fused Trajectory)

```java
// After fusion: single WrappedRRAction containing RR TrajectoryAction
// RR TrajectoryAction.run(packet):
//   - Follows trajectory using PID/feedforward
//   - Updates packet with robot pose, wheel velocities
//   - Returns true until trajectory complete
```

**Fusion benefit**: One continuous trajectory → smooth motion, no stops between segments.

### 2. Cached BuilderAction (if/else branches)

```java
// From ActionManager.createCachedBuilderAction():
new Action() {
    private com.acmerobotics.roadrunner.Action cachedAction;

    @Override public boolean run() {
        if (cachedAction == null) {
            // Build ONCE on first run (uses current pose)
            cachedAction = delegate.apply(
                mecanumDrive.actionBuilder(mecanumDrive.localizer.getPose())
            ).build();
        }
        return cachedAction.run(new TelemetryPacket());
    }
}
```

- **First run**: Builds trajectory from **current robot pose**
- **Subsequent runs**: Reuses cached trajectory
- **Critical for if/else**: Branch not taken until condition evaluated at runtime

### 3. WrappedRRAction (PARALLEL, RACE, WAIT)

```java
// PARALLEL: wraps RR ParallelAction
// RACE: wraps custom RaceAction
// WAIT: wraps RR SleepAction

// All execute via RR's run(packet) directly
```

### 4. Simple InstantAuto Action (PRINT, assignments)

```java
// Adapted via adapt() → RR Action that calls action.run()
// Returns true/false per InstantAuto semantics
```

---

## TelemetryPacket Flow

```
Actions.runBlocking()
    │
    ▼
while (raceAction.run(packet))  // packet created each iteration
    │
    ├──► SequentialAction.run(packet)
    │       │
    │       ├──► action1.run(packet)  → BuilderAction.run() → RR Trajectory.run(packet)
    │       │       │
    │       │       └──► packet.fieldOverlay().setPose(...)  (RR updates pose)
    │       │
    │       ├──► action2.run(packet)  → WrappedRRAction.run() → RR ParallelAction.run(packet)
    │       │
    │       └──► action3.run(packet)  → Adapted InstantAuto → action.run()
    │
    └──► FtcDashboard.send(packet)  (if using dashboard)
```

**Key insight**: `TelemetryPacket` is the **communication channel** for:
- Robot pose (field overlay)
- Trajectory visualization
- Custom telemetry data
- Dashboard rendering

---

## Timing & Loop Rate

| Component | Rate | Notes |
|-----------|------|-------|
| `Actions.runBlocking()` | ~60 Hz | Limited by opmode loop / hardware cycle |
| RR Trajectory following | ~60 Hz | PID updates per iteration |
| InstantAuto `action.run()` | ~60 Hz | Called via adapter each iteration |
| Sensor suppliers | On-demand | Called when `MetaFieldRegistry.getValue()` accessed |

---

## Common Execution Issues

### Issue 1: Trajectory Rebuilt Every Loop

**Symptom**: Robot stutters, poor performance, "trajectory too short" errors

**Cause**: BuilderAction not cached, used in if/else without fusion

**Fix**: Use `createCachedBuilderAction()` in factory:
```java
return createCachedBuilderAction(builder -> builder.strafeTo(...));
```

### Issue 2: Wrong Starting Pose for Cached Actions

**Symptom**: Robot drives to wrong location from if/else branch

**Cause**: Cached action built with stale pose

**Fix**: Cache uses `mecanumDrive.localizer.getPose()` at **first run time** — correct for if/else since branch executes at runtime.

### Issue 3: Actions Never Complete

**Symptom**: Autonomous hangs, doesn't progress

**Cause**: Action `run()` always returns `true`

**Fix**: Ensure actions return `false` when done:
```java
@Override public boolean run() {
    if (done) return false;
    // ... work ...
    return true;  // Continue
}
```

### Issue 4: Supplier Capturing Old HardwareMap

**Symptom**: Crash or stale data after opmode restart

**Fix**: Always clear in `stop()`:
```java
@Override public void stop() {
    MetaFieldRegistry.clear();
    UserActionRegistry.clear();
}
```

---

## Debugging Execution

### 1. Enable RR Trajectory Visualization

```java
// In MecanumDrive or via dashboard
// Actions.runBlocking() automatically sends TelemetryPacket to dashboard
// View at http://<robot-ip>:8080/dash
```

### 2. Log Action Transitions

```java
// Wrap actions for debugging
Action debugWrap(Action action, String name) {
    return new Action() {
        boolean started = false;
        @Override public boolean run() {
            if (!started) { telemetry.addLine("▶ " + name); started = true; }
            boolean result = action.run();
            if (!result) telemetry.addLine("✓ " + name);
            return result;
        }
    };
}
```

### 3. Inspect TelemetryPacket

```java
// In custom RR Action
@Override public boolean run(TelemetryPacket packet) {
    packet.put("myCustomData", value);  // Visible in dashboard
    return true;
}
```

---

## Advanced: Custom RR Action Integration

### Creating a Custom RR Action

```java
public class MyCustomRRAction implements com.acmerobotics.roadrunner.Action {
    private double startTime;
    private double duration;

    public MyCustomRRAction(double duration) { this.duration = duration; }

    @Override public boolean run(TelemetryPacket packet) {
        if (startTime == 0) startTime = packet.getTime();  // Or System.nanoTime()
        
        double elapsed = (packet.getTime() - startTime) / 1e9;
        
        // Do work: control motors, read sensors, etc.
        motor.setPower(Math.sin(elapsed));
        
        // Visualize
        packet.fieldOverlay().setStroke("#ff0000");
        packet.fieldOverlay().strokeCircle(packet.robotPose().getX(), packet.robotPose().getY(), 0.5);
        
        return elapsed < duration;  // true = continue, false = done
    }
}
```

### Registering in InstantAuto

```java
UserActionRegistry.register(new MiniAction("CUSTOM.RR", params -> {
    double[] d = ActionUtils.asDoubles(params, 1);
    if (d != null) {
        return ActionUtils.wrap(new MyCustomRRAction(d[0]));
    }
    return null;
}));
```

### Text File Usage

```ini
CUSTOM.RR(2.5)  # Runs custom action for 2.5 seconds
```

---

## Files Reference

| File | Location |
|------|----------|
| `AutonomousBase.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/` |
| `ActionUtils.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/action/` |
| `ActionManager.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/action/` |
| `MecanumDrive.java` | `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/roadrunner/` |