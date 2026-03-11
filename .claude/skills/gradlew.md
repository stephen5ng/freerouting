---
name: gradlew
description: Run Gradle commands in the freerouting project with proper JAVA_HOME
---

When the user asks to build, run tests, or execute Gradle tasks in this project, use `./gradlew` with JAVA_HOME set to `/opt/homebrew/opt/openjdk`.

## Examples

- "build the project" → `JAVA_HOME=/opt/homebrew/opt/openjdk ./gradlew build`
- "run tests" → `JAVA_HOME=/opt/homebrew/opt/openjdk ./gradlew test`
- "build the jar" → `JAVA_HOME=/opt/homebrew/opt/openjdk ./gradlew jar`

## Notes

- The freerouting project uses Gradle 9.x and requires JAVA_HOME to be set
- Default JAR is `freerouting.jar` (executable with dependencies)
- Use `--console=plain` for cleaner output when needed
