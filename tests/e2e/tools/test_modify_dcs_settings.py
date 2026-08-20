"""Live persistence and external-scope coverage for modify_dcs_settings."""

from harness import (
    EXT_OBJECTS_PROJECT, EXT_OBJECTS_REL, PROJECT, assert_error,
    assert_error_quality,
    assert_no_diff, assert_no_diff_rel, assert_ok, call, e2e_test,
    assert_tree_unchanged, poll_diff_contains, tree_snapshot,
    wait_for_project_ready,
)


MAIN_DCS_TEMPLATE = "ОсновнаяСхемаКомпоновкиДанных"


def _seed_report(name):
    fqn = "Report." + name
    created = call("create_metadata", {"projectName": PROJECT, "fqn": fqn})
    assert_ok(created, "seed a report for advanced DCS settings")
    wait_for_project_ready()
    return fqn


@e2e_test(tool="modify_dcs_settings", kind="write-metadata")
def test_default_settings_persist_and_return_normalized_read_back():
    fqn = _seed_report("E2EAdvancedDcsSettings")
    marker = "E2EAdvancedAmount"
    result = call("modify_dcs_settings", {
        "projectName": PROJECT,
        "ownerFqn": "Отчет.E2EAdvancedDcsSettings",
        "settings": {
            "target": "default",
            "selection": [{"field": marker}],
            "order": [{"field": marker, "direction": "DESC"}],
        },
    })
    assert_ok(result, "write advanced default DCS settings")
    structured = result.structured or {}
    applied = structured.get("dcs") or {}
    if "settingsBefore" not in applied or "settingsAfter" not in applied:
        raise AssertionError("settings write must return normalized before/after read-back: %r" % structured)
    if "/selection" not in applied.get("changedSettingsPaths", []):
        raise AssertionError("changed top-level paths must name selection: %r" % applied)
    poll_diff_contains(marker, ctx="advanced settings must reach the report's .dcs on disk")
    wait_for_project_ready()
    reread = call("get_metadata_details", {
        "projectName": PROJECT,
        "objectFqns": [fqn + ".Template." + MAIN_DCS_TEMPLATE],
    })
    assert_ok(reread, "independently re-read the persisted DCS through the metadata reader")
    if marker not in (reread.text or ""):
        raise AssertionError("independent DCS read-back must contain the selected field: %r"
                             % (reread.text or ""))


@e2e_test(tool="modify_dcs_settings", kind="write-metadata")
def test_invalid_target_enum_and_mutation_are_refused_before_model_changes():
    fqn = _seed_report("E2EInvalidAdvancedDcsSettings")
    before = tree_snapshot()
    cases = [
        ({"target": "bogus", "selection": [{"field": "Amount"}]},
         ["default", "variant"], "invalid settings target"),
        ({"target": "variant", "selection": [{"field": "Amount"}]},
         ["variantName"], "missing variant name"),
        ({"target": "default", "output": {"ResourcePlacement": "DIAGONALLY"}},
         ["ResourcePlacement", "expected one of"], "invalid native output enum"),
        ({"target": "default", "mutation": {"section": "order", "action": "upsert"}},
         ["mutation.selector"], "incomplete surgical mutation"),
    ]
    for settings, expected, ctx in cases:
        result = call("modify_dcs_settings", {
            "projectName": PROJECT,
            "ownerFqn": fqn,
            "settings": settings,
        })
        assert_error(result, ctx)
        assert_error_quality(result.error_text(), suggests=expected, ctx=ctx)
        assert_tree_unchanged(before, "rejected DCS settings must not mutate the seeded report")


@e2e_test(tool="modify_dcs_settings", kind="write-metadata")
def test_external_owner_resolves_in_its_own_project_before_template_check():
    result = call("modify_dcs_settings", {
        "projectName": EXT_OBJECTS_PROJECT,
        "ownerFqn": "ExternalDataProcessor.ExtProc",
        "settings": {"target": "default", "selection": [{"field": "Amount"}]},
    })
    assert_error(result, "external object without a DCS template")
    text = result.text or ""
    if "DCS template not found" not in text or "ExternalDataProcessor.ExtProc" not in text:
        raise AssertionError("the external owner must resolve before the existing-template refusal: %r" % text)
    if "Node not found" in text or "Report.<Name>" in text:
        raise AssertionError("the call must not fall back to the base-configuration/report-only resolver: %r" % text)
    assert_no_diff("a refused external DCS write must not touch the base fixture")
    assert_no_diff_rel(EXT_OBJECTS_REL,
                       "a refused external DCS write must not touch the external fixture")
