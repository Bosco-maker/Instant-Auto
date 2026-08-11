import { RobotState } from './RobotState';

export class FieldRenderer {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private fieldSize = 144; // inches (FTC field)

    constructor(canvas: HTMLCanvasElement) {
        this.canvas = canvas;
        const context = canvas.getContext('2d');
        if (!context) throw new Error('Could not get canvas context');
        this.ctx = context;
    }

    draw(state: RobotState) {
        const { width, height } = this.canvas;
        const scale = width / this.fieldSize;

        // Clear
        this.ctx.fillStyle = '#333';
        this.ctx.fillRect(0, 0, width, height);

        // Draw grid
        this.ctx.strokeStyle = '#444';
        this.ctx.beginPath();
        for (let i = 0; i <= 6; i++) {
            const pos = (i * 24) * scale;
            this.ctx.moveTo(pos, 0);
            this.ctx.lineTo(pos, height);
            this.ctx.moveTo(0, pos);
            this.ctx.lineTo(width, pos);
        }
        this.ctx.stroke();

        // Draw Robot
        this.ctx.save();
        this.ctx.translate(state.x * scale + width / 2, -state.y * scale + height / 2);
        this.ctx.rotate(-state.heading * (Math.PI / 180));

        this.ctx.fillStyle = '#007aff';
        this.ctx.fillRect(-15, -15, 30, 30);

        // Direction indicator
        this.ctx.fillStyle = '#fff';
        this.ctx.fillRect(10, -2, 10, 4);

        this.ctx.restore();
    }
}
