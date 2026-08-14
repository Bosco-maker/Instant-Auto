# Variables

Variables in Instant Auto are managed through a centralized system that allows seamless interaction between your Java code and your sequence files.
Note: Variable names are NOT case-sensitive

## Types of Variables

### Local Variables
Local variables are defined directly in your sequence (`.auto`) or configuration (`.txt`) files using the `key = value` syntax.

```text
myNumber = 10
myString = "hello"
isReady = true
```

If a variable is not pre-registered in Java, the parser automatically treats it as a local variable and infers its type (Integer, Double, Boolean, or String).

### Registered Fields (Global Variables)
These are variables registered in your Java code, typically during initialization. They are useful for settings you want to tweak without recompiling.

```java
MetaFieldRegistry.registerField("maxSpeed", Double.class, 0.8);
```

In your sequence file, you can reference `maxSpeed` or update it at any time.

### Sensor Fields (Dynamic Variables)
Sensor fields allow your auto to react to real-time data. They are registered using a `Supplier` in Java:

```java
MetaFieldRegistry.registerSupplier("batteryVoltage", Double.class, 
    () -> hardwareMap.voltageSensor.iterator().next().getVoltage());
```

Whenever `batteryVoltage` is accessed in your sequence (e.g., in a `PRINT` statement), it fetches the latest value from the sensor.

```text
RACE(
    PRINT(batteryVoltage),
    WAIT(3)
    )
```

> [!WARNING]
> If you assign a value to a sensor field in your auto (e.g., `batteryVoltage = 12.0`), the static value will overwrite the supplier for the remainder of the run.

### Condition Suppliers
These are special boolean suppliers used exclusively for `if` conditions, registered via `UserActionRegistry`:

```java
UserActionRegistry.registerCondition("isPressed", () -> touchSensor.isPressed());
```

**Key Difference:** Unlike regular variables, Condition Suppliers **cannot** be overwritten by assignments in the sequence file, making them safer for logic-critical sensors.

## Changing Variables in Auto
You can change the value of any variable (except Condition Suppliers) directly in your sequence file using the assignment operator `=`.

> [!IMPORTANT]
> **Static Initialization:** In `.auto` files, any line containing an assignment (`key = value`) is processed **during initialization**. This means all top-level assignments are applied before the first action even starts, regardless of their position in the file or whether they are inside `if` blocks.

### Assignment Examples (Static Behavior)
In the examples below, notice how the assignments happen before the `RACE` actions begin:

```text
num = 1
if (false) {
    num = 2
}
RACE(
    PRINT(num),
    WAIT(3)       
)
// Telemetry: 2 (Static assignment overrides the 'if' logic)
```

```text
num = 1
RACE(
    PRINT(num),
    WAIT(3)       
)
num = 2
RACE(
    PRINT(num),
    WAIT(3)       
)
// Telemetry: 2 -> 2 (Both assignments happen before the sequence starts)
```

If you need **sequential** variable changes that happen mid-auto, you should define them within a **MetaAction** (Big Action) in your settings file, where assignments are treated as executable actions.
```text(in UserActionSetting)
    printAction = {
        RACE(
            PRINT(num),
            WAIT(3)       
        )
        num=2
    }
```

```text(in ACTIVE_BlueFarAuto.txt)
    Starting=pose2d(-24,0,0)
    title="Blue Far Auto"
    num=1
    printAction
    printAction
    //Telemetry: 1 -> 2
```


## Custom Types (MetaFields)
By implementing the `MetaField` interface, you can define how complex objects (like `Pose2d` or custom settings) are parsed from text. 

1. **Implement `MetaField<T>`**: Define the identifier and parameter types.
2. **Register Type**: `MetaFieldRegistry.registerType(new MyCustomType());`
3. **Usage**: Use them in your files like `myVar = customType(param1, param2)`.
