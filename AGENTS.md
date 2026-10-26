# Spider Tracker

- Native Android app: Kotlin, Jetpack Compose, one `app` module. Reuse existing state, components and dependencies; preserve behavior and keep changes small.
- Inspect affected callers and tests before editing. Never discard user changes; inspect Git status before staging and commit only reviewed files. Do not commit local SDK settings, caches, credentials or generated APKs by default.
- Compose: retain lifecycle-aware state, accessibility semantics, keyboard-aware overlays and adaptive layouts. Keep business logic outside composables where the existing code does.
- MapLibre Native: extend `MapRenderer.kt` and existing geographic layers. Preserve camera/gesture behavior, lifecycle cleanup, attribution and offline fallback.
- Photon: reuse `AddressSearch.kt`; preserve debounce, throttling, caching, cancellation and explicit errors. Respect `geocoderUrl`; never substitute fake search or location results.
- Routing: reuse `RoutingProvider`, `NavigationController` and the Valhalla integration. `valhallaUrl` defaults to empty; preserve endpoint validation, permission handling, foreground-service cleanup and honest unavailable-service states. Keep demo alerts separate from routing.
- Pixel-art UI: reuse supplied PNGs, pixel typography, navy/cyan palette and existing components. Preserve sharp sprite rendering, circular effects, reduced motion and overlay ordering.
- Validate with `./gradlew.bat -g .gradle-user-home --offline --console=plain :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Use `:app:testDebugUnitTest --rerun` for fresh JVM results and `:app:connectedDebugAndroidTest` on a test emulator. Run `py -3 scripts/update-attractions.py --self-test` for catalog changes. Document failures; do not suppress them or fix unrelated code.
- Token efficiency: use targeted `rg` searches and read affected files only. Exclude `.gradle*`, `.tools`, build outputs and historical evidence from broad searches. Reuse existing helpers; avoid speculative abstractions, dependencies, large logs and unrelated refactors. Report outcome, validation limits and one next task briefly.
- Current verification and limitations: `docs/development-baseline.md`. Historical README phase descriptions may lag the source; inspect current code and tests before planning changes.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.
