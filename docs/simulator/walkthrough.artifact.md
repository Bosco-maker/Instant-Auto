# InstantAuto Web Simulator - Simplified Implementation Walkthrough

I have successfully simplified the InstantAuto Web Simulator to a robust, single-input autonomous routine editor.

## Key Accomplishments

### 1. Unified Autonomous Routine Editor
- Removed `GeneralRobotSettings` and `UserActionSettings` textareas.
- Variables and actions are now defined in a single `ACTIVE Auto` text area.
- Implemented a single-pass parser that extracts variables and actions concurrently.

### 2. Robust Variable System
- Implemented automatic type inference for `double`, `int`, `string`, `boolean`, and `pose2d`.
- Variables can be defined anywhere in the script using `name = value`.
- Case-insensitive variable lookup for conditions and action parameters.

### 3. Core Actions
- **STRAFE.TO(x, y, heading)**: Straight-line movement (renamed from `GO.TO.POSE2D`).
- **SPLINE.TO(x, y, heading, startH, endH)**: New action using quintic spline interpolation for smooth, curved paths.
- Updated `field-renderer.js` with distinct colors: Blue for Strafe, Green for Spline.

### 4. Control Flow
- Implemented `if / else if / else` blocks.
- Boolean conditions can be literals (`true`/`false`) or variable references.
- Correctly handles multi-line blocks and nested-like structures.

### 5. Robust UI & Guide Tab
- Fixed UI errors reported by the user (`TypeError` and `ReferenceError`) by using early window exposure and safe DOM access.
- Added a slide-out **GUIDE** tab that provides a quick syntax reference for all features.
- Keyboard shortcuts: `Space/Enter` for Run, `ArrowRight` for Step, `R` for Reset.

## Verification Summary

### Automated Tests
Ran `node test-core.js` covering:
- [x] Variable type inference (all types)
- [x] STRAFE.TO (literals and variables)
- [x] SPLINE.TO (trajectory generation and smooth paths)
- [x] Simulator Engine (full run and step mode)
- [x] Error handling (missing `Starting` pose, unknown actions)

### Manual Verification
- Verified the UI loads correctly without errors.
- Confirmed the **GUIDE** tab opens and closes smoothly.
- Tested the default autonomous routine to confirm animation and trajectory rendering.
- Verified coordinate system (+X right, +Y up) and AprilTag visualization.

## Instructions for Use
1. Open `index.html` in a browser.
2. Click the **GUIDE** button to see the syntax reference.
3. Edit the `ACTIVE Auto` script or use the default example.
4. Press **Run** to animate or **Step** to debug.
5. Adjust the **Speed** slider to control animation playback.
