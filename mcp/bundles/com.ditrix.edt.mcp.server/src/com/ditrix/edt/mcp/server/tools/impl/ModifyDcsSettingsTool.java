/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.McpKeys;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Focused entry point for native DCS settings authoring. Persistence is intentionally delegated to
 * {@link ModifyMetadataTool}'s established owner/DCS transaction and dual-export path; this adapter only
 * gives the advanced settings contract a discoverable tool of its own.
 */
public final class ModifyDcsSettingsTool implements IMcpTool
{
    public static final String NAME = "modify_dcs_settings"; //$NON-NLS-1$
    private static final String KEY_OWNER_FQN = "ownerFqn"; //$NON-NLS-1$
    private static final String KEY_SETTINGS = "settings"; //$NON-NLS-1$

    @FunctionalInterface
    interface DcsWriteDelegate
    {
        String execute(Map<String, String> params);
    }

    private final DcsWriteDelegate delegate;

    public ModifyDcsSettingsTool()
    {
        this(params -> new ModifyMetadataTool().execute(params));
    }

    ModifyDcsSettingsTool(DcsWriteDelegate delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Author a DCS default/variant settings tree through EDT's native model: total fields, " //$NON-NLS-1$
            + "selection, filters, sorting, nested groups, charts, conditional appearance and user-setting " //$NON-NLS-1$
            + "exposure. Supports surgical mutation with normalized before/after read-back. Parameters and " //$NON-NLS-1$
            + "examples: get_tool_guide('modify_dcs_settings')."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .stringProperty(McpKeys.PROJECT_NAME, "EDT project name (required).", true) //$NON-NLS-1$
            .stringProperty(KEY_OWNER_FQN,
                "DCS owner FQN: Report, ExternalReport, or ExternalDataProcessor (required; " //$NON-NLS-1$
                    + "external owners must already contain a DCS template).", true) //$NON-NLS-1$
            .objectProperty(KEY_SETTINGS,
                "Advanced settings specification (required). target='default' or target='variant' with " //$NON-NLS-1$
                    + "variantName; omitted sections are preserved. See get_tool_guide for the full shape.", //$NON-NLS-1$
                true)
            .build();
    }

    @Override
    public String getOutputSchema()
    {
        return JsonSchemaBuilder.object()
            .booleanProperty("success", "Whether the write succeeded", true) //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty(McpKeys.ACTION, "Applied action") //$NON-NLS-1$
            .stringProperty("fqn", "Normalized DCS owner FQN") //$NON-NLS-1$ //$NON-NLS-2$
            .objectProperty("dcs", "Applied counts plus settingsBefore/settingsAfter/changedSettingsPaths") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("persisted", "Whether EDT exported the owner and .dcs resource") //$NON-NLS-1$ //$NON-NLS-2$
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
        String projectName = JsonUtils.extractStringArgument(params, McpKeys.PROJECT_NAME);
        String ownerFqn = JsonUtils.extractStringArgument(params, KEY_OWNER_FQN);
        String rawSettings = params.get(KEY_SETTINGS);

        String error = JsonUtils.requireArguments(params, McpKeys.PROJECT_NAME, KEY_OWNER_FQN,
            KEY_SETTINGS);
        if (error != null)
        {
            return error;
        }
        JsonElement settings;
        try
        {
            settings = JsonParser.parseString(rawSettings);
        }
        catch (RuntimeException e)
        {
            return ToolResult.error("settings must be a valid JSON object.").toJson(); //$NON-NLS-1$
        }
        if (!settings.isJsonObject())
        {
            return ToolResult.error("settings must be a JSON object.").toJson(); //$NON-NLS-1$
        }

        JsonObject dcs = new JsonObject();
        dcs.add(KEY_SETTINGS, settings.getAsJsonObject());
        Map<String, String> delegated = new LinkedHashMap<>();
        delegated.put(McpKeys.PROJECT_NAME, projectName);
        delegated.put("fqn", ownerFqn); //$NON-NLS-1$
        delegated.put("dcs", dcs.toString()); //$NON-NLS-1$
        return delegate.execute(delegated);
    }
}
