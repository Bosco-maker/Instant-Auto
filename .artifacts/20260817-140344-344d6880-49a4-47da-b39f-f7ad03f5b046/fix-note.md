The issue was that `app.js` is loaded as an ES module (`type="module"`), which has its own scope. The inline `onclick` handlers in `index.html` can only access globally-scoped functions.

**Fix applied:** Added explicit `window.` assignments at the end of `app.js`:

```javascript
window.runSimulation = runSimulation;
window.stepSimulation = stepSimulation;
window.resetSimulation = resetSimulation;
window.updateSpeed = updateSpeed;
window.clearTelemetry = clearTelemetry;
```

This makes the functions accessible to the inline `onclick="runSimulation()"` handlers in the HTML.

The simulator should now work when served via a local HTTP server (required for ES modules to work properly).