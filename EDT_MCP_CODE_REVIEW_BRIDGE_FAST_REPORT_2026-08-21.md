# EDT-MCP Code Review Bridge — implementation report

Date: 2026-08-21  
Repository: `https://github.com/Megabonstr/EDT-MCP-FORK.git`  
Branch: `feature/code-review-bridge-2026-08-21`  
Base branch: `local/doctor-nsi-integration-2026-08-20`  
Base commit: `0b7f92a9e61550c660ca32d05318b6a5391a55cb`  
Implementation commit: `4f2b84f7bc08d2cd9ce0514bc660ab05e82fe8f7`

## Result

The optional MCP:RSV Code Review 1.4.0 analyzer is now callable headlessly through the current DitriXNew/EDT-MCP server as the `code_review` MCP tool. The bridge uses reflection and therefore introduces no compile-time dependency on the optional plugin and copies no GPL source or binary into EDT-MCP.

The tool accepts exactly one scope:

- `modulePaths`: one or more project-relative `.bsl` files;
- `wholeProject: true`: an explicit full-project review.

Analysis runs as one background job. Only one Code Review job may run at a time. Once the analyzer has been invoked, cancellation is reported honestly as `alreadyCommitted`; the external analysis cannot safely be recalled through the current plugin API.

The normalized result contains `status`, `pluginVersion`, `projectName`, `scope`, `summary`, and `findings`. Every finding includes the project-relative `modulePath`, one-based `line` and `column`, `severity`, diagnostic `code`, `message`, and an optional diagnostic `url`.

## Implementation

Changed files in the implementation commit:

- `README.md`
- `docs/tools/README.md`
- `docs/tools/code_review.md`
- `mcp/bundles/com.ditrix.edt.mcp.server/guides/code_review.md`
- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/preferences/ToolGroup.java`
- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/BuiltInToolRegistrar.java`
- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/Toolsets.java`
- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/CodeReviewTool.java`
- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/utils/CodeReviewBridge.java`
- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/utils/CodeReviewBridgeException.java`
- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/utils/CodeReviewReportMapper.java`
- `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/CodeReviewToolTest.java`
- `tests/e2e/tools/test_code_review.py`

The bridge resolves bundle `com.radzivillovich.edt.rsv.codereview` and invokes the public method `BslLanguageServerRunner.analyzeToJson(File analyzeDir, File workingDir)`. The successfully detected installed plugin version was `1.4.0.v202607091607`.

Selected modules are copied to an isolated staging tree while preserving their project-relative paths. Each selected parent directory is analyzed with the staging root as the working directory, and reports are merged. Whole-project mode analyzes the project source root directly. The bridge does not open the Code Review view and does not create EDT problem markers.

## Build and focused test

Focused verification command:

```text
mvn -pl targets/default,bundles/com.ditrix.edt.mcp.server,tests/com.ditrix.edt.mcp.server.tests -am -Dtest=CodeReviewToolTest verify
```

Result: `BUILD SUCCESS`; 1 focused test, 0 failures, 0 errors; completed at 2026-08-21 06:43:59 +03:00.

Update-site build command:

```text
mvn verify --batch-mode -T 1C -DskipTests
```

Result: `BUILD SUCCESS`; completed at 2026-08-21 06:44:34 +03:00.

Built update-site archive:

- version/qualifier: `1.0.0.202608210344`
- path: `C:\AI\EDT-MCP-LOCAL-DOCTOR-NSI-20260820\mcp\repositories\com.ditrix.edt.mcp.server.repository\target\com.ditrix.edt.mcp.server.repository-1.0.0-SNAPSHOT.zip`
- SHA-256: `D4CD51BA79B0FEEEB53AA331AE285A7C54BEF5DC7CFBF60D9D9EAADD8306C205`

Installed bundle verified after the user performed the EDT installation and restart:

- path: `C:\Users\L\.p2\pool\plugins\com.ditrix.edt.mcp.server_1.0.0.202608210344.jar`
- SHA-256: `E4D6BA16FE5B1BB42C64CCE5C82B159F6E486A224C881C18007CF690D2769F31`

All subsequent installable builds must use a higher qualifier/version so p2 recognizes them as updates.

## Live smoke test

The live test used the normal EDT-MCP proxy at `http://127.0.0.1:8764/mcp`.

- proxy status: healthy, one backend discovered at port 8765;
- EDT backend: 2026.2.0.289, server running;
- a fresh MCP `tools/list` exposed `code_review`;
- project: `ВнешниеОбработкиТекущие`;
- module: `ExternalDataProcessors/МастерНСИ/Forms/НастройкиОтбора/Module.bsl`;
- completed job: `f3b003ff-fe86-422e-a880-9a24afbdb902`;
- analyzer version: `1.4.0.v202607091607`;
- summary: 0 errors, 3 warnings, 0 informational findings, 3 total;
- all returned findings retained the requested module path.

Representative finding:

```text
line: 60
column: 2
severity: Warning
code: UnusedLocalVariable
message: Удалите неиспользуемую переменную ВыполняетсяРедактированиеОтборов
url: https://1c-syntax.github.io/bsl-language-server/diagnostics/UnusedLocalVariable
```

Concurrency and cancellation were checked with job `513d4c3b-b08b-4e95-8985-7c3fe5d6e62e`: a second simultaneous start was rejected, cancellation preview made no change, and confirmed cancellation returned `alreadyCommitted` with an explicit statement that the analysis could not be recalled.

A previous disposable whole-project probe against `WM_Probe_Sandbox_20260819` also completed successfully with 21 findings (0 errors, 18 warnings, 3 informational), confirming the full-project route and JSON mapping.

After the final module smoke test, ordinary module listing still returned the expected module and filtered `get_project_errors` returned `No Errors Found`. This supports that the headless path did not add EDT markers. No UI entry point was called, so no Code Review window was opened.

## Skill routing

The Code Review workflow was updated so BSL review uses `code_review` with `modulePaths` by default, reserves `wholeProject` for an explicit request, and falls back to manual/native validation only when the optional analyzer is unavailable or incompatible.

- canonical skill: `C:\AI\EDT-MCP_BUSINESS_SKILLS_LOCAL\skills\edt-mcp-code-review\SKILL.md`
- installed skill: `C:\Users\L\.agents\skills\edt-mcp-code-review\SKILL.md`
- SHA-256 of both files: `027585AB85E959C548C5EEC90E1839053C6F099B162CEE93693DE4E1B648009A`

The two copies were byte-identical after synchronization. The separate skills-pack directory is not a Git repository, so this synchronization is recorded here rather than in the EDT-MCP commit.

## Scope and remaining notes

- No Doctor NSI metadata, BSL, form, DCS, runtime, or business-data artifact was changed.
- No pull request was created, as requested.
- No GPL Code Review or BSL Language Server code or binary was copied into EDT-MCP. The optional Code Review plugin remains independently installed and licensed; the bridge only calls its public runtime surface reflectively.
- The full 5,377-test suite and full live e2e suite were intentionally not run for this fast task; focused unit/build and live smoke evidence are recorded above.
- Generated documentation and golden regeneration exposed broad pre-existing unrelated drift. With explicit user approval, that mass drift was reverted and only the bounded `code_review` documentation/index changes were retained.
- Suggested upstream improvement for MCP:RSV Code Review: when `analyzeToJson` is interrupted, ensure the spawned BSL Language Server process is destroyed (including `destroyForcibly` if graceful termination does not complete) and that all plugin-owned temporary directories are cleaned in `finally`. This would make future cancellation safer.

