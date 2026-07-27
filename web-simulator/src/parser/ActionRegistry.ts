import { configRegistry } from './ConfigRegistry';

export interface Action {
    id: string;
    run: () => boolean;
}

export interface MetaAction {
    identifier: string;
    create: (params: string) => Action;
}

class ActionRegistry {
    private registry: Map<string, MetaAction> = new Map();
    private loadErrors: string[] = [];

    register(action: MetaAction) {
        this.registry.set(action.identifier.toUpperCase(), action);
    }

    createAction(line: string): Action | null {
        line = line.trim();
        if (!line || line.startsWith('//') || line.startsWith('#')) return null;

        // 1. Variable Assignment
        const eqIndex = line.indexOf('=');
        if (eqIndex !== -1) {
            const varName = line.substring(0, eqIndex).trim();
            const valueExpr = line.substring(eqIndex + 1).trim();
            return {
                id: 'ASSIGNMENT',
                run: () => {
                    const val = this.parseValue(valueExpr);
                    configRegistry.updateValue(varName, val);
                    return true;
                }
            };
        }

        // 2. Action Parsing
        const firstParen = line.indexOf('(');
        const lastParen = line.lastIndexOf(')');

        if (firstParen !== -1 && lastParen > firstParen) {
            const name = line.substring(0, firstParen).trim();
            const params = line.substring(firstParen + 1, lastParen).trim();
            const meta = this.registry.get(name.toUpperCase());
            if (meta) return meta.create(params);
        } else {
            const meta = this.registry.get(line.toUpperCase());
            if (meta) return meta.create('');
        }

        return null;
    }

    private parseValue(val: string): any {
        val = val.trim();
        if (val.toLowerCase() === 'true') return true;
        if (val.toLowerCase() === 'false') return false;
        if (val.startsWith('"') && val.endsWith('"')) return val.substring(1, val.length - 1);

        const num = Number(val);
        if (!isNaN(num)) return num;

        return val;
    }

    splitByTopLevelCommas(content: string): string[] {
        const result: string[] = [];
        let current = '';
        let parenLevel = 0;
        let braceLevel = 0;
        let inQuotes = false;

        for (let i = 0; i < content.length; i++) {
            const c = content[i];
            if (c === '"') inQuotes = !inQuotes;
            if (!inQuotes) {
                if (c === '(') parenLevel++;
                if (c === ')') parenLevel--;
                if (c === '{') braceLevel++;
                if (c === '}') braceLevel--;
            }

            if ((c === ',' || c === '\n') && parenLevel === 0 && braceLevel === 0 && !inQuotes) {
                const s = current.trim();
                if (s) result.push(s);
                current = '';
            } else {
                current += c;
            }
        }
        if (current.trim()) result.push(current.trim());
        return result;
    }
}

export const actionRegistry = new ActionRegistry();
