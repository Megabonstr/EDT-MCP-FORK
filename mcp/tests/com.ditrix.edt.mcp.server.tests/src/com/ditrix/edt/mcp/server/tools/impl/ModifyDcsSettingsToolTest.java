/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.ditrix.edt.mcp.server.protocol.McpKeys;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests the focused tool's validation and exact delegation to the shared atomic DCS path. */
public class ModifyDcsSettingsToolTest
{
    @Test
    public void testIdentitySchemaAndGuide()
    {
        ModifyDcsSettingsTool tool = new ModifyDcsSettingsTool(params -> "{}"); //$NON-NLS-1$
        assertEquals("modify_dcs_settings", tool.getName()); //$NON-NLS-1$
        assertTrue(tool.getDescription().contains("native model")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("ownerFqn")); //$NON-NLS-1$
        assertTrue(tool.getOutputSchema().contains("success")); //$NON-NLS-1$
        assertFalse(tool.getGuide().isEmpty());
    }

    @Test
    public void testDelegatesAsModifyMetadataDcsPayload()
    {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        ModifyDcsSettingsTool tool = new ModifyDcsSettingsTool(params -> {
            captured.set(new LinkedHashMap<>(params));
            return "{\"success\":true}"; //$NON-NLS-1$
        });
        Map<String, String> params = new LinkedHashMap<>();
        params.put(McpKeys.PROJECT_NAME, "P"); //$NON-NLS-1$
        params.put("ownerFqn", "Report.Sales"); //$NON-NLS-1$ //$NON-NLS-2$
        params.put("settings", "{\"target\":\"default\",\"selection\":[{\"field\":\"Amount\"}]}"); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals("{\"success\":true}", tool.execute(params)); //$NON-NLS-1$
        assertEquals("P", captured.get().get(McpKeys.PROJECT_NAME)); //$NON-NLS-1$
        assertEquals("Report.Sales", captured.get().get("fqn")); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject dcs = JsonParser.parseString(captured.get().get("dcs")).getAsJsonObject(); //$NON-NLS-1$
        assertTrue(dcs.has("settings")); //$NON-NLS-1$
        assertFalse(dcs.has("dataSets")); //$NON-NLS-1$
    }

    @Test
    public void testMalformedSettingsNeverDelegates()
    {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        ModifyDcsSettingsTool tool = new ModifyDcsSettingsTool(params -> {
            called.set(true);
            return "{}"; //$NON-NLS-1$
        });
        Map<String, String> params = new LinkedHashMap<>();
        params.put(McpKeys.PROJECT_NAME, "P"); //$NON-NLS-1$
        params.put("ownerFqn", "Report.Sales"); //$NON-NLS-1$ //$NON-NLS-2$
        params.put("settings", "[]"); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(tool.execute(params).contains("settings must be a JSON object")); //$NON-NLS-1$
        assertFalse(called.get());
    }

    @Test
    public void testMissingRequiredArgumentNeverDelegates()
    {
        ModifyDcsSettingsTool tool = new ModifyDcsSettingsTool(params -> {
            throw new AssertionError("must not delegate"); //$NON-NLS-1$
        });
        assertTrue(tool.execute(Map.of()).contains("projectName")); //$NON-NLS-1$
    }
}
