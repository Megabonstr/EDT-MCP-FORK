/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Contract and pre-workbench validation tests for {@link ValidateFormModelTool}. */
public class ValidateFormModelToolTest
{
    @Test
    public void testContract()
    {
        ValidateFormModelTool tool = new ValidateFormModelTool();
        assertEquals("validate_form_model", tool.getName()); //$NON-NLS-1$
        assertEquals(IMcpTool.ResponseType.JSON, tool.getResponseType());
        assertTrue(tool.getDescription().contains("get_tool_guide('validate_form_model')")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"formFqn\"")); //$NON-NLS-1$
        assertNotNull(tool.getOutputSchema());
    }

    @Test
    public void testInvalidFormFqnReturnsBeforeWorkbenchAccess()
    {
        String result = new ValidateFormModelTool().execute(Map.of(
            "projectName", "P", "formFqn", "Catalog.Products")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertTrue(result.contains("Invalid formFqn")); //$NON-NLS-1$
    }

    @Test
    public void testOutputSchemaRequiresOnlyTheUniversalSuccessEnvelope()
    {
        JsonObject schema = JsonParser.parseString(new ValidateFormModelTool().getOutputSchema()).getAsJsonObject();
        JsonArray required = schema.getAsJsonArray("required"); //$NON-NLS-1$
        assertEquals(1, required.size());
        assertEquals("success", required.get(0).getAsString()); //$NON-NLS-1$
    }
}
