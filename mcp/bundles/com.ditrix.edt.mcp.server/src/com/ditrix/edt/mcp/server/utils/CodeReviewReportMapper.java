/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Maps the Code Review / BSL Language Server JSON report to the EDT-MCP result contract. */
public final class CodeReviewReportMapper
{
    private CodeReviewReportMapper()
    {
        // Utility class
    }

    /**
     * Maps one raw Code Review report, rejecting findings that cannot be attributed to the
     * requested project scope.
     *
     * @param rawJson raw {@code bsl-json.json} content returned by Code Review
     * @param pluginVersion detected Code Review bundle version
     * @param projectName resolved EDT project
     * @param wholeProject whether the request covered the complete source folder
     * @param requestedModulePaths requested source-relative module paths
     * @param analysisRoot root passed to the Code Review runner
     * @param originalSourceRoot selected project's source folder
     * @param stagedPathMap normalized staged absolute path to original module path
     * @return machine-readable terminal job result
     * @throws CodeReviewBridgeException when the report is malformed or escapes the scope
     */
    public static Map<String, Object> map(String rawJson, String pluginVersion,
        String projectName, boolean wholeProject, List<String> requestedModulePaths,
        Path analysisRoot, Path originalSourceRoot, Map<String, String> stagedPathMap)
        throws CodeReviewBridgeException
    {
        return map(List.of(rawJson), pluginVersion, projectName, wholeProject,
            requestedModulePaths, analysisRoot, originalSourceRoot, stagedPathMap);
    }

    /** Maps and combines one report per selected source directory. */
    public static Map<String, Object> map(List<String> rawReports, String pluginVersion,
        String projectName, boolean wholeProject, List<String> requestedModulePaths,
        Path analysisRoot, Path originalSourceRoot, Map<String, String> stagedPathMap)
        throws CodeReviewBridgeException
    {
        if (rawReports == null || rawReports.isEmpty())
        {
            throw new CodeReviewBridgeException("Malformed Code Review JSON: no reports returned."); //$NON-NLS-1$
        }

        List<Map<String, Object>> findings = new ArrayList<>();
        int errors = 0;
        int warnings = 0;
        int information = 0;

        for (String rawJson : rawReports)
        {
            JsonObject root = parseRoot(rawJson);
            JsonElement fileInfosElement = root.get("fileinfos"); //$NON-NLS-1$
            if (fileInfosElement == null || !fileInfosElement.isJsonArray())
            {
                throw new CodeReviewBridgeException(
                    "Malformed Code Review JSON: top-level 'fileinfos' must be an array."); //$NON-NLS-1$
            }
            JsonArray fileInfos = fileInfosElement.getAsJsonArray();
            for (JsonElement fileInfoElement : fileInfos)
            {
                if (!fileInfoElement.isJsonObject())
                {
                    throw new CodeReviewBridgeException(
                        "Malformed Code Review JSON: every fileinfos entry must be an object."); //$NON-NLS-1$
                }
                JsonObject fileInfo = fileInfoElement.getAsJsonObject();
                String reportedPath = requiredString(fileInfo, "path", "fileinfos path"); //$NON-NLS-1$ //$NON-NLS-2$
                JsonElement diagnosticsElement = fileInfo.get("diagnostics"); //$NON-NLS-1$
                if (diagnosticsElement == null || !diagnosticsElement.isJsonArray())
                {
                    throw new CodeReviewBridgeException(
                        "Malformed Code Review JSON: diagnostics for '" + reportedPath //$NON-NLS-1$
                            + "' must be an array."); //$NON-NLS-1$
                }

                Path absolutePath = toAbsolutePath(reportedPath);
                String modulePath = resolveOriginalModulePath(absolutePath, wholeProject,
                    analysisRoot, originalSourceRoot, stagedPathMap);

                for (JsonElement diagnosticElement : diagnosticsElement.getAsJsonArray())
                {
                    if (!diagnosticElement.isJsonObject())
                    {
                        throw new CodeReviewBridgeException(
                            "Malformed Code Review JSON: every diagnostic must be an object."); //$NON-NLS-1$
                    }
                    JsonObject diagnostic = diagnosticElement.getAsJsonObject();
                JsonObject range = requiredObject(diagnostic, "range", "diagnostic range"); //$NON-NLS-1$ //$NON-NLS-2$
                JsonObject start = requiredObject(range, "start", "diagnostic range start"); //$NON-NLS-1$ //$NON-NLS-2$
                int line = requiredNonNegativeInt(start, "line") + 1; //$NON-NLS-1$
                int column = requiredNonNegativeInt(start, "character") + 1; //$NON-NLS-1$
                String severity = requiredString(diagnostic, "severity", "diagnostic severity"); //$NON-NLS-1$ //$NON-NLS-2$
                String code = requiredString(diagnostic, "code", "diagnostic code"); //$NON-NLS-1$ //$NON-NLS-2$
                String message = requiredString(diagnostic, "message", "diagnostic message"); //$NON-NLS-1$ //$NON-NLS-2$

                String normalizedSeverity = severity.toLowerCase(Locale.ROOT);
                if ("error".equals(normalizedSeverity)) //$NON-NLS-1$
                {
                    errors++;
                }
                else if ("warning".equals(normalizedSeverity)) //$NON-NLS-1$
                {
                    warnings++;
                }
                else
                {
                    information++;
                }

                Map<String, Object> finding = new LinkedHashMap<>();
                finding.put("modulePath", modulePath); //$NON-NLS-1$
                finding.put("line", Integer.valueOf(line)); //$NON-NLS-1$
                finding.put("column", Integer.valueOf(column)); //$NON-NLS-1$
                finding.put("severity", severity); //$NON-NLS-1$
                finding.put("code", code); //$NON-NLS-1$
                finding.put("message", message); //$NON-NLS-1$
                String descriptionUrl = optionalDescriptionUrl(diagnostic);
                if (descriptionUrl != null)
                {
                    finding.put("url", descriptionUrl); //$NON-NLS-1$
                }
                    findings.add(finding);
                }
            }
        }

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("mode", wholeProject ? "wholeProject" : "modules"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (!wholeProject)
        {
            scope.put("modulePaths", new ArrayList<>(requestedModulePaths)); //$NON-NLS-1$
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("errors", Integer.valueOf(errors)); //$NON-NLS-1$
        summary.put("warnings", Integer.valueOf(warnings)); //$NON-NLS-1$
        summary.put("information", Integer.valueOf(information)); //$NON-NLS-1$
        summary.put("total", Integer.valueOf(findings.size())); //$NON-NLS-1$

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "completed"); //$NON-NLS-1$ //$NON-NLS-2$
        result.put("pluginVersion", pluginVersion); //$NON-NLS-1$
        result.put("projectName", projectName); //$NON-NLS-1$
        result.put("scope", scope); //$NON-NLS-1$
        result.put("summary", summary); //$NON-NLS-1$
        result.put("findings", findings); //$NON-NLS-1$
        return result;
    }

    private static JsonObject parseRoot(String rawJson) throws CodeReviewBridgeException
    {
        if (rawJson == null || rawJson.isBlank())
        {
            throw new CodeReviewBridgeException("Malformed Code Review JSON: report is empty."); //$NON-NLS-1$
        }
        try
        {
            JsonElement parsed = JsonParser.parseString(rawJson);
            if (!parsed.isJsonObject())
            {
                throw new CodeReviewBridgeException(
                    "Malformed Code Review JSON: top-level value must be an object."); //$NON-NLS-1$
            }
            return parsed.getAsJsonObject();
        }
        catch (JsonParseException | IllegalStateException e)
        {
            throw new CodeReviewBridgeException(
                "Malformed Code Review JSON: " + safeMessage(e), e); //$NON-NLS-1$
        }
    }

    private static String resolveOriginalModulePath(Path absolutePath, boolean wholeProject,
        Path analysisRoot, Path originalSourceRoot, Map<String, String> stagedPathMap)
        throws CodeReviewBridgeException
    {
        String normalized = normalizeAbsolute(absolutePath);
        if (!wholeProject)
        {
            String mapped = stagedPathMap.get(normalized);
            if (mapped == null)
            {
                throw new CodeReviewBridgeException(
                    "Code Review returned a finding outside the requested staged modules: '" //$NON-NLS-1$
                        + absolutePath + "'. Retry with explicit modulePaths or wholeProject=true."); //$NON-NLS-1$
            }
            return mapped;
        }

        Path normalizedRoot = analysisRoot.toAbsolutePath().normalize();
        Path normalizedFile = absolutePath.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot))
        {
            throw new CodeReviewBridgeException(
                "Code Review returned a finding outside project source folder '" //$NON-NLS-1$
                    + originalSourceRoot + "': '" + absolutePath + "'."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return normalizedRoot.relativize(normalizedFile).toString().replace('\\', '/');
    }

    /** Stable absolute-path lookup key for stage mappings on case-insensitive Windows filesystems. */
    public static String normalizeAbsolute(Path path)
    {
        String value = path.toAbsolutePath().normalize().toString();
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            ? value.toLowerCase(Locale.ROOT) : value;
    }

    private static Path toAbsolutePath(String path) throws CodeReviewBridgeException
    {
        try
        {
            Path resolved = path.startsWith("file:") //$NON-NLS-1$
                ? Paths.get(URI.create(path)) : Paths.get(path);
            if (!resolved.isAbsolute())
            {
                throw new CodeReviewBridgeException(
                    "Malformed Code Review JSON: finding path is not absolute: '" + path + "'."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            return resolved;
        }
        catch (IllegalArgumentException e)
        {
            throw new CodeReviewBridgeException(
                "Malformed Code Review JSON: invalid finding path '" + path + "'.", e); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static JsonObject requiredObject(JsonObject object, String key, String label)
        throws CodeReviewBridgeException
    {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject())
        {
            throw new CodeReviewBridgeException(
                "Malformed Code Review JSON: " + label + " must be an object."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String key, String label)
        throws CodeReviewBridgeException
    {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
        {
            throw new CodeReviewBridgeException(
                "Malformed Code Review JSON: " + label + " must be a string."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        String result;
        try
        {
            result = value.getAsString();
        }
        catch (RuntimeException e)
        {
            throw new CodeReviewBridgeException(
                "Malformed Code Review JSON: " + label + " must be a string.", e); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (result == null || result.isBlank())
        {
            throw new CodeReviewBridgeException(
                "Malformed Code Review JSON: " + label + " must not be blank."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return result;
    }

    private static int requiredNonNegativeInt(JsonObject object, String key)
        throws CodeReviewBridgeException
    {
        JsonElement value = object.get(key);
        try
        {
            int result = value == null || value.isJsonNull() ? -1 : value.getAsInt();
            if (result >= 0)
            {
                return result;
            }
        }
        catch (RuntimeException e)
        {
            // The controlled error below carries the stable contract wording.
        }
        throw new CodeReviewBridgeException(
            "Malformed Code Review JSON: position '" + key + "' must be a non-negative integer."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String optionalDescriptionUrl(JsonObject diagnostic)
        throws CodeReviewBridgeException
    {
        JsonElement codeDescription = diagnostic.get("codeDescription"); //$NON-NLS-1$
        if (codeDescription == null || codeDescription.isJsonNull())
        {
            return null;
        }
        if (!codeDescription.isJsonObject())
        {
            throw new CodeReviewBridgeException(
                "Malformed Code Review JSON: codeDescription must be an object."); //$NON-NLS-1$
        }
        JsonElement href = codeDescription.getAsJsonObject().get("href"); //$NON-NLS-1$
        return href == null || href.isJsonNull() ? null
            : requiredString(codeDescription.getAsJsonObject(), "href", "codeDescription href"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String safeMessage(Throwable failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
