"""Live read-back coverage for validate_form_model."""

from harness import (
    EXT_OBJECTS_PROJECT, EXT_OBJECTS_REL, PROJECT, assert_error,
    assert_error_quality,
    assert_no_diff, assert_no_diff_rel, assert_ok, call, e2e_test,
)


def _assert_validation_shape(result, expected_fqn, expected_valid=None):
    structured = result.structured
    if not isinstance(structured, dict):
        raise AssertionError("validate_form_model must return structured JSON: %r" % structured)
    if structured.get("formFqn") != expected_fqn:
        raise AssertionError("validation must return the normalized form FQN: %r" % structured)
    if not isinstance(structured.get("valid"), bool):
        raise AssertionError("validation must return a boolean valid flag: %r" % structured)
    if not isinstance(structured.get("findingCount"), int):
        raise AssertionError("validation must return an integer findingCount: %r" % structured)
    if not isinstance(structured.get("findings"), list):
        raise AssertionError("validation must return the findings array: %r" % structured)
    if structured["findingCount"] != len(structured["findings"]):
        raise AssertionError("findingCount must match the findings array: %r" % structured)
    if expected_valid is not None and structured["valid"] is not expected_valid:
        raise AssertionError("validation returned the wrong semantic result: %r" % structured)
    if expected_valid is True and (structured["findingCount"] != 0 or structured["findings"]):
        raise AssertionError("a known-valid fixture must have no findings: %r" % structured)


@e2e_test(tool="validate_form_model", kind="read")
def test_existing_fixture_form_is_structurally_checked():
    result = call("validate_form_model", {
        "projectName": PROJECT,
        "formFqn": "Catalog.Catalog.Form.ItemForm",
    })
    assert_ok(result, "validate an existing managed form")
    _assert_validation_shape(result, "Catalog.Catalog.Form.ItemForm", expected_valid=True)
    assert_no_diff("form validation must be read-only")


@e2e_test(tool="validate_form_model", kind="read")
def test_russian_type_token_is_normalized_before_validation():
    result = call("validate_form_model", {
        "projectName": PROJECT,
        "formFqn": "Справочник.Catalog.Form.ItemForm",
    })
    assert_ok(result, "validate a managed form addressed with a Russian type token")
    _assert_validation_shape(result, "Catalog.Catalog.Form.ItemForm", expected_valid=True)
    assert_no_diff("bilingual form validation must be read-only")


@e2e_test(tool="validate_form_model", kind="read")
def test_invalid_form_shape_is_rejected():
    result = call("validate_form_model", {
        "projectName": PROJECT,
        "formFqn": "Catalog.Catalog",
    })
    assert_error(result, "invalid form FQN")
    assert_error_quality(result.error_text(), names=["Catalog.Catalog"],
                         suggests=["Type.Name.Form.FormName", "CommonForm.Name"],
                         ctx="invalid form FQN must explain the accepted shapes")
    assert_no_diff("rejected validation must be read-only")


@e2e_test(tool="validate_form_model", kind="read")
def test_external_object_form_uses_its_own_project_root():
    result = call("validate_form_model", {
        "projectName": EXT_OBJECTS_PROJECT,
        "formFqn": "ExternalDataProcessor.ExtProc.Form.MainForm",
    })
    assert_ok(result, "validate an external data processor form")
    _assert_validation_shape(result, "ExternalDataProcessor.ExtProc.Form.MainForm", expected_valid=True)
    assert_no_diff("external form validation must not touch the base fixture")
    assert_no_diff_rel(EXT_OBJECTS_REL,
                       "external form validation must not touch the external fixture")
