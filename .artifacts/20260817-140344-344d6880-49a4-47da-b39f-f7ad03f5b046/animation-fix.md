## Smooth Robot Animation with Interpolated Heading

### Changes Made

**`field-renderer.js`** - Added animation support:
- `startAnimation(trajectory)` - begins animated playback
- `updateAnimationTime(deltaTime)` - advances animation time
- `getAnimatedPose()` - returns interpolated pose at current time
- `interpolatePose(p1, p2, t)` - linear interpolation for position + shortest-path angular interpolation for heading
- `renderAnimated(state)` - draws field + trail + robot at animated pose
- `renderStatic(state)` - original static render (for step-through mode)

**`app.js`** - Updated to use smooth animation:
- `runSimulation()` now uses `renderer.startAnimation(result.fullTrajectory)` and `animateSmooth()` with `requestAnimationFrame`
- `animateSmooth()` calls `renderer.updateAnimationTime()` and `renderer.renderAnimated()` each frame
- `stepSimulation()` still uses `renderStatic()` for precise step-through

### Heading Interpolation

The `interpolatePose()` function handles heading correctly:
1. Normalizes both headings to [-180, 180]
2. Finds shortest angular path (e.g., 170° → -170° goes -20°, not +340°)
3. Linearly interpolates along that shortest path
4. Normalizes result back to [-180, 180]

### Usage

- **Run** → Smooth animation: robot moves along trajectory with heading smoothly changing between waypoints
- **Step** → Static frames: robot jumps to exact poses, good for debugging
- **Speed slider** → Adjusts animation speed (0.1x - 5x)

The robot arrow now visually represents both position AND heading as it moves along the path.