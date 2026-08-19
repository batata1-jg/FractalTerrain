# superpowers/

Dated design and planning artifacts written by the planning skills. Historical record of intent —
the code, not these files, is the authority on what shipped.

## Subdirectories

| Directory | What                                                                            | When to read                                                        |
| --------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `specs/`  | `2026-08-18-river-primitive-grid-lut-design.md` (the one-lattice-pass carve design), `2026-08-19-dead-code-survey.md` (reachability inventory, acted on in `06a15dd`) | Understanding why the carve merged into one pass, or what the dead-code sweep removed |
| `plans/`  | `2026-08-19-river-primitive-grid-lut.md` — task-by-task plan for the carve rewrite. **Checkboxes are stale**: the rewrite landed, but no task was ticked | Tracing which step of the carve rewrite introduced a given behaviour |
