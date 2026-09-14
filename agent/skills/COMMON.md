# Common operating rules

Apply these rules before every business-project skill:

- Use the current MCP schema/help and repository tool documentation as the
  authority for parameters, limits, side effects, identifiers, errors, and
  recovery. Do not invent undocumented behavior.
- If the exact task supplies a **source packet** (current project source,
  standard implementation donor, coding/metadata standard, or pinned reference
  guide), consume those exact handles before inventing structure or behavior.
  Treat them as design/review evidence only; they do not expand mutation
  authority and do not override current project source, live tool help, or
  runtime truth.
- Keep source reads narrow. Start from the exact method/section/check/guide
  handle supplied by the task. If it is insufficient, expand only to the
  immediately owning section/file or one narrow search in the named source.
  Report `SOURCE_GAP` instead of roaming adjacent repositories or large donor
  trees.
- On ambiguity, an unexpected state, unclear target or ownership, or a
  user-affecting/destructive action, stop and consult the authoritative help.
  Ask the user when safe continuation still needs permission or a decision.
- Keep the exact authorized project, object, application, repository, and file
  targets in scope. Discovery and read-only evidence never authorize mutation.
- Preserve returned job, launch, breakpoint, frame, hash, cursor, and preview
  identifiers; address only that retained operation and never rerun it merely
  to discover status.
- Report only tool-confirmed results, partial/truncated evidence, side effects,
  cleanup state, and anything that remains unproved.

If installed project `rules/` and a task skill appear to conflict, stop and
resolve the conflict instead of silently choosing the more permissive route.
