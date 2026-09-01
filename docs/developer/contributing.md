# Contributing

## Repos & their structure:
1. [Esquimalt-Atom-Smasher/instant-auto-quick-start](https://github.com/Esquimalt-Atom-Smashers/instant-auto-roadrunner-quickstart):
    - The **canonical** InstantAuto, the source of truth. Please submit your contributions/pull requests here. 
    - The [Instant Auto Core](https://central.sonatype.com/artifact/io.github.bosco-maker/instantauto) is published to Maven Central from here (May change later).
    - It is where Instant Auto is adapted into Roadrunner and ready-to-go.
    - Welcomes any [PedroPathing](https://github.com/Pedro-Pathing/PedroPathing) adaptation to be submitted here.
    - ```
quick-start/
├── instantauto/              # Core module (no dependencies, canonical )
│   ├── src/main/java/com/example/instantauto/
│   │   ├── actions/          
│   │   └── configs/          
│   ├── src/test/             
│   └── build.gradle.kts      # Everything is published to Maven Central from here 
├── Teamcode/                 
│   └── src/main/java/com/example/Teamcode/ # FTC code lives here
```

2. [Bosco-maker/InstantAuto](https://github.com/Bosco-maker/Instant-Auto):
    - The [Docs & Web Sim Website](https://bosco-maker.github.io/Instant-Auto/developer/contributing/#architecture-decision-records-adrs) is written and hosted here.
    - ```
Instant-Auto/
├── instantauto/              # Core module (no dependencies, non canonical)
│   ├── src/main/java/com/example/instantauto/
│   │   ├── actions/          # Action, MiniAction, UserAction, Registries, AutoParser
│   │   └── configs/          # MetaField, MetaFieldRegistry, ConfigParser, ConfigEntry
│   ├── src/test/             # Unit & integration tests
│   └── build.gradle.kts
├── docs/                     # MkDocs documentation, the docs website is hosted here
│   ├── user/                 
│   ├── programmer/          
│   ├── developer/           
│   ├── reference/            
│   └── simulator/            
├── pureJava/                 
│   └── src/main/java/com/example/purejava/ # Development-only java app testbed
└── mkdocs.yml
```

3. [Esquimalt-Atom-Smashers/Instant-Auto](https://github.com/Esquimalt-Atom-Smashers/Instant-Auto):
   - Placeholder?  [Bosco-maker/InstantAuto](https://github.com/Bosco-maker/Instant-Auto) is forked from there but it is useless for now. 


## Adding a New Pathing Library (e.g., PedroPathing)

### 1. Core Module (Unchanged)
`instantauto` module requires **no changes** — it's pathing-agnostic.

### 2. TeamCode Layer (New Implementation)

Create new module or package: `TeamCode/src/main/java/.../pedropathing/` for all [PedroPathing adaptation](https://github.com/Pedro-Pathing/Quickstart/tree/master/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/pedroPathing)

#### Implementation Checklist:
I would appreciate the following from such adaptation

1. Have STRAFE.TO() and SPLINE.TO() return a smooth path WITHOUT changing the core Instant Auto core (might involve putting the path through InstantAuto/Action interface and back)
2. Allows if(condition-obtainable-during-auto-only){} else {} return a working path (involves condition suppliers)
3. Rehaul(if necessary) the start() in autonomousbase.java

---

## Testing
### MeepMeep Simulation
See [MeepMeep testbed](https://bosco-maker.github.io/Instant-Auto/programmer/meep-meep-testbed/#setup-instructions)

### Physical Robot
Deploy `AutonomousBase` subclass to Robot Controller. Filming a video showing what worked / did't work would be nice.

---

## Contribution Process

1. Fork [Esquimalt-Atom-Smasher/instant-auto-quick-start](https://github.com/Esquimalt-Atom-Smashers/instant-auto-roadrunner-quickstart).
2. Put in your commits.
3. Put out a pull request, to merge with the master branch.
4. I will review it, or create a new branch onto the repo if for PedroPathing. (Details to be discussed)

You may do unofficial releases, but creating a pull request gives you feedback from me. 

Thank you for your contribution & use of this library.

---

## Resources

- [RoadRunner 1.0 Docs](https://rr.brott.dev/docs/v1-0/introduction/)
- [FTC SDK Docs](https://ftc-docs.firstinspires.org/)
- [MkDocs Material](https://squidfunk.github.io/mkdocs-material/)
- [GitHub Repository](https://github.com/Bosco-Maker/Instant-Auto)