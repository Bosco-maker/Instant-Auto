/**
 * SimulatorEngine - Simplified for Reduced Feature Set
 * Single-pass parse → execute using VariableParser and ActionFactory
 */

import { VariableParser, MetaFieldRegistry, Pose2d, ActionFactory } from './core.js';
import { StrafeAction, SplineAction } from './sim-actions.js';

export class TelemetryCollector {
    constructor() { this.logs = []; }
    log(level, message) { this.logs.push({ level, message, timestamp: Date.now() }); }
    info(msg) { this.log('info', msg); }
    warn(msg) { this.log('warn', msg); }
    error(msg) { this.log('error', msg); }
    action(msg) { this.log('action', msg); }
    pose(msg) { this.log('pose', msg); }
    print(msg) { this.log('info', msg); }
    getAllLogs() { return this.logs; }
    clear() { this.logs = []; }
}

export class SimulatorState {
    constructor() {
        this.timestamp = 0;
        this.pose = new Pose2d(0, 0, 0);
        this.trajectory = [];
        this.currentAction = null;
        this.variables = {};
        this.logs = [];
    }
    static fromEngine(engine) {
        const state = new SimulatorState();
        state.timestamp = engine.currentTime;
        const poseEntry = MetaFieldRegistry.getEntry('robotPose');
        state.pose = (poseEntry && poseEntry.value instanceof Pose2d) ? poseEntry.value : new Pose2d(0, 0, 0);
        state.trajectory = [...engine.fullTrajectory];
        state.currentAction = engine.currentActionName;
        state.variables = MetaFieldRegistry.getAllVariables();
        state.logs = engine.telemetry.getAllLogs().map(l => ({...l}));
        return state;
    }
}

export class SimulatorEngine {
    constructor(activeAutoText) {
        this.activeAutoText = activeAutoText;
        this.variableParser = new VariableParser();
        this.actions = [];
        this.telemetry = new TelemetryCollector();
        this.history = [];
        this.fullTrajectory = [];
        this.currentTime = 0;
        this.currentActionIndex = 0;
        this.currentActionName = null;
        this.isRunning = false;
        this.isComplete = false;
        this.errors = [];
        this.isInitialized = false;
    }

    initialize() {
        this.isInitialized = false;
        this.errors = [];
        this.actions = [];
        this.history = [];
        this.fullTrajectory = [];
        this.currentTime = 0;
        this.currentActionIndex = 0;
        this.currentActionName = null;

        MetaFieldRegistry.clear();
        const parseResult = this.variableParser.parse(this.activeAutoText);
        for (const err of parseResult.errors) { this.telemetry.error(err); this.errors.push(err); }

        const startingEntry = MetaFieldRegistry.getEntry('Starting');
        if (!startingEntry || !startingEntry.value || !(startingEntry.value instanceof Pose2d)) {
            const err = "CRITICAL ERROR: Required 'Starting' field is missing or not a valid pose2d";
            this.telemetry.error(err); this.errors.push(err); return false;
        }

        MetaFieldRegistry.registerVariable('robotPose', startingEntry.value);

        for (const actionLineObj of parseResult.actionLines) {
            const action = ActionFactory.createAction(actionLineObj);
            if (action) {
                this.actions.push(action);
            } else {
                const err = `Action line ${actionLineObj.lineNumber}: Unknown Action -> ${actionLineObj.line}`;
                this.telemetry.error(err); this.errors.push(err);
            }
        }

        this.isInitialized = this.errors.length === 0;
        return this.isInitialized;
    }

    run() {
        if (!this.isInitialized && !this.initialize()) return this.getResult();
        this.isRunning = true;
        this.history = [];
        this.history.push(this.serializeState(SimulatorState.fromEngine(this)));
        while (this.currentActionIndex < this.actions.length) { this.step(); }
        this.isRunning = false;
        this.isComplete = true;
        return this.getResult();
    }

    step() {
        if (!this.isInitialized && !this.initialize()) return null;
        if (this.isComplete) return null;
        if (this.currentActionIndex >= this.actions.length) {
            this.isComplete = true; this.currentActionName = null; return null;
        }

        const action = this.actions[this.currentActionIndex];
        this.currentActionName = action.actionName || `Action ${this.currentActionIndex + 1}`;
        if (action.begin) action.begin(this.currentTime);
        let actionSteps = 0;
        while (action.run() && actionSteps < 1000) { actionSteps++; }
        if (action.getTrajectory) {
            const traj = action.getTrajectory();
            this.fullTrajectory.push(...traj);
            this.currentTime = traj.length > 0 ? traj[traj.length - 1].t : this.currentTime + 1;
        }
        const state = SimulatorState.fromEngine(this);
        const serialized = this.serializeState(state);
        this.history.push(serialized);
        this.currentActionIndex++;
        return serialized;
    }

    reset() {
        this.isInitialized = false; this.actions = []; this.telemetry.clear(); this.history = [];
        this.fullTrajectory = []; this.currentTime = 0; this.currentActionIndex = 0;
        this.currentActionName = null; this.isRunning = false; this.isComplete = false; this.errors = [];
    }

    getResult() {
        return {
            success: this.errors.length === 0,
            errors: [...this.errors],
            history: this.history,
            fullTrajectory: this.fullTrajectory,
            telemetry: this.telemetry.getAllLogs(),
            finalPose: this.history.length > 0 ? this.history[this.history.length - 1].pose : new Pose2d(0, 0, 0)
        };
    }

    serializeState(state) {
        return {
            timestamp: state.timestamp,
            pose: { x: state.pose.x, y: state.pose.y, heading: state.pose.heading },
            trajectory: state.trajectory.map(p => ({ x: p.x, y: p.y, heading: p.heading, t: p.t, actionName: p.actionName })),
            currentAction: state.currentAction,
            variables: {...state.variables},
            logs: state.logs
        };
    }
}

export async function runSimulation(activeAuto) {
    const engine = new SimulatorEngine(activeAuto);
    return engine.run();
}