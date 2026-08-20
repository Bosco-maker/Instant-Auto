# InstantAuto Web Simulator (Reduced Feature Set)

A **browser-only** simulator for InstantAuto, simplified for core functionality.

## Features

- **Single Active Auto Textarea**: Define your variables and autonomous routine in one place.
- **Two Core Actions**:
  - `STRAFE.TO(x, y, heading)`: Straight-line movement to a target pose.
  - `SPLINE.TO(x, y, heading, startHeading, endHeading)`: Smooth curved path using quintic spline interpolation.
- **Robust Variable System**: Automatic type inference for:
  - `double` (e.g., `3.14`)
  - `int` (e.g., `42`)
  - `string` (e.g., `"hello"`)
  - `boolean` (e.g., `true`)
  - `pose2d` (e.g., `pose2d(0, 0, 0)`)
- **Control Flow**: `if / else if / else` blocks with boolean conditions.
- **Slide-out Guide**: In-app syntax reference for actions and variables.
- **Interactive Field**: 144" × 144" FTC field visualization with animated robot movement.

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

- **Core Engine**: `core.js` handles parsing and action generation.
- **Simulation**: `sim-engine.js` orchestrates the execution and state history.
- **Rendering**: `field-renderer.js` uses HTML5 Canvas for visualization.
- **UI**: `index.html` and `app.js` provide a responsive, single-page interface.

### Verification
Run Node.js smoke tests:
```bash
cd docs/simulator
node test-core.js
```
