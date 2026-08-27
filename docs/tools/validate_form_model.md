# validate_form_model

Validate managed-form IDs, names, bindings, handlers, attachment and command-bar invariants. Full parameters and examples: call get_tool_guide('validate_form_model').

## Parameters
| Parameter | Required | Type | Description |
| --- | --- | --- | --- |
| projectName | yes | string | EDT project name. |
| formFqn | yes | string | Form FQN: 'Type.Name.Form.FormName' or 'CommonForm.Name'. |

## Guide
## Parameter details

`formFqn` addresses the form itself: `Type.Name.Form.FormName` or `CommonForm.Name`. The tool resolves
the descriptor and reads its editable content inside a BM read transaction.

The normalized findings cover:

- invalid and duplicate item, attribute and command names/IDs;
- invalid or duplicate parameter names (form parameters have an independent namespace and no ID);
- unresolved or empty data paths;
- missing type-required `extInfo`;
- buttons that reference missing form commands;
- handlers with missing event metadata or BSL procedure names;
- detached content or a missing content top-object FQN;
- a missing root auto command bar or an `id` other than the platform `-1` sentinel.

The tool is read-only and resolves forms in configuration, extension, and external-objects projects.
`valid=true` means this structural pass found nothing; it does not replace EDT's project checks or a
runtime UI test.

## Example

`{projectName:'P', formFqn:'Catalog.Products.Form.ItemForm'}`

---
*Generated-equivalent reference assembled from the tool source and guide. Regenerate it from the live development build with `docs/generate_tool_docs.py` before opening the PR.*
