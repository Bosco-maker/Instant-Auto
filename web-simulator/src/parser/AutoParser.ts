import { actionRegistry, Action } from './ActionRegistry';

export class AutoParser {
    private logs: string[] = [];

    parse(content: string): Action[] {
        const actions: Action[] = [];
        const lines = content.split('\n');

        for (let i = 0; i < lines.length; i++) {
            let line = lines[i].trim();
            if (!line || line.startsWith('//') || line.startsWith('#')) continue;

            const action = actionRegistry.createAction(line);
            if (action) {
                actions.push(action);
            } else {
                this.logs.push(`Line ${i + 1}: Unknown action or syntax error: ${line}`);
            }
        }
        return actions;
    }

    getLogs(): string[] {
        return this.logs;
    }
}
