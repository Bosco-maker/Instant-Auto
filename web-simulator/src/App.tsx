import { useState, useRef, useEffect } from 'react'
import './App.css'
import { AutoParser } from './parser/AutoParser'
import { FieldRenderer } from './simulator/FieldRenderer'
import { SimulationEngine } from './simulator/SimulationEngine'
import { actionRegistry } from './parser/ActionRegistry'

function App() {
  const [logs, setLogs] = useState<string[]>([])
  const [autoCode, setAutoCode] = useState('')
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const rendererRef = useRef<FieldRenderer | null>(null)
  const engineRef = useRef<SimulationEngine | null>(null)

  useEffect(() => {
    if (canvasRef.current && !rendererRef.current) {
      rendererRef.current = new FieldRenderer(canvasRef.current)
      engineRef.current = new SimulationEngine((state) => {
        rendererRef.current?.draw(state)
      })

      // Register some mock actions for testing
      actionRegistry.register({
        identifier: 'PRINT',
        create: (params) => ({
          id: 'PRINT',
          run: () => {
            console.log('SIM PRINT:', params)
            setLogs(prev => [...prev, `PRINT: ${params}`])
            return true
          }
        })
      })

      rendererRef.current.draw({ x: 0, y: 0, heading: 0 })
    }
  }, [])

  const handleRun = () => {
    const parser = new AutoParser()
    const actions = parser.parse(autoCode)
    setLogs(parser.getLogs())

    if (engineRef.current) {
      engineRef.current.setActions(actions)
      // Basic execution loop
      const runLoop = () => {
        if (engineRef.current?.step()) {
          setTimeout(runLoop, 100)
        }
      }
      runLoop()
    }
  }

  return (
    <div className="app-container">
      <header>
        <h1>Autonomous Simulator</h1>
      </header>
      <main>
        <div className="simulator-layout">
          <section className="controls">
            <h2>Editor</h2>
            <textarea
              value={autoCode}
              onChange={(e) => setAutoCode(e.target.value)}
              placeholder="Enter .auto code here..."
              className="code-editor"
            />
            <button onClick={handleRun}>Run Simulation</button>
          </section>
          <section className="visualizer">
            <canvas ref={canvasRef} id="field-canvas" width="600" height="600"></canvas>
          </section>
          <section className="logs">
            <h2>Logs</h2>
            <div className="log-output">
              {logs.map((log, i) => <div key={i}>{log}</div>)}
            </div>
          </section>
        </div>
      </main>
    </div>
  )
}

export default App
