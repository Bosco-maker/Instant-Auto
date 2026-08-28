/**
 * Field Renderer - Draws FTC field, robot, and trajectory on canvas
 * FTC Field: 144" x 144" (12ft x 12ft)
 * Supports animated robot movement along trajectory with interpolated heading
 * Supports custom field background images for different seasons
 */

export class FieldRenderer {
    constructor(canvas, fieldConfig = null) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');

        // Field configuration (from field registry)
        this.fieldConfig = fieldConfig || { width: 144, height: 144, image: null };

        // Field dimensions (inches)
        this.fieldWidth = this.fieldConfig.width || 144;
        this.fieldHeight = this.fieldConfig.height || 144;

        // Rendering options
        this.padding = 40; // pixels around field
        this.robotSize = 14; // robot triangle size in pixels
        this.trailWidth = 2;
        this.trailColor = '#007acc';
        this.robotColor = '#d32f2f';
        this.robotHeadingColor = '#fff';

        // Coordinate transform: field inches -> canvas pixels
        this.scale = 1;
        this.offsetX = 0;
        this.offsetY = 0;

        // Field image
        this.fieldImage = null;
        this.imageLoaded = false;
        this.imageError = false;

        // Animation state
        this.animationTime = 0;
        this.currentTrajectory = null;
        this.isAnimating = false;

        // Load field image if specified
        if (this.fieldConfig.image) {
            this.loadFieldImage(this.fieldConfig.image);
        }

        this.resize();
        window.addEventListener('resize', () => this.resize());
    }

    loadFieldImage(src) {
        this.fieldImage = new Image();
        this.fieldImage.onload = () => {
            this.imageLoaded = true;
            this.imageError = false;
            this.renderStatic({ pose: { x: 0, y: 0, heading: 0 }, trajectory: [] });
        };
        this.fieldImage.onerror = () => {
            console.warn(`Failed to load field image: ${src}`);
            this.imageLoaded = false;
            this.imageError = true;
            this.renderStatic({ pose: { x: 0, y: 0, heading: 0 }, trajectory: [] });
        };
        this.fieldImage.src = src;
    }

    setFieldConfig(config) {
        this.fieldConfig = config;
        this.fieldWidth = config.width || 144;
        this.fieldHeight = config.height || 144;
        this.imageLoaded = false;
        this.imageError = false;

        if (config.image) {
            this.loadFieldImage(config.image);
        } else {
            this.renderStatic({ pose: { x: 0, y: 0, heading: 0 }, trajectory: [] });
        }
    }

    resize() {
        const rect = this.canvas.getBoundingClientRect();
        const dpr = window.devicePixelRatio || 1;

        this.canvas.width = rect.width * dpr;
        this.canvas.height = rect.height * dpr;
        this.canvas.style.width = rect.width + 'px';
        this.canvas.style.height = rect.height + 'px';
        this.ctx.scale(dpr, dpr);

        // Calculate scale to fit field in canvas with padding
        const canvasWidth = rect.width;
        const canvasHeight = rect.height;

        const scaleX = (canvasWidth - 2 * this.padding) / this.fieldWidth;
        const scaleY = (canvasHeight - 2 * this.padding) / this.fieldHeight;
        this.scale = Math.min(scaleX, scaleY);

        this.offsetX = (canvasWidth - this.fieldWidth * this.scale) / 2;
        this.offsetY = (canvasHeight - this.fieldHeight * this.scale) / 2;
    }

    // Transform field coordinates (inches, origin at center) to canvas pixels
    fieldToCanvas(x, y) {
        // Field: (0,0) at center, +X right, +Y up
        // Canvas: (0,0) at top-left, +X right, +Y down
        return {
            x: this.offsetX + (x + this.fieldWidth / 2) * this.scale,
            y: this.offsetY + (this.fieldHeight / 2 - y) * this.scale
        };
    }

    // Clear canvas
    clear() {
        const rect = this.canvas.getBoundingClientRect();
        this.ctx.clearRect(0, 0, rect.width, rect.height);
    }

    // Draw complete field - uses image if loaded, otherwise programmatic fallback
    drawField() {
        const { x: left, y: top } = this.fieldToCanvas(-this.fieldWidth/2, this.fieldHeight/2);
        const width = this.fieldWidth * this.scale;
        const height = this.fieldHeight * this.scale;

        if (this.imageLoaded && this.fieldImage) {
            // Draw field background image
            this.ctx.drawImage(this.fieldImage, left, top, width, height);
        } else {
            // Fallback: original programmatic field drawing
            this.drawFieldBackground();
            this.drawFieldLines();
            this.drawAprilTags();
            this.drawCoordinateLabels();
        }
    }

    // Programmatic fallback methods (original drawing code)
    drawFieldBackground() {
        const { x: left, y: top } = this.fieldToCanvas(-this.fieldWidth/2, this.fieldHeight/2);
        const width = this.fieldWidth * this.scale;
        const height = this.fieldHeight * this.scale;

        // Field base
        this.ctx.fillStyle = '#e8f5e9';
        this.ctx.fillRect(left, top, width, height);

        // Border
        this.ctx.strokeStyle = '#2e7d32';
        this.ctx.lineWidth = 3;
        this.ctx.strokeRect(left, top, width, height);
    }

    drawFieldLines() {
        this.ctx.strokeStyle = '#81c784';
        this.ctx.lineWidth = 1;
        this.ctx.setLineDash([10 * this.scale, 10 * this.scale]);

        // Center lines
        const { x: cx, y: cy } = this.fieldToCanvas(0, 0);
        const { x: left, y: top } = this.fieldToCanvas(-this.fieldWidth/2, this.fieldHeight/2);
        const { x: right, y: bottom } = this.fieldToCanvas(this.fieldWidth/2, -this.fieldHeight/2);

        // Vertical center line
        this.ctx.beginPath();
        this.ctx.moveTo(cx, top);
        this.ctx.lineTo(cx, bottom);
        this.ctx.stroke();

        // Horizontal center line
        this.ctx.beginPath();
        this.ctx.moveTo(left, cy);
        this.ctx.lineTo(right, cy);
        this.ctx.stroke();

        this.ctx.setLineDash([]);

        // Center circle (30 inch radius)
        this.ctx.beginPath();
        this.ctx.arc(cx, cy, 30 * this.scale, 0, Math.PI * 2);
        this.ctx.stroke();

        // Starting lines (tiles)
        this.drawTileGrid();
    }

    drawTileGrid() {
        // FTC field has 24" x 24" tiles
        const tileSize = 24;
        this.ctx.strokeStyle = '#a5d6a7';
        this.ctx.lineWidth = 0.5;

        for (let x = -this.fieldWidth/2; x <= this.fieldWidth/2; x += tileSize) {
            const { x: x1, y: y1 } = this.fieldToCanvas(x, this.fieldHeight/2);
            const { x: x2, y: y2 } = this.fieldToCanvas(x, -this.fieldHeight/2);
            this.ctx.beginPath();
            this.ctx.moveTo(x1, y1);
            this.ctx.lineTo(x2, y2);
            this.ctx.stroke();
        }

        for (let y = -this.fieldHeight/2; y <= this.fieldHeight/2; y += tileSize) {
            const { x: x1, y: y1 } = this.fieldToCanvas(-this.fieldWidth/2, y);
            const { x: x2, y: y2 } = this.fieldToCanvas(this.fieldWidth/2, y);
            this.ctx.beginPath();
            this.ctx.moveTo(x1, y1);
            this.ctx.lineTo(x2, y2);
            this.ctx.stroke();
        }
    }

    drawAprilTags() {
        // AprilTag locations on FTC field (approximate)
        // Blue alliance side (positive Y): tags 1-6
        // Red alliance side (negative Y): tags 7-12
        // Coordinates from FTC field specs
        const tags = [
            // Blue side
            { id: 1, x: -60, y: 66 },
            { id: 2, x: -24, y: 66 },
            { id: 3, x: 24, y: 66 },
            { id: 4, x: 60, y: 66 },
            { id: 5, x: 66, y: 24 },
            { id: 6, x: 66, y: -24 },
            // Red side
            { id: 7, x: 60, y: -66 },
            { id: 8, x: 24, y: -66 },
            { id: 9, x: -24, y: -66 },
            { id: 10, x: -60, y: -66 },
            { id: 11, x: -66, y: -24 },
            { id: 12, x: -66, y: 24 },
        ];

        this.ctx.font = `${Math.max(10, 12 * this.scale)}px monospace`;
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';

        for (const tag of tags) {
            const pos = this.fieldToCanvas(tag.x, tag.y);

            // Tag background
            this.ctx.fillStyle = tag.id <= 6 ? '#1976d2' : '#d32f2f';
            this.ctx.beginPath();
            this.ctx.arc(pos.x, pos.y, 8 * this.scale, 0, Math.PI * 2);
            this.ctx.fill();

            // Tag ID
            this.ctx.fillStyle = '#fff';
            this.ctx.fillText(tag.id.toString(), pos.x, pos.y);
        }
    }

    drawCoordinateLabels() {
        this.ctx.font = `${Math.max(9, 10 * this.scale)}px monospace`;
        this.ctx.fillStyle = '#666';
        this.ctx.textAlign = 'center';

        // X axis labels
        for (let x = -72; x <= 72; x += 24) {
            const pos = this.fieldToCanvas(x, -this.fieldHeight/2 - 10);
            this.ctx.fillText(`${x}"`, pos.x, pos.y);
        }

        this.ctx.textAlign = 'right';
        this.ctx.textBaseline = 'middle';
        // Y axis labels
        for (let y = -72; y <= 72; y += 24) {
            const pos = this.fieldToCanvas(-this.fieldWidth/2 - 10, y);
            this.ctx.fillText(`${y}"`, pos.x, pos.y);
        }
    }

    // Draw trajectory trail
    drawTrajectory(trajectory) {
        if (!trajectory || trajectory.length < 2) return;

        this.ctx.lineWidth = this.trailWidth;
        this.ctx.lineCap = 'round';
        this.ctx.lineJoin = 'round';

        // Draw as gradient segments by action
        let segmentStart = 0;

        for (let i = 0; i < trajectory.length; i++) {
            const point = trajectory[i];
            const nextPoint = trajectory[i + 1];

            if (nextPoint && point.actionName !== nextPoint.actionName) {
                this.drawTrajectorySegment(trajectory, segmentStart, i, point.actionName);
                segmentStart = i;
            }
        }
        // Last segment
        if (segmentStart < trajectory.length - 1) {
            this.drawTrajectorySegment(trajectory, segmentStart, trajectory.length - 1, trajectory[segmentStart].actionName);
        }
    }

    drawTrajectorySegment(trajectory, startIdx, endIdx, actionName) {
        if (endIdx <= startIdx) return;

        // Color by action type
        let color = this.trailColor;
        if (actionName.includes('STRAFE.TO')) color = '#007acc';        // Blue
        else if (actionName.includes('SPLINE.TO')) color = '#2e7d32';   // Green

        this.ctx.strokeStyle = color;
        this.ctx.globalAlpha = 0.8;

        this.ctx.beginPath();
        const start = this.fieldToCanvas(trajectory[startIdx].x, trajectory[startIdx].y);
        this.ctx.moveTo(start.x, start.y);

        for (let i = startIdx + 1; i <= endIdx; i++) {
            const pos = this.fieldToCanvas(trajectory[i].x, trajectory[i].y);
            this.ctx.lineTo(pos.x, pos.y);
        }
        this.ctx.stroke();
        this.ctx.globalAlpha = 1.0;
    }

    // Draw robot at pose with heading
    drawRobot(pose) {
        const pos = this.fieldToCanvas(pose.x, pose.y);
        const heading = pose.heading * Math.PI / 180; // Convert degrees to radians

        this.ctx.save();
        this.ctx.translate(pos.x, pos.y);
        this.ctx.rotate(-heading); // Canvas Y is inverted

        // Robot body (triangle/arrow)
        this.ctx.fillStyle = this.robotColor;
        this.ctx.beginPath();
        this.ctx.moveTo(this.robotSize, 0); // Front tip
        this.ctx.lineTo(-this.robotSize * 0.7, this.robotSize * 0.7); // Rear left
        this.ctx.lineTo(-this.robotSize * 0.7, -this.robotSize * 0.7); // Rear right
        this.ctx.closePath();
        this.ctx.fill();

        // Robot outline
        this.ctx.strokeStyle = '#fff';
        this.ctx.lineWidth = 2;
        this.ctx.stroke();

        // Heading indicator (line from center to front)
        this.ctx.strokeStyle = this.robotHeadingColor;
        this.ctx.lineWidth = 2;
        this.ctx.beginPath();
        this.ctx.moveTo(this.robotSize * 0.3, 0);
        this.ctx.lineTo(this.robotSize, 0);
        this.ctx.stroke();

        this.ctx.restore();
    }

    // Draw current target/waypoint
    drawTarget(pose) {
        if (!pose) return;
        const pos = this.fieldToCanvas(pose.x, pose.y);

        this.ctx.strokeStyle = '#ff9800';
        this.ctx.lineWidth = 2;
        this.ctx.setLineDash([5 * this.scale, 5 * this.scale]);
        this.ctx.beginPath();
        this.ctx.arc(pos.x, pos.y, 12 * this.scale, 0, Math.PI * 2);
        this.ctx.stroke();
        this.ctx.setLineDash([]);
    }

    // Calculate interpolated pose at a given time along the trajectory
    getPoseAtTime(trajectory, time) {
        if (!trajectory || trajectory.length === 0) return null;
        if (trajectory.length === 1) return {
            x: trajectory[0].x,
            y: trajectory[0].y,
            heading: trajectory[0].heading
        };

        // Find the segment containing this time
        for (let i = 0; i < trajectory.length - 1; i++) {
            const p1 = trajectory[i];
            const p2 = trajectory[i + 1];

            if (time >= p1.t && time <= p2.t) {
                const duration = p2.t - p1.t;
                if (duration <= 0) {
                    return { x: p1.x, y: p1.y, heading: p1.heading };
                }
                const t = (time - p1.t) / duration;
                return this.interpolatePose(p1, p2, t);
            }
        }

        // Time beyond trajectory end - return last pose
        const last = trajectory[trajectory.length - 1];
        return { x: last.x, y: last.y, heading: last.heading };
    }

    // Linearly interpolate between two poses (including heading)
    interpolatePose(p1, p2, t) {
        // Clamp t
        t = Math.max(0, Math.min(1, t));

        // Linear interpolation for position
        const x = p1.x + (p2.x - p1.x) * t;
        const y = p1.y + (p2.y - p1.y) * t;

        // Angular interpolation for heading (shortest path)
        let h1 = p1.heading;
        let h2 = p2.heading;

        // Normalize to [-180, 180]
        while (h1 > 180) h1 -= 360;
        while (h1 < -180) h1 += 360;
        while (h2 > 180) h2 -= 360;
        while (h2 < -180) h2 += 360;

        // Find shortest angular path
        let diff = h2 - h1;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;

        const heading = h1 + diff * t;

        // Normalize result
        let resultHeading = heading;
        while (resultHeading > 180) resultHeading -= 360;
        while (resultHeading < -180) resultHeading += 360;

        return { x, y, heading: resultHeading };
    }

    // Start animation with a trajectory
    startAnimation(trajectory) {
        this.currentTrajectory = trajectory;
        this.animationTime = trajectory[0]?.t || 0;
        this.isAnimating = true;
    }

    // Stop animation
    stopAnimation() {
        this.isAnimating = false;
        this.currentTrajectory = null;
    }

    // Update animation time (call from animation loop)
    updateAnimationTime(deltaTime) {
        if (!this.isAnimating || !this.currentTrajectory) return false;

        const lastTime = this.currentTrajectory[this.currentTrajectory.length - 1]?.t;
        this.animationTime += deltaTime;

        if (this.animationTime >= lastTime) {
            this.animationTime = lastTime;
            this.isAnimating = false;
            return false; // Animation complete
        }
        return true; // Animation continuing
    }

    // Get current animated pose
    getAnimatedPose() {
        if (!this.currentTrajectory) return null;
        return this.getPoseAtTime(this.currentTrajectory, this.animationTime);
    }

    // Render a static frame (for step-through mode)
    renderStatic(state, options = {}) {
        this.clear();
        this.drawField();

        // Draw full trajectory trail
        if (state.trajectory && state.trajectory.length > 0) {
            this.drawTrajectory(state.trajectory);
        }

        // Draw target if provided
        if (options.targetPose) {
            this.drawTarget(options.targetPose);
        }

        // Draw robot at current pose
        this.drawRobot(state.pose);
    }

    // Render animated frame
    renderAnimated(state, options = {}) {
        this.clear();
        this.drawField();

        // Draw full trajectory trail (already traveled portion)
        if (state.trajectory && state.trajectory.length > 0) {
            this.drawTrajectory(state.trajectory);
        }

        // Draw target if provided
        if (options.targetPose) {
            this.drawTarget(options.targetPose);
        }

        // Draw robot at animated pose
        const animatedPose = this.getAnimatedPose();
        if (animatedPose) {
            this.drawRobot(animatedPose);
        }
    }
}

// Legend helper
export function updateLegend(legendElement, trajectory) {
    if (!trajectory || trajectory.length === 0) {
        legendElement.style.display = 'none';
        return;
    }

    const actions = [...new Set(trajectory.map(p => p.actionName).filter(Boolean))];
    if (actions.length === 0) {
        legendElement.style.display = 'none';
        return;
    }

    legendElement.style.display = 'block';
    legendElement.innerHTML = actions.map(action => {
        let color = '#007acc';
        if (action.includes('STRAFE.TO')) color = '#007acc';
        else if (action.includes('SPLINE.TO')) color = '#2e7d32';
        return `<div style="display:flex;align-items:center;gap:6px;margin:2px 0;">
            <span style="width:12px;height:12px;background:${color};border-radius:2px;"></span>
            <span style="font-size:11px;">${action}</span>
        </div>`;
    }).join('');
}