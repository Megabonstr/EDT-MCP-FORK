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
            "totalFields": [{
                "dataPath": marker,
                "expression": "SUM(%s)" % marker,
                "groups": ["E2EWarehouse"],
            }],
            "selection": [{
                "kind": "group",
                "field": "E2EWarehouse",
                "items": [{"field": marker}],
            }],
            "filter": [{
                "kind": "group",
                "groupType": "AND_GROUP",
                "items": [{"left": "E2EPosted", "comparison": "EQUAL", "right": True}],
            }],
            "order": [{"field": marker, "direction": "DESC"}],
            "structure": [{
                "kind": "group",
                "id": "e2e-stable-group",
                "name": "E2EWarehouse",
                "groupFields": [{"field": "E2EWarehouse", "groupType": "ITEMS"}],
                "items": [{
                    "kind": "chart",
                    "id": "e2e-chart",
                    "points": [{
                        "id": "e2e-point",
                        "groupFields": [{"field": "E2EWarehouse"}],
                    }],
                    "series": [{
                        "id": "e2e-series",
                        "groupFields": [{"field": marker}],
                    }],
                }],
            }],
            "conditionalAppearance": [{
                "fields": [marker],
                "filter": [{"left": marker, "comparison": "GREATER", "right": 0}],
                "userSetting": {"id": "e2e-appearance", "viewMode": "QUICK_ACCESS"},
            }],
            "userSettings": {
                "id": "e2e-settings",
                "viewMode": "NORMAL",
                "selection": {"id": "e2e-fields", "viewMode": "QUICK_ACCESS"},
            },
            "output": {
                "ResourcePlacement": "VERTICALLY",
                "ChartType.ResourcesPlacement": "SERIES",
            },
        },
    })
    assert_ok(result, "write advanced default DCS settings")
    structured = result.structured or {}
    applied = structured.get("dcs") or {}
    if "settingsBefore" not in applied or "settingsAfter" not in applied:
        raise AssertionError("settings write must return normalized before/after read-back: %r" % structured)
    expected_paths = {
        "/totalFields", "/selection", "/filter", "/order", "/structure",
        "/conditionalAppearance", "/userSettings", "/output",
    }
    actual_paths = set(applied.get("changedSettingsPaths", []))
    if not expected_paths.issubset(actual_paths):
        raise AssertionError("changed top-level paths are incomplete: %r" % applied)
    after = applied.get("settingsAfter") or {}
    if after.get("output") != {
            "ResourcePlacement": "VERTICALLY",
            "ChartType.ResourcesPlacement": "SERIES"}:
        raise AssertionError("native output enums must round-trip as literals: %r" % after)
    structure = after.get("structure") or []
    if not structure or structure[0].get("id") != "e2e-stable-group":
        raise AssertionError("nested structure and stable IDs must round-trip: %r" % after)
    appearances = after.get("conditionalAppearance") or []
    if not appearances or (appearances[0].get("userSetting") or {}).get("id") != "e2e-appearance":
        raise AssertionError("conditional appearance exposure must round-trip: %r" % after)
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


@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_canonical_modify_metadata_creates_and_persists_variant():
    fqn = _seed_report("E2EAdvancedDcsVariant")
    marker = "E2EVariantAmount"
    result = call("modify_metadata", {
        "projectName": PROJECT,
        "fqn": fqn,
        "dcs": {
            "settings": {
                "target": "variant",
                "variantName": "E2EByWarehouse",
                "variantPresentation": "E2E by warehouse",
                "selection": [{"field": marker}],
                "order": [{"field": marker, "direction": "ASC"}],
            },
        },
    })
    assert_ok(result, "write an advanced DCS variant through canonical modify_metadata")
    applied = (result.structured or {}).get("dcs") or {}
    after = applied.get("settingsAfter") or {}
    if after.get("target") != "variant" or after.get("variantName") != "E2EByWarehouse":
        raise AssertionError("canonical facade must return the authored variant: %r" % applied)
    poll_diff_contains(marker, ctx="canonical modify_metadata variant must reach the .dcs on disk")
    poll_diff_contains("E2EByWarehouse", ctx="the named DCS variant must reach the .dcs on disk")


@e2e_test(tool="modify_dcs_settings", kind="write-metadata")
def test_surgical_upsert_preserves_structure_and_selection_selector_identity():
    _seed_report("E2EAdvancedDcsSurgical")
    owner = "Report.E2EAdvancedDcsSurgical"
    initial = call("modify_dcs_settings", {
        "projectName": PROJECT,
        "ownerFqn": owner,
        "settings": {
            "target": "default",
            "selection": [{"field": "E2ESurgicalAmount"}],
            "structure": [{"kind": "group", "id": "e2e-stable-id", "name": "Before"}],
        },
    })
    assert_ok(initial, "seed stable selector identities")

    structure = call("modify_dcs_settings", {
        "projectName": PROJECT,
        "ownerFqn": owner,
        "settings": {
            "target": "default",
            "mutation": {
                "section": "structure",
                "action": "upsert",
                "selector": "e2e-stable-id",
                "value": {"kind": "group", "name": "After"},
            },
        },
    })
    assert_ok(structure, "surgically replace a structure item")
    after_structure = (((structure.structured or {}).get("dcs") or {}).get("settingsAfter") or {})
    items = after_structure.get("structure") or []
    if len(items) != 1 or items[0].get("id") != "e2e-stable-id" or items[0].get("name") != "After":
        raise AssertionError("structure upsert must preserve selector identity in place: %r" % items)

    selection = call("modify_dcs_settings", {
        "projectName": PROJECT,
        "ownerFqn": owner,
        "settings": {
            "target": "default",
            "mutation": {
                "section": "selection",
                "action": "upsert",
                "selector": "E2ESurgicalAmount",
                "value": {"field": "E2ESurgicalAmount", "title": "E2E total"},
            },
        },
    })
    assert_ok(selection, "surgically replace a selection item")
    after_selection = (((selection.structured or {}).get("dcs") or {}).get("settingsAfter") or {})
    fields = after_selection.get("selection") or []
    if len(fields) != 1 or fields[0].get("field") != "E2ESurgicalAmount":
        raise AssertionError("selection upsert must preserve selector identity without duplicates: %r" % fields)
    if fields[0].get("title") != "E2E total":
        raise AssertionError("selection upsert must apply the replacement value: %r" % fields)


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
