/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.McpKeys;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.FormElementWriter;
import com.ditrix.edt.mcp.server.utils.FormModelValidator;
import com.ditrix.edt.mcp.server.utils.FormValidationException;
import com.ditrix.edt.mcp.server.utils.MetadataTypeUtils;
import com.ditrix.edt.mcp.server.utils.ProjectContext;

/** Validates one editable managed-form model inside a BM read transaction. */
public final class ValidateFormModelTool implements IMcpTool
{
    public static final String NAME = "validate_form_model"; //$NON-NLS-1$

    private static final String KEY_FORM_FQN = "formFqn"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Validate managed-form IDs, names, bindings, handlers, attachment and command-bar " //$NON-NLS-1$
            + "invariants. Full parameters and examples: call get_tool_guide('validate_form_model')."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .stringProperty(McpKeys.PROJECT_NAME, "EDT project name.", true) //$NON-NLS-1$
            .stringProperty(KEY_FORM_FQN,
                "Form FQN: 'Type.Name.Form.FormName' or 'CommonForm.Name'.", true) //$NON-NLS-1$
            .build();
    }

    @Override
    public String getOutputSchema()
    {
        return JsonSchemaBuilder.object()
            .booleanProperty("success", "Whether validation executed.", true) //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("valid", "Whether the form has no structural findings.", true) //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("formFqn", "Normalized form FQN.") //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("findingCount", "Number of structural findings.") //$NON-NLS-1$ //$NON-NLS-2$
            .objectArrayProperty("findings", "Findings as {code, severity, path, message}.") //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty(McpKeys.MESSAGE, "Human-readable validation summary.") //$NON-NLS-1$
            .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String required = JsonUtils.requireArguments(params, McpKeys.PROJECT_NAME, KEY_FORM_FQN);
        if (required != null)
        {
            return required;
        }
        String normalized = MetadataTypeUtils.normalizeFqn(params.get(KEY_FORM_FQN));
        String formPath = FormElementWriter.parseFormPath(normalized);
        if (formPath == null)
        {
            return ToolResult.error("Invalid formFqn '" + params.get(KEY_FORM_FQN) //$NON-NLS-1$
                + "': expected 'Type.Name.Form.FormName' or 'CommonForm.Name'.").toJson(); //$NON-NLS-1$
        }

        AtomicReference<String> result = new AtomicReference<>();
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.syncExec(() -> result.set(validateOnUiThread(params.get(McpKeys.PROJECT_NAME),
            normalized, formPath)));
        return result.get();
    }

    private static String validateOnUiThread(String projectName, String normalized, String formPath)
    {
        try
        {
            ProjectContext.ConfigurationResult resolved = ProjectContext.resolveMetadataRoot(projectName);
            if (!resolved.ok())
            {
                return resolved.errorJson();
            }
            FormElementWriter.FormEditContext context =
                FormElementWriter.resolveForEdit(resolved.project(), resolved.scope(), formPath,
                    "Form not found: " + normalized); //$NON-NLS-1$
            FormModelValidator.Result validation = FormElementWriter.readEditableForm(context,
                "ValidateFormModel", (formModel, tx) -> FormModelValidator.validate(formModel)); //$NON-NLS-1$
            return ToolResult.success()
                .put("valid", validation.isValid()) //$NON-NLS-1$
                .put("formFqn", normalized) //$NON-NLS-1$
                .put("findingCount", validation.findings().size()) //$NON-NLS-1$
                .put("findings", validation.findingsJson()) //$NON-NLS-1$
                .put(McpKeys.MESSAGE, validation.isValid()
                    ? "Form model is structurally valid." //$NON-NLS-1$
                    : "Form model has " + validation.findings().size() + " structural finding(s).") //$NON-NLS-1$ //$NON-NLS-2$
                .toJson();
        }
        catch (Exception e)
        {
            String ready = FormValidationException.jsonOf(e);
            if (ready != null)
            {
                return ready;
            }
            Activator.logError("Error validating form model", e); //$NON-NLS-1$
            return ToolResult.error("Failed to validate form model: " + e.getMessage()).toJson(); //$NON-NLS-1$
        }
    }
}
