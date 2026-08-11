export interface RobotState {
    x: number;
    y: number;
    heading: number;
}

export const INITIAL_STATE: RobotState = {
    x: 0,
    y: 0,
    heading: 0
};
