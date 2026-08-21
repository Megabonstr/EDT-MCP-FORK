## Guide

Runs the separately installed MCP:RSV Code Review plugin without editor selection, UI commands,
Problems markers, or result views. The plugin's current diagnostic preferences are used unchanged.

## Parameter details

- `projectName` is the exact open EDT project name.
- Choose exactly one scope:
  - `modulePaths`: one or more `.bsl` paths relative to the project's `src` folder. Discover them
    with `list_modules`. Absolute paths, `src/` prefixes, traversal, and external files are rejected.
  - `wholeProject=true`: an explicit scan of every BSL module under `src`. Omitting both scope
    modes never widens the request to the whole project.
- `waitSeconds` controls only how long the start call waits, from 0 to 45 seconds. The analysis
  remains a background job and is polled with `get_job_status` using the returned `jobId`.

Only one Code Review analysis may run at a time. Once the Code Review runner starts, v1 marks the
job committed because the plugin does not expose a safe process-cancellation handle. `cancel_job`
therefore reports that the running analysis was not cancelled. The plugin's own engine limit is 10
minutes.

## Examples

Review selected modules:

```json
{
  "projectName": "MyProject",
  "modulePaths": [
    "CommonModules/MyModule/Module.bsl",
    "Documents/Order/ManagerModule.bsl"
  ],
  "waitSeconds": 5
}
```

Start an explicit whole-project scan and return immediately:

```json
{
  "projectName": "MyProject",
  "wholeProject": true,
  "waitSeconds": 0
}
```

Poll the returned job:

```json
{
  "jobId": "<jobId>",
  "waitSeconds": 10
}
```

The terminal job result contains the detected plugin version, resolved project and scope, summary
counts, and findings with the original project-relative module path, 1-based line and column,
severity, diagnostic code, message, and optional description URL.
