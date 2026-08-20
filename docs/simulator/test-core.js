/**
 * Smoke test for InstantAuto core in Node.js (no browser)
 * Verifies parser + execution logic works for reduced feature set
 */

import { VariableParser, MetaFieldRegistry, Pose2d, ActionFactory, StrafeAction, SplineAction } from './core.js';
import { SimulatorEngine } from './sim-engine.js';

let passed = 0;
let failed = 0;
const failures = [];

function assert(condition, message) {
    if (condition) {
        passed++;
        console.log(`  PASS: ${message}`);
    } else {
        failed++;
        failures.push(message);
        console.error(`  FAIL: ${message}`);
    }
}

function runTest(name, fn) {
    console.log(`\n[TEST] ${name}`);
    try {
        fn();
    } catch (e) {
        failed++;
        failures.push(`${name}: ${e.message}`);
        console.error(`  ERROR: ${e.message}`);
    }
}

// ============================================================
// Test 1: Variable Type Inference
// ============================================================
runTest('Variable Parser - Type Inference', () => {
    MetaFieldRegistry.clear();
    const parser = new VariableParser();

    const text = `myDouble = 3.14
myInt = 42
myString = "hello world"
myBool = true
myPose = pose2d(10, 20, 45)
Starting = pose2d(0, 0, 0)`;

    const result = parser.parse(text);

    assert(result.variables.myDouble === 3.14, 'Double parsed correctly');
    assert(MetaFieldRegistry.getVariableType('myDouble') === 'double', 'Double type tracked');

    assert(result.variables.myInt === 42, 'Int parsed correctly');
    assert(MetaFieldRegistry.getVariableType('myInt') === 'int', 'Int type tracked');

    assert(result.variables.myString === 'hello world', 'String parsed correctly');
    assert(MetaFieldRegistry.getVariableType('myString') === 'string', 'String type tracked');

    assert(result.variables.myBool === true, 'Boolean parsed correctly');
    assert(MetaFieldRegistry.getVariableType('myBool') === 'boolean', 'Boolean type tracked');

    assert(result.variables.myPose instanceof Pose2d, 'Pose2d parsed correctly');
    assert(result.variables.myPose.x === 10 && result.variables.myPose.y === 20 && result.variables.myPose.heading === 45, 'Pose2d values correct');
    assert(MetaFieldRegistry.getVariableType('myPose') === 'pose2d', 'Pose2d type tracked');

    assert(result.variables.Starting instanceof Pose2d, 'Starting pose parsed');
});

// ============================================================
// Test 2: STRAFE.TO Action Creation
// ============================================================
runTest('ActionFactory - STRAFE.TO', () => {
    MetaFieldRegistry.clear();

    // With literal coordinates
    const action1 = ActionFactory.createAction({ line: 'STRAFE.TO(24, 24, 90)', lineNumber: 1 });
    assert(action1 instanceof StrafeAction, 'STRAFE.TO with literals creates StrafeAction');
    assert(action1.targetX === 24 && action1.targetY === 24 && action1.targetHeading === 90, 'STRAFE.TO parameters correct');

    // With variable reference
    MetaFieldRegistry.registerVariable('scorePose', new Pose2d(48, 48, 0));
    const action2 = ActionFactory.createAction({ line: 'STRAFE.TO(scorePose)', lineNumber: 2 });
    assert(action2 instanceof StrafeAction, 'STRAFE.TO with variable creates StrafeAction');
    assert(action2.variableName === 'scorePose', 'Variable name stored');
});

// ============================================================
// Test 3: SPLINE.TO Action Creation
// ============================================================
runTest('ActionFactory - SPLINE.TO', () => {
    MetaFieldRegistry.clear();

    const action = ActionFactory.createAction({ line: 'SPLINE.TO(48, 48, 90, 0, 90)', lineNumber: 1 });
    assert(action instanceof SplineAction, 'SPLINE.TO creates SplineAction');
    assert(action.targetX === 48 && action.targetY === 48, 'Target position correct');
    assert(action.targetHeading === 90, 'Target heading correct');
    assert(action.startPathTangent === 0, 'Start tangent correct');
    assert(action.endPathTangent === 90, 'End tangent correct');
});

// ============================================================
// Test 4: SPLINE.TO Trajectory Generation
// ============================================================
runTest('SplineAction - Trajectory Generation', () => {
    MetaFieldRegistry.clear();
    MetaFieldRegistry.registerVariable('robotPose', new Pose2d(0, 0, 0));

    const action = new SplineAction(48, 48, 90, 0, 90);
    action.begin(0);
    action.run();

    const traj = action.getTrajectory();
    assert(traj.length > 20, `Spline generates smooth trajectory: ${traj.length} points`);

    // Check start point
    assert(Math.abs(traj[0].x - 0) < 0.1 && Math.abs(traj[0].y - 0) < 0.1, 'Spline starts at robot pose');

    // Check end point
    const last = traj[traj.length - 1];
    assert(Math.abs(last.x - 48) < 0.5 && Math.abs(last.y - 48) < 0.5, 'Spline ends near target');

    // Check heading progression (interpolated from 0 to 90)
    assert(Math.abs(traj[0].heading - 0) < 0.1, 'Start heading is 0');
    assert(Math.abs(last.heading - 90) < 0.1, 'End heading is 90');
});

// ============================================================
// Test 5: Full Simulation - Basic STRAFE.TO
// ============================================================
runTest('Simulator Engine - Basic STRAFE.TO', () => {
    const active = `Starting = pose2d(0, 0, 0)
STRAFE.TO(24, 24, 90)`;

    const engine = new SimulatorEngine(active);
    const result = engine.run();

    assert(result.success, 'Simulation succeeded');
    assert(result.history.length >= 2, `History has ${result.history.length} states`);

    const finalPose = result.history[result.history.length - 1].pose;
    assert(Math.abs(finalPose.x - 24) < 0.1 && Math.abs(finalPose.y - 24) < 0.1, `Final pose correct: (${finalPose.x}, ${finalPose.y})`);
    assert(Math.abs(finalPose.heading - 90) < 0.1, `Final heading correct: ${finalPose.heading}`);
});

// ============================================================
// Test 6: Variable Reference in STRAFE.TO
// ============================================================
runTest('Simulator Engine - STRAFE.TO with Variable', () => {
    const active = `Starting = pose2d(0, 0, 0)
scorePose = pose2d(48, 48, 0)
STRAFE.TO(scorePose)`;

    const engine = new SimulatorEngine(active);
    const result = engine.run();

    assert(result.success, 'Simulation with variable reference succeeded');
    const finalPose = result.history[result.history.length - 1].pose;
    assert(Math.abs(finalPose.x - 48) < 0.1 && Math.abs(finalPose.y - 48) < 0.1, `Final pose from variable: (${finalPose.x}, ${finalPose.y})`);
});

// ============================================================
// Test 7: Conditional Branching - If/Else
// ============================================================
runTest('Simulator Engine - If/Else Branching', () => {
    // True branch
    const activeTrue = `Starting = pose2d(0, 0, 0)
isBlue = true
if (isBlue) {
    STRAFE.TO(24, 0, 0)
} else {
    STRAFE.TO(-24, 0, 0)
}`;

    const engine1 = new SimulatorEngine(activeTrue);
    const result1 = engine1.run();
    assert(result1.success, 'If/true branch simulation succeeded');
    assert(result1.history.length > 1, 'History has entries for true branch');
    const finalPose1 = result1.history[result1.history.length - 1].pose;
    assert(Math.abs(finalPose1.x - 24) < 0.1, `True branch taken: x=${finalPose1.x}`);

    // False branch
    const activeFalse = `Starting = pose2d(0, 0, 0)
isBlue = false
if (isBlue) {
    STRAFE.TO(24, 0, 0)
} else {
    STRAFE.TO(-24, 0, 0)
}`;

    const engine2 = new SimulatorEngine(activeFalse);
    const result2 = engine2.run();
    assert(result2.success, 'If/false branch simulation succeeded');
    assert(result2.history.length > 1, 'History has entries for false branch');
    const finalPose2 = result2.history[result2.history.length - 1].pose;
    assert(Math.abs(finalPose2.x - (-24)) < 0.1, `False branch taken: x=${finalPose2.x}`);

    // Literal true condition
    const activeLiteral = `Starting = pose2d(0, 0, 0)
if (true) {
    STRAFE.TO(10, 10, 0)
} else {
    STRAFE.TO(-10, -10, 0)
}`;

    const engine3 = new SimulatorEngine(activeLiteral);
    const result3 = engine3.run();
    assert(result3.success, 'Literal true condition succeeded');
    const finalPose3 = result3.history[result3.history.length - 1].pose;
    assert(Math.abs(finalPose3.x - 10) < 0.1, `Literal true branch taken: x=${finalPose3.x}`);
});

// ============================================================
// Test 8: Else-If Chain
// ============================================================
runTest('Simulator Engine - Else-If Chain', () => {
    const active = `Starting = pose2d(0, 0, 0)
isTarget = false
isAlternative = true
if (isTarget) {
    STRAFE.TO(10, 0, 0)
} else if (isAlternative) {
    STRAFE.TO(0, 10, 0)
} else {
    STRAFE.TO(-10, 0, 0)
}`;

    const engine = new SimulatorEngine(active);
    const result = engine.run();
    assert(result.success, 'Else-if chain simulation succeeded');
    const finalPose = result.history[result.history.length - 1].pose;
    assert(Math.abs(finalPose.y - 10) < 0.1, `Alternative branch taken: y=${finalPose.y}`);
});

// ============================================================
// Test 9: Error - Missing Starting Field
// ============================================================
runTest('Error - Missing Starting Field', () => {
    const active = `maxPower = 1.0
STRAFE.TO(0, 0, 0)`;

    const engine = new SimulatorEngine(active);
    const result = engine.run();

    assert(!result.success, 'Simulation failed due to missing Starting');
    assert(result.errors.some(e => e.includes("'Starting' field")), 'Error mentions Starting field');
});

// ============================================================
// Test 10: Error - Invalid Starting (not pose2d)
// ============================================================
runTest('Error - Invalid Starting Type', () => {
    const active = `Starting = 42
STRAFE.TO(0, 0, 0)`;

    const engine = new SimulatorEngine(active);
    const result = engine.run();

    assert(!result.success, 'Simulation failed due to invalid Starting type');
    assert(result.errors.some(e => e.includes("'Starting' field")), 'Error mentions Starting field');
});

// ============================================================
// Test 11: Error - Unknown Action
// ============================================================
runTest('Error - Unknown Action', () => {
    const active = `Starting = pose2d(0, 0, 0)
INVALID_ACTION()`;

    const engine = new SimulatorEngine(active);
    const result = engine.run();

    assert(!result.success, 'Simulation failed due to unknown action');
    assert(result.errors.some(e => e.includes('Unknown Action')), 'Error mentions Unknown Action');
});

// ============================================================
// Test 12: Multiple Actions Sequence
// ============================================================
runTest('Simulator Engine - Multiple Actions', () => {
    const active = `Starting = pose2d(0, 0, 0)
STRAFE.TO(24, 0, 0)
STRAFE.TO(24, 24, 90)
STRAFE.TO(0, 24, 180)`;

    const engine = new SimulatorEngine(active);
    const result = engine.run();

    assert(result.success, 'Multi-action simulation succeeded');
    assert(result.history.length === 4, `History has ${result.history.length} states (initial + 3 actions)`);

    const finalPose = result.history[result.history.length - 1].pose;
    assert(Math.abs(finalPose.x - 0) < 0.1 && Math.abs(finalPose.y - 24) < 0.1, `Final pose: (${finalPose.x}, ${finalPose.y})`);
});

// ============================================================
// Test 13: Mixed STRAFE.TO and SPLINE.TO
// ============================================================
runTest('Simulator Engine - Mixed STRAFE.TO and SPLINE.TO', () => {
    const active = `Starting = pose2d(0, 0, 0)
STRAFE.TO(24, 0, 0)
SPLINE.TO(24, 24, 90, 0, 90)`;

    const engine = new SimulatorEngine(active);
    const result = engine.run();

    assert(result.success, 'Mixed actions simulation succeeded');
    assert(result.history.length === 3, `History has ${result.history.length} states`);

    // Check trajectory has both segments
    const actionNames = [...new Set(result.fullTrajectory.map(p => p.actionName))];
    assert(actionNames.some(n => n.includes('STRAFE.TO')), 'Has STRAFE.TO segment');
    assert(actionNames.some(n => n.includes('SPLINE.TO')), 'Has SPLINE.TO segment');
});

// ============================================================
// Test 14: Step Mode
// ============================================================
runTest('Simulator Engine - Step Mode', () => {
    const active = `Starting = pose2d(0, 0, 0)
STRAFE.TO(24, 0, 0)
STRAFE.TO(24, 24, 90)`;

    const engine = new SimulatorEngine(active);
    assert(engine.initialize(), 'Engine initializes');

    // Step 1
    const state1 = engine.step();
    assert(state1 !== null, 'Step 1 returns state');
    assert(state1.currentAction !== null, 'Step 1 has currentAction');
    assert(state1.currentAction.includes('STRAFE.TO'), 'Step 1 is a STRAFE.TO action');

    // Step 2
    const state2 = engine.step();
    assert(state2 !== null, 'Step 2 returns state');
    assert(state2.currentAction.includes('STRAFE.TO'), 'Step 2 is a STRAFE.TO action');

    // Step 3 (complete)
    const state3 = engine.step();
    assert(state3 === null || engine.isComplete, 'Step 3 completes simulation');
});

// ============================================================
// Summary
// ============================================================
console.log(`\n${'='.repeat(60)}`);
console.log(`TEST SUMMARY: ${passed} passed, ${failed} failed`);
console.log(`${'='.repeat(60)}`);

if (failed > 0) {
    console.error('FAILED TESTS:');
    failures.forEach(f => console.error(`  - ${f}`));
    process.exit(1);
} else {
    console.log('ALL TESTS PASSED');
    process.exit(0);
}