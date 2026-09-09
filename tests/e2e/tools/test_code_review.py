"""Focused optional-plugin smoke for code_review."""

import re

from harness import (
    PROJECT,
    assert_error_quality,
    assert_no_diff,
    assert_ok,
    call,
    e2e_test,
)


@e2e_test(tool="code_review", kind="read")
def test_explicit_module_returns_report_or_clear_optional_plugin_sentinel():
    result = call("code_review", {
        "projectName": PROJECT,
        "modulePaths": ["CommonModules/OK/Module.bsl"],
        "waitSeconds": 45,
    })
    if result.is_error:
        assert_error_quality(
            result.error_text(),
            names=["MCP:RSV Code Review"],
            suggests=["Install", "1.4.0"],
        )
        assert_no_diff()
        return

    assert_ok(result, "start explicit-module Code Review")
    text = result.text
    for _ in range(12):
        if "| status | running |" not in text:
            break
        match = re.search(r"(?m)^\| jobId \| ([^|]+) \|$", text)
        if not match:
            raise AssertionError("running code_review response did not contain jobId")
        poll = call("get_job_status", {
            "jobId": match.group(1).strip(),
            "waitSeconds": 45,
        })
        assert_ok(poll, "poll Code Review background job")
        text = poll.text

    if "| status | done |" not in text:
        raise AssertionError("code_review did not reach done state:\n" + text)
    for expected in ("pluginVersion", "TestConfiguration", "summary", "findings"):
        if expected not in text:
            raise AssertionError("code_review result is missing %r:\n%s" % (expected, text))
    assert_no_diff()
