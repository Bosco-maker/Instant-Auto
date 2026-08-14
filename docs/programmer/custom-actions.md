# Custom Actions

### Concept
Custom actions use `ActionUtils` to safely parse parameters. This utility handles both literal numbers (e.g., 10.5) and variable names (e.g., `targetHeight`) defined in the `MetaFieldRegistry`.

### Example
**File**: `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/action/ActionManager.java`

```java
UserActionRegistry.register(new MiniAction("SET_ARM", (params) -> {
    // Use asDoubles to automatically resolve variables or parse strings
    double[] vals = ActionUtils.asDoubles(params, 1);
    if (vals == null) return null; // Error handling
    
    double targetPos = vals[0];
    return () -> {
        arm.setTarget(targetPos);
        return arm.isBusy(); // Run until arm reaches target
    };
}));
```

### Expected Outcome
The `SET_ARM` command can be called as `SET_ARM(500)` or `SET_ARM(highJunctionPos)`, providing ultimate flexibility in script design.
