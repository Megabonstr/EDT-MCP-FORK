---
name: edt-mcp-project-code-research
description: Investigate 1C BSL behavior, dependencies, impact, and implementation differences through EDT-MCP. Read-only by default; not for plugin development.
---

# EDT-MCP code research

## Purpose and trigger

Use this skill to produce a bounded, evidence-backed explanation of 1C project
code, dependencies, impact, or implementation differences.

## Operating rule

Read and apply [the common operating rules](../COMMON.md) before this workflow.

## Task boundary

Keep the task read-only and tied to exact project, metadata FQN, module, method,
or symbol targets. Route a known bounded correction to
`edt-mcp-project-local-fix`; do not turn research into implementation without
authorization.

## Primary workflow

1. Resolve candidates with `get_metadata_objects`, `list_modules`, and
   `search_in_code`. Search is literal and not dialect-aware: for identifiers,
   use AST-backed definition/reference/hierarchy tools and do not infer absence
   from one Russian or English spelling.
2. Narrow with `get_module_structure`, then prefer `read_method_source` over
   `read_module_source` unless module-level context is required.
3. If the task supplies an exact standard implementation donor, coding check,
   or pinned reference guide, compare only the relevant project surface with
   those handles. Use them to explain differences or constraints; do not widen
   into a survey of the donor repository.
4. Follow only the relationships needed using `go_to_definition`,
   `find_references`, `get_method_call_hierarchy`, `get_outgoing_structures`,
   or `get_symbol_info`.
5. `get_method_call_hierarchy` scans only one project's `<project>/src`.
   Inspect each relevant base/extension project separately and name any project
   left unsearched. Treat `get_outgoing_structures` as a heuristic lower bound,
   even when its response is not marked partial.
6. Treat every single-hop/depth-1 result and structured-output analysis as a
   lower bound. Inspect bounded source and relevant projects before claiming a
   complete contract.
7. Start with counts, file lists, filters, or method/range reads; request full
   payloads or more pages only when needed. Preserve truncation and cursor
   evidence in the result.
8. Use `get_platform_documentation` when the conclusion depends on platform
   behavior rather than project code. If a material task-supplied source handle
   remains insufficient after the bounded expansion allowed by `COMMON.md`,
   report `SOURCE_GAP` rather than roaming.

## Authority rule

Do not write code, metadata, runtime data, or Git state. Request a new task
boundary before any implementation or runtime experiment.

## Stop rule

Stop when the exact target cannot be resolved, required project/source-packet
evidence is unavailable, or the conclusion needs unauthorized runtime proof.

## Completion signal

Return exact targets, the evidenced call/data flow, relevant dependencies and
exceptions, comparison to supplied standard/donor handles when applicable,
impact classification, partial-result caveats, unknowns, and the smallest
useful next validation step.
