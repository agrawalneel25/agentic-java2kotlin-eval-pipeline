# Headless J2K Notes

JetBrains' static Java-to-Kotlin converter is implemented inside the IntelliJ IDEA Kotlin plugin. It is not exposed as a stable standalone CLI.

I found the entry point by searching the IntelliJ Community source for `NewJavaToKotlinConverter` and tracing the call sites in `KotlinJ2KOnTheFlyEditorButtonProvider`. `ApplicationStarter` is the documented extension point for headless IDE commands; the j2k runner registers one named `j2k` so the sandbox launcher can invoke it without opening a UI.

This repo adds `runner/`, an IntelliJ Platform plugin with an `ApplicationStarter` named `j2k`. The starter:

1. Copies Java files into a temporary project.
2. Opens that project through IntelliJ Platform APIs.
3. Registers a JDK and Java source root.
4. Waits for smart mode so indexes are ready.
5. Calls `NewJavaToKotlinConverter.elementsToKotlin`.
6. Writes converted `.kt` files to the requested output directory.

Local command:

```bash
bash scripts/run-static-j2k.sh edge-cases/java build/edge-static-j2k-real
```

Real-world benchmark command:

```bash
bash scripts/fetch-benchmark.sh
bash scripts/run-static-j2k.sh work/commons-csv/src/main/java build/commons-csv-j2k-real
./gradlew run --args="--java work/commons-csv/src/main/java --kotlin build/commons-csv-j2k-real --report reports/COMMONS_CSV.md --jsonl reports/commons-csv.jsonl"
```

The GitHub Action builds and validates the runner, then checks reports generated from live static-J2K output. I keep the actual `runIde` command as a local/manual step because IntelliJ sandbox startup can hang under headless Linux even when the same runner completes on a developer machine.
