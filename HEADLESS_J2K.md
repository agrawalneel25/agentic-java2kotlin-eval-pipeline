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

Real-world benchmark command:

```bash
bash scripts/fetch-benchmark.sh
bash scripts/run-static-j2k.sh work/commons-csv/src/main/java build/commons-csv-j2k
./gradlew run --args="--java work/commons-csv/src/main/java --kotlin build/commons-csv-j2k --report reports/COMMONS_CSV.md --jsonl reports/commons-csv.jsonl"
```

The GitHub Action runs the same shape under `xvfb-run`: build the runner, convert the edge-case corpus, convert Apache Commons CSV, then evaluate both generated Kotlin trees.
