# Headless J2K Notes

JetBrains' static Java-to-Kotlin converter is implemented inside the IntelliJ IDEA Kotlin plugin. It is not exposed as a stable standalone CLI.

This repo adds `runner/`, an IntelliJ Platform plugin with an `ApplicationStarter` named `j2k`. The starter:

1. Copies Java files into a temporary project.
2. Opens that project through IntelliJ Platform APIs.
3. Registers a JDK and Java source root.
4. Waits for smart mode so indexes are ready.
5. Calls `NewJavaToKotlinConverter.elementsToKotlin`.
6. Writes converted `.kt` files to the requested output directory.

Local command:

```bash
bash scripts/run-static-j2k.sh edge-cases/java build/edge-static-j2k
```

The GitHub Action compiles this runner and validates plugin configuration:

```bash
./gradlew :runner:compileKotlin :runner:verifyPluginProjectConfiguration
```

I do not force `runIde` execution in CI by default. IntelliJ Platform startup can hang under headless Linux before `ApplicationStarter` dispatches, which makes the job noisy rather than informative. If a stable runner command is available, set the repository variable `J2K_RUNNER_CMD` and the workflow will run conversion on Apache Commons CSV before evaluation.
