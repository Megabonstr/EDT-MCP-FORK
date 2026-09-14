---
name: edt-mcp-project-forms
description: Research and safely change managed 1C forms through EDT-MCP, including parameters, bindings, handlers, dynamic lists, and visual verification. Structured operations by default.
---

# EDT-MCP managed forms

## Purpose and trigger

Use this skill to inspect or change a managed form's structure, data,
commands, handlers, dynamic lists, or visible layout.

## Operating rule

Read and apply [the common operating rules](../COMMON.md) before this workflow.

## Task boundary

Work on the exact project and form FQN through structured EDT-MCP operations.
Route code-only fixes to `edt-mcp-project-local-fix` and DCS changes to
`edt-mcp-project-query-dcs`. Direct `Form.form` editing is a last resort only
when the installed project rules describe the procedure, structured operations
cannot represent the change, and the user explicitly authorizes the risk.

## Primary workflow

1. Read the form with `get_metadata_details` and inspect only relevant handlers
   with `get_module_structure` and `read_method_source`.
2. Consult the current guide, then use the narrowest supported
   `create_metadata`, `modify_metadata`, or `delete_metadata` operation.
3. After creating or modifying a form, run `validate_form_model` for that exact
   form. It checks the current in-memory model immediately; read its findings,
   fix material structural defects, and validate again. Do not substitute stale
   project markers for this form-specific check.
4. Re-read the form and verify the requested ownership, binding, data-path,
   command, and handler relationships; validate changed query text with
   `validate_query` when applicable.
5. For a dynamic list, verify its owning form attribute, main table or custom
   query, selected fields, visible item data paths, settings/filter handlers,
   and refresh/requery behavior. Validate changed query text before writing and
   ensure every visible `List.Field` path resolves afterward.
6. Use `get_form_layout_snapshot` when layout structure matters and
   `get_form_screenshot` only when rendered appearance is acceptance evidence.
   When taking a post-change screenshot, use `refresh=true`. Blank output most
   likely means EDT lacks `-DnativeFormBufferedLayoutRender=true`; report visual
   evidence as unavailable rather than treating the change as failed.
7. Run targeted project validation and an authorized runtime UI scenario only
   when those additional evidence layers are needed. `validate_form_model` and
   `get_project_errors` are complementary: the former is current form structure,
   the latter is EDT's previously computed project markers.

## Authority rule

Do not broaden a form mutation, leave dangling bindings, activate UI, launch a
client, or change runtime data without the authority required for that effect.

## Stop rule

Stop when the form target is ambiguous, the structured writer cannot represent
the requested change, required references cannot be preserved, or necessary
visual/runtime evidence is unavailable.

## Completion signal

Return the exact form target, confirmed structural/source diff,
`validate_form_model` result, other targeted validation, requested layout or
runtime evidence, and explicit gaps. A model read or screenshot proves only the
state it actually reports, not user interaction.
