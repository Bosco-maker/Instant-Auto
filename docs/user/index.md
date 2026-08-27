# User Guide

Welcome to the InstantAuto User Guide. This section covers everything you need to know to write and manage autonomous routines **without writing Java code**.

## Conceptual Model

InstantAuto separates **robot capabilities** (defined in Java) from **autonomous logic** (defined in text files).

| Layer | Defined In | Changed By | Recompile Needed? |
|-------|------------|------------|-------------------|
| Hardware, sensors, primitive actions | Java (`ConfigManager`, `ActionManager`) | Programmer | Yes |
| Poses, tuning params, reusable macros | `GeneralRobotSettings.txt`, `UserActionSettings.txt` | Programmer / Drive Team | **No** |
| Match-specific routine | `ACTIVE*.txt` | Drive Team | **No** |

**Key insight**: Once a programmer registers `STRAFE.TO`, `SPLINE.TO`, and sensor variables in Java, anyone can compose complex autonomous routines by editing text files on the Robot Controller.

---

## Three File Types

| File | Purpose | Example |
|------|---------|---------|
| `GeneralRobotSettings.txt` | Global configuration: poses, speeds, sensor defaults | `scorePose = pose2d(48, 24, 90)` |
| `UserActionSettings.txt` | Reusable "Big Actions" composed of primitives | `scoreSample = { SPLINE.TO(...), INTAKE.ON, WAIT(0.5) }` |
| `ACTIVE<Name>.txt` | Match routine: starting pose + action sequence | `Starting = pose2d(-24, 0, 0)` then `STRAFE.TO(...)` |

---

## Getting Started

→ [Getting Started Tutorial](getting-started.md) — Complete end-to-end walkthrough from installation to first working autonomous.

---

## Core Concepts

| Topic | Description |
|-------|-------------|
| [Syntax](syntax.md) | File format, comments, variables, actions, conditionals |
| [Actions](actions.md) | Complete reference: `STRAFE.TO`, `SPLINE.TO`, `PRINT`, `PARALLEL`, `RACE`, `WAIT`, `HELLO.WORLD` |
| [Variables](variables.md) | Variable types, suppliers, sensor fields, static vs runtime assignment |
| [Conditions](conditions.md) | `if / else if / else` blocks, condition evaluation priority |

---

## Examples

→ [Examples](examples.md) — Real autonomous routines tested on physical robots.