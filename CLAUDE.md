# FractalTerrain

A Fabric mod (Minecraft 1.20.1 / Java 21) that replaces overworld terrain generation with the output of a
deep-learning diffusion model, plus a procedural hydrology (riverUnit) system.

## Files

| File               | What                                                     | When to read                                                          |
| ------------------ | -------------------------------------------------------- | --------------------------------------------------------------------- |
| `ARCHITECTURE.md`  | System overview: pipeline, providers, data flow, frames  | Understanding how generation fits together, onboarding, cross-cutting |
| `README.md`        | User-facing mod description, install, disclaimer         | Understanding the product, install steps, known-broken features       |
| `build.gradle`     | Loom build, Spotless, model-manifest + debug-harness tasks | Adding tasks/deps, changing the pinned model revision, build issues   |
| `gradle.properties`| Gradle/mod versions and flags                            | Bumping versions, toggling build flags                                |
| `settings.gradle`  | Gradle project + repositories                            | Adding repositories or subprojects                                    |
| `.gitignore`       | Ignore rules; note `/CLAUDE.md` and `/.claude/` are ignored (local-only) | Checking why a file is untracked, adding an ignore rule |
| `LICENSE.txt`      | License                                                  | Legal questions                                                       |
| `quick-tips-optimizing-jvm.md` | JVM hot-path optimization techniques; a reference essay, not project-specific | General JVM performance technique lookup |
| `river-dynamics.md` | Research notes deriving Rosgen Level-I classification from a DEM; basis for `hydrology/rosgen/` | Understanding why the Rosgen classifier works the way it does |

## Subdirectories

| Directory | What                                              | When to read                                            |
| --------- | ------------------------------------------------- | ------------------------------------------------------- |
| `src/`    | Mod source (`main`, `client`, `test` source sets) | All code work — see `src/main/java/.../fractal_terrain` |
| `gradle/` | Gradle wrapper config (no checked-in `gradlew`)   | Matching a local `gradle` to the wrapper version        |
| `libs/`   | Local jar dependencies                            | Adding/updating a local dependency                      |
| `run/`    | Dev Minecraft working dir; `run/debug` dumps      | Inspecting generated worlds and debug PNG/TIFF output   |
| `docs/`   | Scraped Minecraft Wiki worldgen reference (36 pages, generated — do not edit), plus `superpowers/` dated plans and specs | Vanilla worldgen semantics: surface rules, density functions, biomes, features, carvers; or prior design work |
| `.claude/`| Agent skills + doc/code conventions. Git-ignored, local-only | **Writing any docstring or comment** (`conventions/documentation.md` "Tier 3"), doc conventions, skill definitions |
| `build/`  | Gradle build output. Generated — do not edit      | Never edit directly                                     |
| `logs/`   | Runtime logs. Generated — do not edit             | Never edit directly                                     |

The primary source package is `src/main/java/me/batata_1/fractal_terrain/` (the intermediate
`me/batata_1/` dirs are package scaffolding only).

## Build

```
gradle build              # compile + spotlessCheck + mod jar
gradle spotlessApply      # run BEFORE committing (palantirJavaFormat, enforced by build)
gradle runClient          # launch dev client (working dir run/)
gradle runServer          # launch dev server
gradle runDatagen         # Fabric data generator
```

Gradle properties: `-PwithSourcesJar=true`.

There is no checked-in `gradlew` wrapper — invoke Gradle via your IDE or a local `gradle` matching
`gradle/wrapper/gradle-wrapper.properties`.

## Test

Two layers. A JUnit 5 suite (`useJUnitPlatform()`, 15 `*Test.java` classes under
`src/test/java/`) gates the deterministic hydrology math:

```
gradle test                   # JUnit 5 golden suite
```

**Baseline, measured 2026-08-23:** `gradle compileJava compileClientJava spotlessCheck` **passes**, but
`gradle build` **fails at `:compileTestJava`** — `src/test/.../hydrology/features/ConfluencePrimitiveTest.java`
calls `ConfluencePrimitive.w(double[])` and `.d(double[])`, neither of which exists on
`ConfluencePrimitive` (it implements only `h(double[])`); 9 errors. This is pre-existing and unrelated to
the `RiverProvider`/`LocalNetworkBuilder` river refactor — that test file was last modified three commits
before the refactor began. `gradle test` therefore cannot run at all as the tree stands.

With `ConfluencePrimitiveTest` excluded, a run reported **81 tests, 19 failed, 1 skipped** — mark that
figure explicitly as measured with a test file excluded, not as a clean baseline:

> `RosgenKeyTest` (6), `ComputeRiverGridTest` (3), `ChannelGeometryTest` (3), `RiverGoldenTest` (2),
> `MeandersGoldenTest` (2), `GlobalRiverGoldenTest` (1), `ReachMetricsSamplerTest` (1), `CentrelineTest` (1).

`ComputeRiverGridTest`'s 3 failures are new relative to the last quoted baseline (which had it at 12
passing), but are **not** caused by the river refactor: the only production file it exercises is
`HydrologyProfileInprinter`, and the refactor's diff to that file is import renames and formatting only.
Record it as pre-existing-but-newly-visible — the suite could not compile far enough to reach it before.

The suite has broken and been repaired several times; treat any quoted baseline, including this one, as
a claim to re-verify rather than a fact. Re-measure before blaming your own change: build a worktree at
`HEAD`, copy `libs/onnxruntime/teste.jar` into it (`libs/` is git-ignored, and without it you get ~132
phantom errors), and run `gradle test` there. Comparing the *actual failure messages* in
`build/test-results/test/*.xml` — not just which test names fail — is what proves a refactor left
generation output untouched.

Manual harnesses run as `JavaExec` tasks pointing at `main()` classes in `debug/tests/`:

```
gradle globalRiverTest        # global riverUnit network + PNG dumps
gradle riverTest              # local riverUnit network + PNG dumps
gradle meandersTest           # meander relaxation
gradle spatialIndexBenchmark  # spatial-index microbench
gradle pipelineTest           # NOT a pipeline test — samples nvidia-smi VRAM only
gradle captureSelectionTest   # stream-capture selection fixtures, no JUnit suite required
```

When adding a harness, add a matching `tasks.register('<name>', JavaExec)` entry in `build.gradle`.

## Development

### Read the guidelines before implementing

Before the first `Edit`, `Write`, or `NotebookEdit` of a session — and before writing a plan that
specifies code — read this project's own guidance. Applies to code, tests, config, and docs. Does
not apply to answering questions, reading, or searching.

Read order; stop once you have what the task needs, and skip what is absent without comment:

1. This file — the index. Follow its "when to read" pointers.
2. The `README.md` or `CLAUDE.md` in, or above, the directory you are about to edit.
3. `ARCHITECTURE.md` for anything crossing providers, frames, or the generation pipeline.
4. `.claude/conventions/` — read `CLAUDE.md` there, then open only the files covering what you
   touch: `documentation.md` for any docstring or comment, `structural.md` and `code-quality/`
   for code, `performance.md` on or near a hot path, `temporal.md` for comment tense,
   `diff-format.md` for diffs, `intent-markers.md` for `:PERF:`/`:UNSAFE:`/`:SCHEMA:`.

`docs/` is scraped Minecraft Wiki worldgen reference, not conventions. Read it for vanilla
worldgen semantics, never for how to write code here.

Indexes first, then only the sections your change touches. Once per session per file — never
re-read what is already in context.

- These docs outrank your defaults. Where a convention differs from how you would otherwise
  write it, follow the convention.
- Where two docs conflict, the one nearest the file being edited wins.
- Where a doc contradicts the surrounding code, follow the doc and say so in one line.
- Where no doc covers the case, match the two or three nearest existing files and name them.

### Delegate the work

Implementation is delegated, not done in the main thread.

- Default: a `general-purpose` agent on Sonnet (Agent tool, `subagent_type: "general-purpose"`,
  `model: "sonnet"`).
- Dispatch `developer` only for changes you estimate at 1000+ lines touched, never below: its
  heavy agent prompt and scripted workflow burn far more tokens, and pay off only at that size.
- Trivial exception: a one- or two-line mechanical edit the user already specified exactly, or a
  revert. Say when you skip delegation and why.

A subagent starts with none of the conversation. Its briefing carries, and carries only:

1. The task, stated as an outcome.
2. The exact files to create or change.
3. The guideline paths from the read order above — the subagent reads them itself; do not
   paraphrase their contents.
4. The acceptance check: `gradle spotlessApply` then `gradle build`. Where the change touches
   hydrology math, add `gradle test` **and** the current failure baseline from the Test section
   above, so the subagent does not chase pre-existing failures.

Keep in the main thread: design decisions, user communication, and verification of what comes
back. Read the returned diff before reporting it done — a subagent's claim is not evidence.

### Report

Open the message that first proposes or makes a change with one line:

`Guidelines: <paths read>` — or `Guidelines: none cover this; matched <file>`

Before claiming work is done, check the change against each convention you read and name any you
knowingly deviated from, with the reason.
