# Doctor NSI: temporary EDT-MCP build on current upstream

Date: 2026-08-27 (Europe/Moscow)

## Result

A temporary installable EDT-MCP build was prepared from the current
`DitriXNew/EDT-MCP:master`, while the fork's `master` was left untouched. The
fresh upstream DCS implementation remains authoritative. Two fork-only tools
were adapted to that upstream architecture:

- `code_review` — a headless bridge to the separately installed MCP:RSV Code
  Review plugin;
- `validate_form_model` — read-only structural validation for managed forms.

The installed build was verified through the live MCP endpoint in the normal
EDT 2026.2 workspace.

## Git state and provenance

| Item | Value |
| --- | --- |
| Upstream remote | `https://github.com/DitriXNew/EDT-MCP.git` |
| Verified upstream branch | `DitriXNew/EDT-MCP:master` |
| Upstream checkpoint | `b91783a4b2bda75697e2b528222c6edb02d98279` |
| Upstream description | `v2.15.1-4-gb91783a4` |
| Fork remote | `https://github.com/Megabonstr/EDT-MCP-FORK.git` |
| Fork `master` checkpoint | `9018755ed14edd656af352af09a75e184f47e8e6` |
| Temporary branch | [`local/doctor-nsi-upstream-temp-2026-08-27`](https://github.com/Megabonstr/EDT-MCP-FORK/tree/local/doctor-nsi-upstream-temp-2026-08-27) |
| Implementation checkpoint before this report | `505849e60a3607646f4788acab576b2ce25f6bb8` |

`upstream/master` was fetched again immediately before preparing this report;
it still resolved to the expected `b91783a4...` checkpoint. The temporary
branch was created from that upstream commit. No merge, rebase, or push was
performed against the fork's `master`.

The implementation commits are:

1. `926f95ba` — port the headless Code Review bridge;
2. `3b1e5db2` — port managed-form structural validation;
3. `505849e6` — harden background-job, review, and form-validation contracts.

## What was ported

### `code_review`

The tool was ported from the fork's former
`feature/code-review-bridge-2026-08-21` work, but adapted to the current
upstream registrar, toolsets, preferences, guides, background-job registry,
unit-test layout, and e2e ratchets.

Contract highlights:

- the caller must supply either explicit `modulePaths` or
  `wholeProject=true`; omission never widens the scope;
- only project-relative `.bsl` module paths are accepted;
- the analyzer works in an isolated staging directory and does not edit the
  product repository;
- only one review analysis may run at a time;
- after the request is handed to the analyzer, cancellation reports
  `alreadyCommitted` because plugin 1.4.0 exposes no safe cancellation handle;
- the terminal result includes analyzer version, exact scope, summary, and
  structured findings.

References:

- [tool guide](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/docs/tools/code_review.md)
- [Java tool](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/CodeReviewTool.java)
- [bridge implementation](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/utils/CodeReviewBridge.java)
- [black-box e2e test](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/tests/e2e/tools/test_code_review.py)

### `validate_form_model`

The current upstream form tools provide layout snapshots and screenshots, but
do not replace a structural form-model validator. The port therefore remains
useful and was retained as a read-only Forms tool.

It validates IDs, names, bindings, handlers, attachments, parameters, buttons,
and root command-bar invariants. `valid=true` proves only this static structural
pass; it is not runtime UI acceptance.

References:

- [tool guide](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/docs/tools/validate_form_model.md)
- [Java tool](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ValidateFormModelTool.java)
- [validator](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/utils/FormModelValidator.java)
- [black-box e2e test](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/tests/e2e/tools/test_validate_form_model.py)

## What was deliberately not ported

- The old Doctor NSI integration branch was not merged wholesale.
- The legacy `modify_dcs_settings` tool was not restored. The unified upstream
  [`dcs`](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/docs/tools/dcs.md)
  tool is the only supported DCS route in this branch.
- No old form/DCS code was chosen mechanically over an upstream conflict.
- No other fork delta was copied merely because it existed on an old branch.

The name `DcsSettingsWriter` does exist in the current tree, but it is the
implementation introduced by upstream commit `4f108036` as part of the new
unified `dcs` tool. `upstream/master..temporary-branch` contains no change to
that class. It is not the old Doctor NSI writer resurrected by this port.

## Build and automated tests

The installable build was produced with the repository's standard
`source/compile.sh` route and a qualifier newer than the previously installed
local build.

| Gate | Result |
| --- | --- |
| Full Tycho/Maven build | PASS (`BUILD SUCCESS`) |
| Unit tests | 5760 run, 0 failures, 0 errors, 2 skipped |
| `CodeReviewToolTest` | 1 passed |
| `ValidateFormModelToolTest` | 3 passed |
| `FormModelValidatorTest` | 7 passed |
| `BackgroundJobsTest` | 26 passed |
| Official MCP conformance | PASS against baseline: 8 passed, 24 expected failures, 0 unexpected failures |

The full `tests/e2e/` fixture suite was not run in the user's normal workspace,
because it does not contain the dedicated `TestConfiguration` fixture project.
Instead, the ported tools and the new DCS surface were exercised against real,
already-open projects as focused live smoke tests. No fixture project was
silently imported into the user's workspace.

## Installed live smoke

Environment:

- EDT: `2026.2.0.289`;
- proxy: `127.0.0.1:8764`, healthy with one backend;
- backend: `127.0.0.1:8765`, `live=true`, `ready=true`;
- installed EDT-MCP bundle:
  `com.ditrix.edt.mcp.server_1.0.0.202608270739.jar`;
- installed MCP:RSV Code Review bundle:
  `com.radzivillovich.edt.rsv.codereview_1.4.0.v202607091607.jar`.

Results:

| Smoke | Result |
| --- | --- |
| `tools/list` | `code_review`, `validate_form_model`, and `dcs` are registered |
| EDT UI list | user confirmed that the new tools are visible after restart |
| `validate_form_model` | `ExternalDataProcessor.МастерНСИ.Form.Форма`: `valid=true`, 0 findings |
| `dcs` | read `Report.ABCXYZАнализПродаж`, hash `abe633732fb3c6702df4`, 2 data sets, 21 parameters, 4 variants |
| `code_review` | analyzed one explicit Doctor NSI module, plugin 1.4.0, terminal `done` |
| Review findings | 0 errors, 57 warnings, 224 information, 281 total |
| Duplicate admission | second concurrent `code_review` correctly rejected with the active `jobId` |
| Cancellation preview | no mutation; reported the running owner and state |
| Confirmed cancellation | `alreadyCommitted`; no false cancellation claim |
| Product Git after review | clean; the analyzer did not modify the Doctor NSI repository |

The 281 analyzer findings describe the existing selected BSL module. They are
not build failures and were not introduced or auto-fixed by this EDT-MCP port.

## EDT log review

The current workspace log is `H:\Проекты\NSI\.metadata\.log`.

- No severity-4 entry from `com.ditrix.edt.mcp.server` appeared during the
  focused live tests.
- The MCP warnings were attributable to a deliberately rejected duplicate
  review and to slow-call logging for a successful analyzer run.
- Platform/indexing errors recorded at EDT startup occurred before the focused
  calls. They include pre-existing external-project/model indexing noise and a
  failed standalone-server launch; they are not evidence of a failure in the
  tested tools.

## Installable artifacts

| Artifact | Value |
| --- | --- |
| ZIP | `C:\AI\EDT-MCP-UPSTREAM-TEMP-20260827\source\dist\MCP-EDT.v1.0.0.202608271043.zip` |
| ZIP size | 3,590,399 bytes |
| ZIP SHA-256 | `4DA02CBAB7C6C6A55228A1539F5CCF7E89A74E82EFCFCB25E5A6CE4863C3F0CB` |
| JAR | `com.ditrix.edt.mcp.server_1.0.0.202608270739.jar` |
| JAR size | 3,744,385 bytes |
| JAR SHA-256 | `09D766E251BB4216C6E227C0D62D8270236F58FD29A0061EE7FD48BA0DB9829D` |

## Current upstream instructions for the next model

The most important upstream change for instruction migration is the new
task-oriented business-project Skills Pack from upstream commit `213c07d3`.
Another model updating locally installed skills should start here, rather than
copying the repository-development `.claude/skills` tree wholesale:

1. [Skills Pack README](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/README.md)
2. [task router](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/ROUTER.md)
3. [live capability matrix](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/TOOL_CAPABILITY_MATRIX.md)
4. [shared skill contract](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/skills/COMMON.md)
5. [all task-oriented skills](https://github.com/Megabonstr/EDT-MCP-FORK/tree/local/doctor-nsi-upstream-temp-2026-08-27/agent/skills)

Priority skill files for Doctor NSI:

- [session establishment](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/skills/edt-mcp-project-session/SKILL.md)
- [DCS and query work](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/skills/edt-mcp-project-query-dcs/SKILL.md)
- [managed forms](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/skills/edt-mcp-project-forms/SKILL.md)
- [bounded BSL fixes](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/skills/edt-mcp-project-local-fix/SKILL.md)
- [runtime/debug](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/skills/edt-mcp-project-runtime-debug/SKILL.md)
- [YAXUnit jobs](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/agent/skills/edt-mcp-project-yaxunit/SKILL.md)

The standing bilingual rules were also updated. Their own README explains how
they coexist with the compact task-oriented pack:

- [rules pack README](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/rules/README.md)
- [English machine-facing rules](https://github.com/Megabonstr/EDT-MCP-FORK/tree/local/doctor-nsi-upstream-temp-2026-08-27/rules/en)
- [Russian localized rules](https://github.com/Megabonstr/EDT-MCP-FORK/tree/local/doctor-nsi-upstream-temp-2026-08-27/rules/ru)
- [repository verification tiers and EDT-log gate](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/CLAUDE.md)

Recommended migration adjustments for the next model:

| Existing topic | Update for this branch |
| --- | --- |
| DCS | Route all reads and writes through unified `dcs`; remove any `modify_dcs_settings` contract |
| Forms | Add `validate_form_model` as static structural validation; retain snapshot/screenshot for visual evidence |
| BSL review | Add optional `code_review` after native EDT validation; keep scope explicit and poll the exact `jobId` |
| Background jobs | Preserve ownership until terminal state; do not claim committed review cancellation |
| Source of truth | Read current live schema/help first, then generated `docs/tools/`; treat copied matrices as secondary |
| Acceptance | Separate static, automated, live runtime, manual UI, and log-review evidence |

The upstream validator is
[`docs/validate_agent_skills.py`](https://github.com/Megabonstr/EDT-MCP-FORK/blob/local/doctor-nsi-upstream-temp-2026-08-27/docs/validate_agent_skills.py).
On this Windows machine, the global `core.autocrlf=true` converts the checked-out
agent Markdown to CRLF, so running the validator directly in this worktree
reports line-ending violations even though the Git blobs are LF. Validation of
an isolated LF-normalized copy passed: 13 skills, 92 registered/documented tools
plus one proxy tool, router, matrix, and links. A skill migration should preserve
UTF-8 without BOM and LF.

## Known issues and boundaries

- The Code Review analyzer remains an optional external plugin dependency.
- Code Review v1 cannot safely cancel work already handed to the analyzer.
- The normal user workspace is not the dedicated full-e2e fixture workspace.
- Static form validation and DCS readback do not prove 1C runtime UI or business
  semantics.
- Existing EDT startup/indexing errors remain adjacent workspace debt and were
  not changed by this temporary plugin task.
- This branch is temporary. Promotion or merge into a main fork branch requires
  a separate decision.
