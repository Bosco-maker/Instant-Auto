/**
 * InstantAuto Core - Simplified for Reduced Feature Set
 * VariableParser, SimpleAutoParser, ActionFactory, Pose2d
 */

// ============================================================================
// MetaFieldRegistry - Stores variable definitions and values
// ============================================================================

export class ConfigEntry {
    constructor(fieldName, type, value) {
        this.fieldName = fieldName;
        this.type = type;
        this._value = value;
    }

    get value() { return this._value; }
    set value(v) { this._value = v; }
}

export class MetaFieldRegistry {
    static entries = new Map();
    static variableTypes = new Map();

    static registerVariable(name, value) {
        const normalizedName = name.toLowerCase();
        const type = this.inferType(value);
        this.entries.set(normalizedName, new ConfigEntry(name, type, value));
        this.variableTypes.set(normalizedName, type);
    }

    static inferType(value) {
        if (value === true || value === false) return 'boolean';
        if (typeof value === 'string') return 'string';
        if (typeof value === 'number') return Number.isInteger(value) ? 'int' : 'double';
        if (value instanceof Pose2d) return 'pose2d';
        return 'unknown';
    }

    static getEntry(name) {
        if (!name) return null;
        return this.entries.get(name.toLowerCase());
    }

    static getVariableType(name) {
        return this.variableTypes.get(name.toLowerCase());
    }

    static getAllVariables() {
        const result = {};
        for (const [name, entry] of this.entries) {
            result[name] = entry.value;
        }
        return result;
    }

    static clear() {
        this.entries.clear();
        this.variableTypes.clear();
    }
}

// ============================================================================
// MetaField Types - Pose2d
// ============================================================================

export class Pose2d {
    constructor(x, y, heading) {
        this.x = Number(x);
        this.y = Number(y);
        this.heading = Number(heading);
    }

    getIdentifier() { return "pose2d"; }

    toString() {
        return `Pose2d(${this.x.toFixed(2)}, ${this.y.toFixed(2)}, ${this.heading.toFixed(2)})`;
    }

    static lerp(p1, p2, t) {
        return new Pose2d(
            p1.x + (p2.x - p1.x) * t,
            p1.y + (p2.y - p1.y) * t,
            p1.heading + (p2.heading - p1.heading) * t
        );
    }
}

// ============================================================================
// VariableParser - Parses ACTIVE Auto text, extracts variables and actions
// ============================================================================

export class VariableParser {
    constructor() {
        this.variables = {};
        this.actionLines = [];
        this.errors = [];
        this.lineNumber = 0;
    }

    parse(text) {
        this.variables = {};
        this.actionLines = [];
        this.errors = [];
        this.lineNumber = 0;

        const lines = text.split('\n');
        let i = 0;

        while (i < lines.length) {
            this.lineNumber = i + 1;
            let line = this.stripComments(lines[i]).trim();

            if (!line) {
                i++;
                continue;
            }

            if (this.isVariableAssignment(line)) {
                this.parseVariableAssignment(line);
                i++;
            }
            else if (line.toLowerCase().startsWith('if')) {
                const result = this.extractIfBlockChain(lines, i);
                this.actionLines.push(...result.blockLines);
                i = result.nextIndex;
            }
            else {
                this.actionLines.push({ line, lineNumber: this.lineNumber });
                i++;
            }
        }

        return {
            variables: this.variables,
            actionLines: this.actionLines,
            errors: this.errors
        };
    }

    stripComments(line) {
        let idx = line.indexOf('//');
        if (idx !== -1) line = line.substring(0, idx);
        idx = line.indexOf('#');
        if (idx !== -1) line = line.substring(0, idx);
        return line;
    }

    isVariableAssignment(line) {
        const eqIndex = line.indexOf('=');
        if (eqIndex === -1) return false;
        let parenLevel = 0;
        for (let i = 0; i < eqIndex; i++) {
            if (line[i] === '(') parenLevel++;
            if (line[i] === ')') parenLevel--;
        }
        return parenLevel === 0;
    }

    parseVariableAssignment(line) {
        const eqIndex = line.indexOf('=');
        const varName = line.substring(0, eqIndex).trim();
        const valueExpr = line.substring(eqIndex + 1).trim();

        if (!varName) {
            this.errors.push(`Line ${this.lineNumber}: Empty variable name`);
            return;
        }

        const value = this.parseValue(valueExpr);
        this.variables[varName] = value;
        MetaFieldRegistry.registerVariable(varName, value);
    }

    parseValue(val) {
        val = val.trim();
        const lowerVal = val.toLowerCase();

        if (lowerVal === 'true') return true;
        if (lowerVal === 'false') return false;
        if (val.startsWith('"') && val.endsWith('"')) return val.slice(1, -1);
        if (val.startsWith("'") && val.endsWith("'")) return val.slice(1, -1);

        if (lowerVal.startsWith('pose2d(') && val.endsWith(')')) {
            const params = val.substring(7, val.length - 1);
            const parts = this.splitParams(params);
            if (parts.length === 3) {
                const x = parseFloat(parts[0]);
                const y = parseFloat(parts[1]);
                const h = parseFloat(parts[2]);
                if (!isNaN(x) && !isNaN(y) && !isNaN(h)) return new Pose2d(x, y, h);
            }
            return null;
        }

        const asInt = parseInt(val);
        if (!isNaN(asInt) && String(asInt) === val) return asInt;
        const asDouble = parseFloat(val);
        if (!isNaN(asDouble)) return asDouble;

        return val;
    }

    splitParams(params) {
        const result = [];
        let current = '', parenLevel = 0, inQuotes = false, quoteChar = '';
        for (const c of params) {
            if ((c === '"' || c === "'") && !inQuotes) { inQuotes = true; quoteChar = c; }
            else if (c === quoteChar && inQuotes) { inQuotes = false; quoteChar = ''; }
            if (c === '(' && !inQuotes) parenLevel++;
            if (c === ')' && !inQuotes) parenLevel--;
            if (c === ',' && !inQuotes && parenLevel === 0) { result.push(current.trim()); current = ''; }
            else { current += c; }
        }
        result.push(current.trim());
        return result;
    }

    extractIfBlockChain(lines, startIndex) {
        const blocks = [];
        let currentI = startIndex;

        while (currentI < lines.length) {
            let line = this.stripComments(lines[currentI]).trim();
            if (!line) { currentI++; continue; }

            const lowerLine = line.toLowerCase();
            const isIf = lowerLine.startsWith('if');
            const isElseIf = lowerLine.startsWith('else if');
            const isElse = lowerLine.startsWith('else') && !isElseIf;

            if (isIf || isElseIf || isElse) {
                let condition = 'else';
                if (isIf || isElseIf) {
                    const startParen = line.indexOf('(');
                    const endParen = this.findMatchingParen(line, startParen);
                    if (startParen === -1 || endParen === -1) {
                        this.errors.push(`Line ${currentI + 1}: Syntax Error. '${isIf ? 'if' : 'else if'}' missing condition in (parentheses)`);
                        condition = 'invalid'; // Prevent this block from ever running
                    } else {
                        condition = line.substring(startParen + 1, endParen).trim();
                    }
                }

                const braceResult = this.extractBracedContent(lines, currentI);
                blocks.push({ condition, actions: braceResult.actions });
                currentI = braceResult.endLineIdx;

                let remainder = braceResult.lineRemainder.trim();
                if (remainder.toLowerCase().startsWith('else')) {
                    lines[currentI] = remainder;
                    continue;
                } else {
                    let nextLineI = currentI + 1;
                    let foundNextPart = false;
                    while (nextLineI < lines.length) {
                        let nextLine = this.stripComments(lines[nextLineI]).trim();
                        if (!nextLine) { nextLineI++; continue; }
                        if (nextLine.toLowerCase().startsWith('else')) {
                            currentI = nextLineI;
                            foundNextPart = true;
                            break;
                        }
                        break;
                    }
                    if (!foundNextPart) {
                        currentI = nextLineI;
                        break;
                    }
                }
            } else {
                break;
            }
        }

        let chosenActions = [];
        for (const block of blocks) {
            if (block.condition === 'else' || this.evaluateCondition(block.condition)) {
                chosenActions = block.actions;
                break;
            }
        }
        return { blockLines: chosenActions, nextIndex: currentI };
    }

    extractBracedContent(lines, startIndex) {
        let currentI = startIndex;
        let braceLevel = 0;
        let foundStart = false;
        let content = "";
        let lineRemainder = "";

        while (currentI < lines.length) {
            let line = this.stripComments(lines[currentI]);
            let searchStart = 0;

            if (!foundStart) {
                const sIdx = line.indexOf('{');
                if (sIdx !== -1) {
                    foundStart = true;
                    braceLevel = 1;
                    searchStart = sIdx + 1;
                } else {
                    currentI++;
                    continue;
                }
            }

            for (let k = searchStart; k < line.length; k++) {
                if (line[k] === '{') braceLevel++;
                else if (line[k] === '}') braceLevel--;

                if (braceLevel === 0) {
                    content += line.substring(searchStart, k);
                    lineRemainder = line.substring(k + 1);
                    const actions = this.linesToActions(content, startIndex);
                    return { actions, endLineIdx: currentI, lineRemainder };
                }
            }

            content += line.substring(searchStart) + "\n";
            currentI++;
        }

        return { actions: this.linesToActions(content, startIndex), endLineIdx: currentI, lineRemainder: "" };
    }

    linesToActions(content, baseLineNumber) {
        const subLines = content.split('\n');
        const actions = [];
        for (let k = 0; k < subLines.length; k++) {
            const sl = subLines[k].trim();
            if (sl) actions.push({ line: sl, lineNumber: baseLineNumber + k + 1 });
        }
        return actions;
    }

    findMatchingParen(line, startIdx) {
        if (startIdx === -1) return -1;
        let level = 0;
        for (let i = startIdx; i < line.length; i++) {
            if (line[i] === '(') level++;
            if (line[i] === ')') level--;
            if (level === 0) return i;
        }
        return -1;
    }

    evaluateCondition(condition) {
        condition = condition.trim().toLowerCase();
        if (condition === 'true') return true;
        if (condition === 'false') return false;
        const entry = MetaFieldRegistry.getEntry(condition);
        if (entry && typeof entry.value === 'boolean') return entry.value;
        return false;
    }

    getErrors() { return this.errors; }
}

// ============================================================================
// ActionFactory - Creates action instances from parsed lines
// ============================================================================

export class ActionFactory {
    static createAction(actionLineObj) {
        const line = actionLineObj.line.trim();
        const lineNumber = actionLineObj.lineNumber;
        if (!line) return null;

        const strafeMatch = line.match(/^STRAFE\.TO\s*\((.*)\)$/i);
        if (strafeMatch) return this.createStrafeAction(strafeMatch[1].trim(), lineNumber);

        const splineMatch = line.match(/^SPLINE\.TO\s*\((.*)\)$/i);
        if (splineMatch) return this.createSplineAction(splineMatch[1].trim(), lineNumber);

        return null;
    }

    static createStrafeAction(params, lineNumber) {
        if (!params) return null;
        if (!params.includes(',') && /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(params.trim())) {
            return new StrafeAction(0, 0, 0, params.trim());
        }
        const parts = this.splitParams(params);
        if (parts.length !== 3) return null;
        const x = parseFloat(parts[0]), y = parseFloat(parts[1]), h = parseFloat(parts[2]);
        if (isNaN(x) || isNaN(y) || isNaN(h)) return null;
        return new StrafeAction(x, y, h);
    }

    static createSplineAction(params, lineNumber) {
        if (!params) return null;
        const parts = this.splitParams(params);

        if (parts.length === 3) {
            const firstParam = parts[0].trim();
            const startH = parseFloat(parts[1]);
            const endH = parseFloat(parts[2]);
            if (!isNaN(startH) && !isNaN(endH)) {
                // Check if first param is a variable
                if (/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(firstParam)) {
                    return new SplineAction(0, 0, 0, startH, endH, firstParam);
                }
            }
        }

        if (parts.length === 5) {
            const x = parseFloat(parts[0]), y = parseFloat(parts[1]), h = parseFloat(parts[2]), sh = parseFloat(parts[3]), eh = parseFloat(parts[4]);
            if (isNaN(x) || isNaN(y) || isNaN(h) || isNaN(sh) || isNaN(eh)) return null;
            return new SplineAction(x, y, h, sh, eh);
        }

        return null;
    }

    static splitParams(params) {
        const result = [];
        let current = '', parenLevel = 0, inQuotes = false, quoteChar = '';
        for (const c of params) {
            if ((c === '"' || c === "'") && !inQuotes) { inQuotes = true; quoteChar = c; }
            else if (c === quoteChar && inQuotes) { inQuotes = false; quoteChar = ''; }
            if (c === '(' && !inQuotes) parenLevel++;
            if (c === ')' && !inQuotes) parenLevel--;
            if (c === ',' && !inQuotes && parenLevel === 0) { result.push(current.trim()); current = ''; }
            else { current += c; }
        }
        result.push(current.trim());
        return result;
    }
}

// ============================================================================
// Action Base Class
// ============================================================================

export class Action {
    constructor(options = {}) {
        Object.assign(this, options);
        this.isComplete = false;
        this.trajectory = [];
        this.startTime = 0;
        this.telemetryMessage = null;
        this.actionName = "Action";
    }
    begin(timestamp) { this.startTime = timestamp; }
    run() { return false; }
    getTrajectory() { return this.trajectory; }
}

export class StrafeAction extends Action {
    constructor(x, y, heading, variableName = null) {
        super();
        this.targetX = x; this.targetY = y; this.targetHeading = heading; this.variableName = variableName;
        this.actionName = variableName ? `STRAFE.TO(${variableName})` : `STRAFE.TO(${x.toFixed(1)}, ${y.toFixed(1)}, ${heading.toFixed(1)})`;
    }
    begin(timestamp) {
        super.begin(timestamp);
        if (this.variableName) {
            const entry = MetaFieldRegistry.getEntry(this.variableName);
            if (entry && entry.value instanceof Pose2d) {
                this.targetX = entry.value.x; this.targetY = entry.value.y; this.targetHeading = entry.value.heading;
                this.actionName = `STRAFE.TO(${this.variableName}) -> (${this.targetX.toFixed(1)}, ${this.targetY.toFixed(1)}, ${this.targetHeading.toFixed(1)})`;
            }
        }
    }
    run() {
        if (this.isComplete) return false;
        if (this.trajectory.length === 0) {
            let currentPose = MetaFieldRegistry.getEntry('robotPose');
            let startX = 0, startY = 0, startH = 0;
            if (currentPose && currentPose.value instanceof Pose2d) { startX = currentPose.value.x; startY = currentPose.value.y; startH = currentPose.value.heading; }
            const duration = 1.0, steps = 20;
            for (let i = 0; i <= steps; i++) {
                const t = i / steps;
                this.trajectory.push({ x: startX + (this.targetX - startX) * t, y: startY + (this.targetY - startY) * t, heading: startH + (this.targetHeading - startH) * t, t: this.startTime + t * duration, actionName: this.actionName });
            }
            MetaFieldRegistry.registerVariable('robotPose', new Pose2d(this.targetX, this.targetY, this.targetHeading));
        }
        this.isComplete = true; return false;
    }
}

export class SplineAction extends Action {
    constructor(x, y, heading, startHeading, endHeading, variableName = null) {
        super();
        this.targetX = x; this.targetY = y; this.targetHeading = heading; this.startPathTangent = startHeading; this.endPathTangent = endHeading;
        this.variableName = variableName;
        this.actionName = variableName ? `SPLINE.TO(${variableName}, ${startHeading.toFixed(1)}, ${endHeading.toFixed(1)})` : `SPLINE.TO(${x.toFixed(1)}, ${y.toFixed(1)}, ${heading.toFixed(1)}, ${startHeading.toFixed(1)}, ${endHeading.toFixed(1)})`;
    }
    begin(timestamp) {
        super.begin(timestamp);
        if (this.variableName) {
            const entry = MetaFieldRegistry.getEntry(this.variableName);
            if (entry && entry.value instanceof Pose2d) {
                this.targetX = entry.value.x; this.targetY = entry.value.y; this.targetHeading = entry.value.heading;
                this.actionName = `SPLINE.TO(${this.variableName}) -> (${this.targetX.toFixed(1)}, ${this.targetY.toFixed(1)}, ${this.targetHeading.toFixed(1)})`;
            }
        }
    }
    static quinticSpline(t, p0, p1, v0, v1) {
        const t2 = t * t, t3 = t2 * t, t4 = t3 * t, t5 = t4 * t;
        let v0x = Math.cos(v0 * Math.PI / 180), v0y = Math.sin(v0 * Math.PI / 180), v1x = Math.cos(v1 * Math.PI / 180), v1y = Math.sin(v1 * Math.PI / 180);
        const dx = p1.x - p0.x, dy = p1.y - p0.y, dist = Math.sqrt(dx * dx + dy * dy), speed = dist > 0 ? dist : 1;
        v0x *= speed; v0y *= speed; v1x *= speed; v1y *= speed;
        const c0x = p0.x, c1x = v0x, c2x = 0, c3x = 10 * dx - 6 * v0x - 4 * v1x, c4x = -15 * dx + 8 * v0x + 7 * v1x, c5x = 6 * dx - 3 * v0x - 3 * v1x;
        const c0y = p0.y, c1y = v0y, c2y = 0, c3y = 10 * dy - 6 * v0y - 4 * v1y, c4y = -15 * dy + 8 * v0y + 7 * v1y, c5y = 6 * dy - 3 * v0y - 3 * v1y;
        return { x: c0x + c1x * t + c2x * t2 + c3x * t3 + c4x * t4 + c5x * t5, y: c0y + c1y * t + c2y * t2 + c3y * t3 + c4y * t4 + c5y * t5 };
    }
    run() {
        if (this.isComplete) return false;
        if (this.trajectory.length === 0) {
            let currentPose = MetaFieldRegistry.getEntry('robotPose');
            let startX = 0, startY = 0, startH = 0; if (currentPose && currentPose.value instanceof Pose2d) { startX = currentPose.value.x; startY = currentPose.value.y; startH = currentPose.value.heading; }
            const duration = 1.5, steps = 30;
            for (let i = 0; i <= steps; i++) {
                const t = i / steps;
                const point = SplineAction.quinticSpline(t, { x: startX, y: startY }, { x: this.targetX, y: this.targetY }, this.startPathTangent, this.endPathTangent);
                const h = startH + (this.targetHeading - startH) * t;
                this.trajectory.push({ x: point.x, y: point.y, heading: h, t: this.startTime + t * duration, actionName: this.actionName });
            }
            MetaFieldRegistry.registerVariable('robotPose', new Pose2d(this.targetX, this.targetY, this.targetHeading));
        }
        this.isComplete = true; return false;
    }
}