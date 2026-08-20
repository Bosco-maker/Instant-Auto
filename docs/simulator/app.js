/**
 * Main Application - InstantAuto Simulator (Simplified)
 * Single ACTIVE Auto textarea, smooth robot animation, guide tab
 */

import { SimulatorEngine } from './sim-engine.js';
import { FieldRenderer, updateLegend } from './field-renderer.js';
import { Pose2d } from './core.js';

// State
let renderer = null;
let engine = null;
let animationId = null;
let animationSpeed = 1;
let isAnimating = false;
let lastFrameTime = 0;
let hasUserChanges = false;

// DOM Elements (will be initialized in init)
let elements = {};

function init() {
    console.log("Initializing InstantAuto Simulator...");
    try {
        elements = {
            canvas: document.getElementById('fieldCanvas'),
            activeAutoTA: document.getElementById('activeAuto'),
            runBtn: document.getElementById('runBtn'),
            resetBtn: document.getElementById('resetBtn'),
            speedRange: document.getElementById('speedRange'),
            speedLabel: document.getElementById('speedLabel'),
            statusEl: document.getElementById('status'),
            telemetryEl: document.getElementById('telemetry'),
            legendEl: document.getElementById('legend'),
            guidePanel: document.getElementById('guidePanel')
        };

        if (elements.canvas) {
            renderer = new FieldRenderer(elements.canvas);
            renderer.renderStatic({ pose: new Pose2d(0, 0, 0), trajectory: [] });
        }

        if (elements.activeAutoTA) {
            elements.activeAutoTA.value = DEFAULT_ACTIVE_AUTO;
            elements.activeAutoTA.addEventListener('input', () => {
                hasUserChanges = true;
            });
        }

        log('info', 'InstantAuto Simulator ready');
        updateStatus('Ready — Edit config or press Run', 'idle');

        if (elements.guidePanel) {
            elements.guidePanel.classList.remove('open');
        }
    } catch (e) {
        console.error("Initialization error:", e);
    }
}

// Default ACTIVE Auto configuration
const DEFAULT_ACTIVE_AUTO = `Starting = pose2d(-60, -60, 0)
scorePose = pose2d(48, 48, 90)
isBlue = true

STRAFE.TO(0, -20, 0)
SPLINE.TO(scorePose, 0, 90)

if (isBlue) {
    STRAFE.TO(scorePose)
} else {
    STRAFE.TO(-48, 48, 90)
}

STRAFE.TO(0, 0, 0)`;

function toggleGuide() {
    if (elements.guidePanel) {
        elements.guidePanel.classList.toggle('open');
    } else {
        const panel = document.getElementById('guidePanel');
        if (panel) panel.classList.toggle('open');
    }
}

function updateSpeed(value) {
    animationSpeed = parseFloat(value);
    if (elements.speedLabel) {
        elements.speedLabel.textContent = animationSpeed.toFixed(1) + 'x';
    }
}

function updateStatus(message, type = 'idle') {
    if (elements.statusEl) {
        elements.statusEl.textContent = message;
        elements.statusEl.className = 'status ' + type;
    }
}

function log(level, message) {
    if (elements.telemetryEl) {
        const line = document.createElement('div');
        line.className = `telemetry-line ${level}`;
        const time = new Date().toLocaleTimeString();
        line.textContent = `[${time}] ${message}`;
        elements.telemetryEl.appendChild(line);
        elements.telemetryEl.scrollTop = elements.telemetryEl.scrollHeight;
    }
}

function clearTelemetry() {
    if (elements.telemetryEl) {
        elements.telemetryEl.innerHTML = '';
    }
}

async function runSimulation() {
    if (isAnimating) return;

    const active = elements.activeAutoTA ? elements.activeAutoTA.value : (document.getElementById('activeAuto')?.value || "");

    disableControls(true);
    clearTelemetry();
    updateStatus('Initializing...', 'running');

    try {
        engine = new SimulatorEngine(active);
        const result = engine.run();

        if (result.errors.length > 0) {
            for (const err of result.errors) {
                log('error', err);
            }
            updateStatus('Simulation failed to initialize', 'error');
            disableControls(false);
            return;
        }

        isAnimating = true;
        lastFrameTime = performance.now();
        if (renderer) renderer.startAnimation(result.fullTrajectory);
        updateStatus('Running simulation...', 'running');
        animateSmooth();

    } catch (e) {
        log('error', `Fatal error: ${e.message}`);
        console.error(e);
        updateStatus('Error running simulation', 'error');
        disableControls(false);
    }
}

function animateSmooth() {
    if (!isAnimating || !renderer || !renderer.isAnimating) {
        finishAnimation();
        return;
    }

    const now = performance.now();
    const deltaTime = (now - lastFrameTime) / 1000 * animationSpeed;
    lastFrameTime = now;

    const continuing = renderer.updateAnimationTime(deltaTime);
    renderer.renderAnimated({ trajectory: renderer.currentTrajectory });
    updateLegend(elements.legendEl, renderer.currentTrajectory);

    if (continuing) {
        animationId = requestAnimationFrame(animateSmooth);
    } else {
        finishAnimation();
    }
}

function resetSimulation() {
    stopAnimation();
    if (engine) {
        engine.reset();
        engine = null;
    }
    if (renderer) {
        renderer.renderStatic({ pose: new Pose2d(0, 0, 0), trajectory: [] });
    }
    updateLegend(elements.legendEl, []);
    clearTelemetry();
    updateStatus('Ready — Edit config or press Run', 'idle');
    disableControls(false);
}

function disableControls(disabled) {
    if (elements.runBtn) elements.runBtn.disabled = disabled;
    if (elements.activeAutoTA) elements.activeAutoTA.disabled = disabled;
}

function stopAnimation() {
    isAnimating = false;
    if (renderer) renderer.stopAnimation();
    if (animationId) {
        cancelAnimationFrame(animationId);
        animationId = null;
    }
}

function finishAnimation() {
    isAnimating = false;
    if (renderer) renderer.stopAnimation();
    updateStatus('Simulation complete', 'running');
    log('info', 'Simulation complete');
    disableControls(false);
}

document.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'TEXTAREA') return;
    switch (e.key) {
        case ' ':
        case 'Enter':
            e.preventDefault();
            if (!isAnimating && (!engine || !engine.isComplete)) runSimulation();
            break;
        case 'r':
        case 'R':
            if (e.ctrlKey || e.metaKey) return;
            e.preventDefault();
            resetSimulation();
            break;
    }
});

window.addEventListener('beforeunload', (e) => {
    if (hasUserChanges) {
        e.preventDefault();
        e.returnValue = '';
    }
});

// Expose functions globally for inline onclick handlers
window.runSimulation = runSimulation;
window.resetSimulation = resetSimulation;
window.updateSpeed = updateSpeed;
window.clearTelemetry = clearTelemetry;
window.toggleGuide = toggleGuide;

// Initialize on load
document.addEventListener('DOMContentLoaded', init);