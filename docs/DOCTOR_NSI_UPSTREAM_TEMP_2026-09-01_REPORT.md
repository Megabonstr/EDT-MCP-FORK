# Doctor NSI EDT-MCP upstream temporary refresh — 2026-09-01

## Status

**READY** for temporary local installation and Doctor NSI development.

The build is based on the exact pinned upstream checkpoint. Both retained fork-only tools work in the isolated live EDT profile, current upstream `dcs` and `launch` remain registered, no obsolete DCS surface was restored, and the focused source fixture stayed clean. The Doctor NSI product worktree was already dirty and changed independently during this task; this work made no Doctor product source or business-data changes.

## Git and integration

- Upstream base: `f13ee9c7b82829e9998491b72895cec8f45cdd96`
- Temporary branch: `local/doctor-nsi-upstream-temp-2026-09-01`
- Implementation/evidence HEAD before this report commit: `8125e7f7e28f21718ea07c597aed41e918f7aa96`
- Ported only: `code_review` and `validate_form_model`, their focused tests/guides/registration, and the `BackgroundJobs.startExclusive` support required by `code_review`.
- Preserved from upstream: the canonical `launch` tool with `run|debug` mode and the current `dcs` implementation.
- Deliberately not restored: `debug_launch`, `modify_dcs_settings`, old DCS writer code, other fork features, or Doctor NSI product changes.
- Conflict resolution: the README/tool-list conflict was resolved semantically against current upstream registration; no mechanical old-side selection was used.
- Diff from base before this report: 24 files, 2,302 insertions, 15 deletions.
- Fork `master`, upstream branches, and the earlier temporary worktree/branch were not modified or merged.

## Verification

- Focused Java tests: **37 passed, 0 failed, 0 errors, 0 skipped**:
  - `CodeReviewToolTest`: 1
  - `ValidateFormModelToolTest`: 3
  - `FormModelValidatorTest`: 7
  - `BackgroundJobsTest`: 26
- Focused tools-list golden check: **1/1 passed**; final non-update run also passed and the fixture was clean.
- Final package build: `source/compile.sh --skip-tests --version 2026.09.01-temp` — **BUILD SUCCESS**.
- Full unit, full e2e, and conformance suites were intentionally not run, as required by the interim minimal task.

### Isolated live smoke

Profile: `C:\AI\EDT-MCP-DEV\1C_EDT 2026.2`, workspace `C:\AI\EDT-MCP-DEV-WS`, endpoint `http://127.0.0.1:8769/mcp`.

- Health: `ok`; live tool count: 90.
- Present: `code_review`, `validate_form_model`, `dcs`, `launch`.
- Absent: `debug_launch`, `modify_dcs_settings`.
- `validate_form_model(TestConfiguration, CommonForm.Form)`: success, structurally valid, 0 findings.
- Read-only `dcs get conditionalAppearance` on `CommonForm.Form`: success; hash `c4b008c2246145718aad`.
- `code_review` on exactly `CommonModules/OK/Module.bsl`: completed in 4.968 s with MCP:RSV Code Review `1.4.0.v202607091607`; 0 errors, 0 warnings, 0 information findings.
- The `TestConfiguration` source fixture remained Git-clean after review.
- New EDT log entries for the valid final calls completed with `outcome=ok`; no new severity-4 entry or attributable EDT/plugin exception appeared. Three earlier severity-2 messages were expected fail-closed responses to a malformed local PowerShell wrapper call with omitted arguments, corrected before the final smoke.

## Installable artifact

- ZIP: `C:\AI\EDT-MCP-UPSTREAM-TEMP-20260901\source\dist\MCP-EDT.v2026.09.01-temp.zip`
- ZIP size: 3,654,634 bytes
- ZIP SHA-256: `5FA2C6227C1418BE4C4EEDF2C56D055F83206BA3DACFB9370A9A94C752A7A080`
- Bundle/JAR version: `1.0.0.202609010745`
- JAR: `C:\AI\EDT-MCP-UPSTREAM-TEMP-20260901\mcp\repositories\com.ditrix.edt.mcp.server.repository\target\repository\plugins\com.ditrix.edt.mcp.server_1.0.0.202609010745.jar`
- JAR size: 3,812,479 bytes
- JAR SHA-256: `D445F16C763529D58DC25C8A3D02FBBD485B872740EEF2D8827C52C7A1045B54`
- The same JAR hash was deployed to the isolated profile for the final live smoke.

## Known limits

- This is a temporary upstream refresh, not a release or merge decision.
- The Doctor NSI worktree was not clean before the smoke. Its pre-existing modified/untracked files were preserved; the plugin task did not write there.
- Runtime proof is limited to the explicit focused calls above. No whole-project review, launch execution, DCS mutation, or broad suite was performed.
- Branch push state and the final report commit SHA are recorded in the delivery response after remote verification.
