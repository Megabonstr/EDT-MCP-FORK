/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ditrix.edt.mcp.server.protocol.jsonrpc.ToolAnnotations;
import com.ditrix.edt.mcp.server.tools.IMcpTool.ResponseType;
import com.ditrix.edt.mcp.server.utils.CodeReviewReportMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Focused contract and representative-result mapping test for {@link CodeReviewTool}. */
public class CodeReviewToolTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void testRepresentativeReportMapsSummaryAndOriginalFinding()
        throws Exception
    {
        CodeReviewTool tool = new CodeReviewTool();
        assertEquals("code_review", tool.getName()); //$NON-NLS-1$
        assertEquals(ResponseType.MARKDOWN, tool.getResponseType());
        assertTrue(tool.getDescription().contains("get_tool_guide('code_review')")); //$NON-NLS-1$
        JsonObject schema = JsonParser.parseString(tool.getInputSchema()).getAsJsonObject();
        JsonObject properties = schema.getAsJsonObject("properties"); //$NON-NLS-1$
        assertTrue(properties.has("projectName")); //$NON-NLS-1$
        assertTrue(properties.has("modulePaths")); //$NON-NLS-1$
        assertTrue(properties.has("wholeProject")); //$NON-NLS-1$
        assertTrue(properties.has("waitSeconds")); //$NON-NLS-1$
        ToolAnnotations annotations = tool.getAnnotations();
        assertEquals(Boolean.TRUE, annotations.getReadOnlyHint());
        assertEquals(Boolean.TRUE, annotations.getIdempotentHint());

        String originalModule = "CommonModules/ReviewTarget/Module.bsl"; //$NON-NLS-1$
        Path analysisRoot = Paths.get(System.getProperty("java.io.tmpdir"), //$NON-NLS-1$
            "edt-mcp-code-review-test").toAbsolutePath().normalize(); //$NON-NLS-1$
        Path stagedModule = analysisRoot.resolve(originalModule.replace('/',
            java.io.File.separatorChar));
        Map<String, String> stagedPaths = new LinkedHashMap<>();
        stagedPaths.put(CodeReviewReportMapper.normalizeAbsolute(stagedModule), originalModule);

        String rawJson = "{\"fileinfos\":[{\"path\":" //$NON-NLS-1$
            + JsonParser.parseString("\"" + stagedModule.toUri().toString() + "\"") //$NON-NLS-1$ //$NON-NLS-2$
                .toString()
            + ",\"diagnostics\":[{\"range\":{\"start\":{\"line\":6," //$NON-NLS-1$
            + "\"character\":4},\"end\":{\"line\":6,\"character\":12}}," //$NON-NLS-1$
            + "\"severity\":\"Warning\",\"code\":\"MagicNumber\"," //$NON-NLS-1$
            + "\"message\":\"Replace the magic number with a named constant.\"," //$NON-NLS-1$
            + "\"codeDescription\":{\"href\":\"https://example.invalid/MagicNumber\"}}]}]}"; //$NON-NLS-1$

        Map<String, Object> result = CodeReviewReportMapper.map(rawJson,
            "1.4.0.v202607091607", "TestConfiguration", false, //$NON-NLS-1$ //$NON-NLS-2$
            Arrays.asList(originalModule), analysisRoot,
            analysisRoot.resolve("original-src"), stagedPaths); //$NON-NLS-1$

        assertEquals("completed", result.get("status")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("1.4.0.v202607091607", result.get("pluginVersion")); //$NON-NLS-1$ //$NON-NLS-2$
        Map<String, Object> summary = (Map<String, Object>)result.get("summary"); //$NON-NLS-1$
        assertEquals(Integer.valueOf(0), summary.get("errors")); //$NON-NLS-1$
        assertEquals(Integer.valueOf(1), summary.get("warnings")); //$NON-NLS-1$
        assertEquals(Integer.valueOf(0), summary.get("information")); //$NON-NLS-1$
        assertEquals(Integer.valueOf(1), summary.get("total")); //$NON-NLS-1$

        List<Map<String, Object>> findings =
            (List<Map<String, Object>>)result.get("findings"); //$NON-NLS-1$
        assertEquals(1, findings.size());
        Map<String, Object> finding = findings.get(0);
        assertEquals(originalModule, finding.get("modulePath")); //$NON-NLS-1$
        assertEquals(Integer.valueOf(7), finding.get("line")); //$NON-NLS-1$
        assertEquals(Integer.valueOf(5), finding.get("column")); //$NON-NLS-1$
        assertEquals("Warning", finding.get("severity")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("MagicNumber", finding.get("code")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Replace the magic number with a named constant.", //$NON-NLS-1$
            finding.get("message")); //$NON-NLS-1$
        assertEquals("https://example.invalid/MagicNumber", //$NON-NLS-1$
            finding.get("url")); //$NON-NLS-1$
    }
}
