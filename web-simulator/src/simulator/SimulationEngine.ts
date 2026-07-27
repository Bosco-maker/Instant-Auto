import { RobotState, INITIAL_STATE } from './RobotState';
import { Action } from '../parser/ActionRegistry';

export class SimulationEngine {
    private state: RobotState = { ...INITIAL_STATE };
    private actions: Action[] = [];
    private currentIndex = 0;
    private onUpdate: (state: RobotState) => void;

    constructor(onUpdate: (state: RobotState) => void) {
        this.onUpdate = onUpdate;
    }

    setActions(actions: Action[]) {
        this.actions = actions;
        this.currentIndex = 0;
        this.state = { ...INITIAL_STATE };
    }

    step() {
        if (this.currentIndex >= this.actions.length) return false;

        const action = this.actions[this.currentIndex];
        const done = action.run(); // In a real sim, this would take deltaTime

        // For this simple version, we assume actions finish in one step
        // In Phase 2, we will add time-based movement
        if (done) {
            this.currentIndex++;
        }

        this.onUpdate(this.state);
        return this.currentIndex < this.actions.length;
    }

    getState() {
        return this.state;
    }
}
