# InstantAuto Web Simulator (Reduced Feature Set)

A **browser-only** simulator for InstantAuto, simplified for core functionality.

> **Note**: This is a **separate JavaScript implementation** (not shared with the robot Java code). It supports a subset of InstantAuto features for quick prototyping and learning.

---

## Features

- **Single Active Auto Textarea**: Define variables and autonomous routine in one place
- **Two Core Actions**:
  - `STRAFE.TO(x, y, heading)` — Straight-line movement
  - `SPLINE.TO(x, y, heading, startTangent, endTangent)` — Curved path (quintic spline)
- **Robust Variable System**: Automatic type inference
  - `double` (e.g., `3.14`)
  - `int` (e.g., `42`)
  - `string` (e.g., `"hello"`)
  - `boolean` (e.g., `true`)
  - `pose2d` (e.g., `pose2d(0, 0, 0)`)
- **Control Flow**: `if / else if / else` blocks with boolean conditions
- **Slide-out Guide**: In-app syntax reference
- **Interactive Field**: 144" × 144" FTC field with animated robot

---

## Limitations (vs Robot Code)

| Feature | Web Simulator | Robot (TeamCode) |
|---------|---------------|------------------|
| `STRAFE.TO` | ✅ | ✅ |
| `SPLINE.TO` | ✅ | ✅ |
| `WAIT` | ❌ | ✅ |
| `PARALLEL` | ❌ | ✅ |
| `RACE` | ❌ | ✅ |
| `PRINT` | ❌ | ✅ |
| `HELLO.WORLD` | ❌ | ✅ |
| Subsystem actions | ❌ | ✅ |
| Condition suppliers | ❌ | ✅ |
| Sensor integration | ❌ | ✅ |
| RoadRunner fusion | ❌ (separate impl) | ✅ |

---

## Quick Start

1. Open `docs/simulator/index.html` in a browser
2. Edit the text area on the left
3. Press **Run** to simulate
4. Use the **Guide** button (☰) for syntax help

---

## Syntax Guide

### Actions
```javascript
// Straight line movement
STRAFE.TO(24, 24, 90)

// Variable-based movement
scorePose = pose2d(48, 48, 0)
STRAFE.TO(scorePose)

// Curved path
// SPLINE.TO(targetX, targetY, targetHeading, pathStartTangent, pathEndTangent)
SPLINE.TO(48, 48, 90, 0, 90)
```

### Variables
```javascript
Starting = pose2d(-60, -60, 0) // Required
isBlue = true
robotName = "Champion"
maxSpeed = 0.8
```

### Conditionals
```javascript
if (isBlue) {
    STRAFE.TO(24, 0, 0)
} else if (isRed) {
    STRAFE.TO(-24, 0, 0)
} else {
    STRAFE.TO(0, 0, 0)
}
```

---

## Technical Details

| File | Purpose |
|------|---------|
| `core.js` | Parsing, variable system, action generation |
| `sim-engine.js` | Execution orchestration, state history |
| `field-renderer.js` | HTML5 Canvas field visualization |
| `index.html` + `app.js` | UI, textarea, controls |
| `test-core.js` | Node.js smoke tests |

---

## Running Tests

```bash
cd docs/simulator
node test-core.js
```

---

## Use Cases

- **Learning InstantAuto syntax** without hardware
- **Prototyping paths** quickly (copy to robot text files)
- **Drive team practice** editing autonomous routines
- **Classroom demos** of text-based autonomous

---

## Not a Replacement For

- **MeepMeepTestbed** (Java, uses real RoadRunner, tests trajectories accurately)
- **Physical robot testing** (hardware interaction, timing, sensor feedback)

For full-featured simulation with all actions, use the **MeepMeepTestbed** module in the main project.

---

## Version

**Simulator Version**: 1.0 (Reduced Feature Set)  
**Compatible with**: InstantAuto syntax v1.0 (subset)