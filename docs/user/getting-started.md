# Getting Started

## Installation

TODO

## Your First Autonomous

*   **How to Make One Auto:**
    *   Navigate to http://192.168.43.1:8080/ (http://192.168.49.1:8080/ if you are using a phone controller), select onBot Java.
    *   Create a new file at org/firstinspires/ftc/teamcode
    *   Set File Type to Text File
    *   Set File Name to ACTIVE[your-auto-name].txt, example: ACTIVEBlueFarAuto.txt
    *   Restart Robot and you auto will appear at Select Autonomous
    *   Write these into the auto text file:
    ```toml
        Starting=pose2d(-24,0,0)
        title="Blue Far Auto"
        PARALLEL(
            HELLO.WORLD,
            WAIT(3)       
        )
    ```
    *   Build the code (press the red wrench button)

    **Running on the Robot Controller**
    *   Restart Robot and you auto will appear at Select Autonomous
    *   Telemetry should output: Hello World for 3 seconds, then output all registered fields (as auto ends)