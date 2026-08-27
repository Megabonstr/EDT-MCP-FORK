/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.McpKeys;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.ToolAnnotations;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.BackgroundJobPolling;
import com.ditrix.edt.mcp.server.utils.BackgroundJobRenderer;
import com.ditrix.edt.mcp.server.utils.BackgroundJobs;
import com.ditrix.edt.mcp.server.utils.BackgroundJobs.JobSnapshot;
import com.ditrix.edt.mcp.server.utils.BslModuleUtils;
import com.ditrix.edt.mcp.server.utils.CodeReviewBridge;
import com.ditrix.edt.mcp.server.utils.CodeReviewBridge.ModuleInput;
import com.ditrix.edt.mcp.server.utils.CodeReviewBridge.PluginContract;
import com.ditrix.edt.mcp.server.utils.CodeReviewBridgeException;
import com.ditrix.edt.mcp.server.utils.ProjectContext;

/** Runs the optional MCP:RSV Code Review plugin headlessly as one background analysis. */
public class CodeReviewTool implements IMcpTool
{
    public static final String NAME = "code_review"; //$NON-NLS-1$

    private static final String KEY_MODULE_PATHS = "modulePaths"; //$NON-NLS-1$
    private static final String KEY_WHOLE_PROJECT = "wholeProject"; //$NON-NLS-1$
    private static final String KEY_WAIT_SECONDS = "waitSeconds"; //$NON-NLS-1$
    private static final int DEFAULT_WAIT_SECONDS = 5;
    private static final int MAX_WAIT_SECONDS = 45;
    // Code Review owns the real ten-minute engine limit. This registry guard is deliberately just
    // beyond it and cannot interrupt the committed runner invocation.
    private static final long JOB_REGISTRY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(11);

    private final BackgroundJobs jobs;
    private final CodeReviewBridge bridge;
    private final Object startLock = new Object();
    private String activeJobId;

    public CodeReviewTool()
    {
        this(BackgroundJobs.shared(), new CodeReviewBridge());
    }

    CodeReviewTool(BackgroundJobs jobs, CodeReviewBridge bridge)
    {
        this.jobs = jobs;
        this.bridge = bridge;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Run the optional MCP:RSV Code Review plugin headlessly for explicit BSL modules " //$NON-NLS-1$
            + "or an explicitly requested whole project, returning a background job to poll with " //$NON-NLS-1$
            + "get_job_status. Full parameters and examples: call get_tool_guide('code_review')."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .stringProperty(McpKeys.PROJECT_NAME, "Exact EDT project name.", true) //$NON-NLS-1$
            .stringArrayProperty(KEY_MODULE_PATHS,
                "Non-empty array of paths relative to the project's src folder, for example " //$NON-NLS-1$
                    + "['CommonModules/MyModule/Module.bsl']; mutually exclusive with " //$NON-NLS-1$
                    + "wholeProject=true.") //$NON-NLS-1$
            .booleanProperty(KEY_WHOLE_PROJECT,
                "Explicitly scan every BSL module under the project src folder. Default false; " //$NON-NLS-1$
                    + "mutually exclusive with modulePaths.") //$NON-NLS-1$
            .integerProperty(KEY_WAIT_SECONDS,
                "Seconds this start call waits for the background job, from 0 to 45; default 5. " //$NON-NLS-1$
                    + "Use 0 to return the jobId immediately, then poll get_job_status.") //$NON-NLS-1$
            .build();
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        // It reads project source and starts a local analyzer process, but writes neither the EDT
        // project nor Problems markers. The name has no read prefix, so declare this explicitly.
        return new ToolAnnotations(null, Boolean.TRUE, null, Boolean.TRUE, Boolean.FALSE);
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String required = JsonUtils.requireArguments(params, McpKeys.PROJECT_NAME);
        if (required != null)
        {
            return required;
        }
        Integer waitSeconds = BackgroundJobPolling.readWaitSeconds(params, KEY_WAIT_SECONDS,
            DEFAULT_WAIT_SECONDS, MAX_WAIT_SECONDS);
        if (waitSeconds == null)
        {
            return BackgroundJobPolling.waitSecondsError(KEY_WAIT_SECONDS,
                params != null ? params.get(KEY_WAIT_SECONDS) : null,
                DEFAULT_WAIT_SECONDS, MAX_WAIT_SECONDS);
        }

        String projectName = JsonUtils.extractStringArgument(params, McpKeys.PROJECT_NAME);
        boolean wholeProject = JsonUtils.extractBooleanArgument(params, KEY_WHOLE_PROJECT, false);
        List<String> requestedPaths = JsonUtils.extractArrayArgument(params, KEY_MODULE_PATHS);
        boolean hasModules = requestedPaths != null && !requestedPaths.isEmpty();
        if (wholeProject == hasModules)
        {
            return ToolResult.error(
                "code_review requires exactly one scope: pass a non-empty modulePaths array, or " //$NON-NLS-1$
                    + "set wholeProject=true. Do not combine them and do not omit both.") //$NON-NLS-1$
                .toJson();
        }

        ProjectContext context = ProjectContext.of(projectName);
        if (!context.exists())
        {
            return ToolResult.error(ProjectContext.notFoundMessage(projectName)).toJson();
        }
        if (!context.isOpen())
        {
            return ToolResult.error("Project '" + projectName //$NON-NLS-1$
                + "' is closed. Open it in EDT, confirm it is ready with list_projects, and retry " //$NON-NLS-1$
                + "code_review.").toJson(); //$NON-NLS-1$
        }

        ResolvedScope scope;
        try
        {
            scope = resolveScope(context.project(), wholeProject, requestedPaths);
        }
        catch (CodeReviewBridgeException e)
        {
            return ToolResult.error(e.getMessage()).toJson();
        }

        PluginContract plugin;
        try
        {
            plugin = bridge.inspect();
        }
        catch (CodeReviewBridgeException e)
        {
            return ToolResult.error(e.getMessage()).toJson();
        }

        synchronized (startLock)
        {
            JobSnapshot active = activeJobId == null ? null : jobs.get(activeJobId);
            if (active != null && active.getStatus() == BackgroundJobs.Status.RUNNING)
            {
                return ToolResult.error("Another code_review analysis is already running as job '" //$NON-NLS-1$
                    + activeJobId + "'. Poll it with get_job_status and wait for a terminal state " //$NON-NLS-1$
                    + "before starting another analysis.").toJson(); //$NON-NLS-1$
            }
            activeJobId = null;

            try
            {
                JobSnapshot started = jobs.start(NAME, JOB_REGISTRY_TIMEOUT_MS,
                    "Accepted Code Review " + plugin.version() + " for project '" //$NON-NLS-1$ //$NON-NLS-2$
                        + projectName + "'.", progress -> { //$NON-NLS-1$
                            // Once the plugin process is about to start, cancellation is deliberately
                            // unsupported in v1: its public runner does not expose a safe process stop.
                            if (!progress.tryCommit())
                            {
                                return null;
                            }
                            progress.add("Started the Code Review runner; this v1 analysis cannot " //$NON-NLS-1$
                                + "be safely cancelled. Its internal limit is 10 minutes."); //$NON-NLS-1$
                            return bridge.review(plugin, projectName, scope.sourceRoot,
                                wholeProject, scope.modules, progress);
                        });
                activeJobId = started.getId();
                return BackgroundJobRenderer.render(BackgroundJobPolling.await(jobs,
                    started.getId(), waitSeconds.intValue()));
            }
            catch (RejectedExecutionException e)
            {
                return ToolResult.error(
                    "Could not start code_review because the background-job registry is full or " //$NON-NLS-1$
                        + "stopping: " + safeMessage(e) + ". Poll existing jobs with " //$NON-NLS-1$ //$NON-NLS-2$
                        + "get_job_status and retry, or restart EDT if the bundle is stopping.") //$NON-NLS-1$
                    .toJson();
            }
        }
    }

    private static ResolvedScope resolveScope(IProject project, boolean wholeProject,
        List<String> requestedPaths) throws CodeReviewBridgeException
    {
        IFolder sourceFolder = project.getFolder(BslModuleUtils.SOURCE_FOLDER);
        if (!sourceFolder.exists() || sourceFolder.getLocation() == null)
        {
            throw new CodeReviewBridgeException("Project '" + project.getName() //$NON-NLS-1$
                + "' has no accessible src folder. Confirm the EDT project layout with " //$NON-NLS-1$
                + "list_projects and list_modules, then retry code_review."); //$NON-NLS-1$
        }
        Path sourceRoot;
        try
        {
            sourceRoot = sourceFolder.getLocation().toFile().toPath().toRealPath();
        }
        catch (IOException e)
        {
            throw new CodeReviewBridgeException("Could not resolve project '" + project.getName() //$NON-NLS-1$
                + "' src folder: " + safeMessage(e) //$NON-NLS-1$
                + ". Check filesystem access and retry code_review.", e); //$NON-NLS-1$
        }
        if (wholeProject)
        {
            return new ResolvedScope(sourceRoot, new ArrayList<>());
        }

        Map<String, ModuleInput> unique = new LinkedHashMap<>();
        for (String rawPath : requestedPaths)
        {
            String modulePath = rawPath == null ? "" : rawPath.trim().replace('\\', '/'); //$NON-NLS-1$
            validateRelativeModulePath(modulePath);
            IFile module = BslModuleUtils.resolveModuleFile(project, modulePath);
            if (module == null || !module.exists() || !project.equals(module.getProject())
                || module.getLocation() == null)
            {
                throw moduleNotFound(modulePath, project.getName());
            }
            Path realModule;
            try
            {
                realModule = module.getLocation().toFile().toPath().toRealPath();
            }
            catch (IOException e)
            {
                throw new CodeReviewBridgeException("Could not resolve modulePath '" + modulePath //$NON-NLS-1$
                    + "' inside project '" + project.getName() + "': " + safeMessage(e) //$NON-NLS-1$ //$NON-NLS-2$
                    + ". Use list_modules to select a readable BSL module and retry.", e); //$NON-NLS-1$
            }
            if (!realModule.startsWith(sourceRoot))
            {
                throw new CodeReviewBridgeException("modulePath '" + modulePath //$NON-NLS-1$
                    + "' resolves outside project '" + project.getName() //$NON-NLS-1$
                    + "' src folder. Pass only a path returned by list_modules."); //$NON-NLS-1$
            }
            unique.putIfAbsent(modulePath, new ModuleInput(modulePath, realModule));
        }
        return new ResolvedScope(sourceRoot, new ArrayList<>(unique.values()));
    }

    private static void validateRelativeModulePath(String modulePath)
        throws CodeReviewBridgeException
    {
        if (modulePath.isBlank())
        {
            throw new CodeReviewBridgeException(
                "modulePaths contains a blank value. Pass non-empty paths returned by " //$NON-NLS-1$
                    + "list_modules and retry code_review."); //$NON-NLS-1$
        }
        if (BslModuleUtils.looksLikeAbsolutePath(modulePath))
        {
            throw new CodeReviewBridgeException("modulePath '" + modulePath //$NON-NLS-1$
                + "' is absolute. Pass a path relative to the selected project's src folder, " //$NON-NLS-1$
                + "as returned by list_modules."); //$NON-NLS-1$
        }
        if (modulePath.equals("src") || modulePath.startsWith("src/")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            throw new CodeReviewBridgeException("modulePath '" + modulePath //$NON-NLS-1$
                + "' must be relative to src and must not include the 'src/' prefix. Use " //$NON-NLS-1$
                + "list_modules to obtain the accepted path."); //$NON-NLS-1$
        }
        for (String segment : modulePath.split("/")) //$NON-NLS-1$
        {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) //$NON-NLS-1$ //$NON-NLS-2$
            {
                throw new CodeReviewBridgeException("modulePath '" + modulePath //$NON-NLS-1$
                    + "' contains an empty, '.' or '..' segment. Pass an exact path returned by " //$NON-NLS-1$
                    + "list_modules."); //$NON-NLS-1$
            }
        }
        if (!modulePath.toLowerCase(java.util.Locale.ROOT).endsWith(".bsl")) //$NON-NLS-1$
        {
            throw new CodeReviewBridgeException("modulePath '" + modulePath //$NON-NLS-1$
                + "' is not a .bsl module. Use list_modules to select a BSL module."); //$NON-NLS-1$
        }
    }

    private static CodeReviewBridgeException moduleNotFound(String modulePath, String projectName)
    {
        return new CodeReviewBridgeException("BSL modulePath '" + modulePath //$NON-NLS-1$
            + "' was not found inside project '" + projectName //$NON-NLS-1$
            + "'. Use list_modules for that project and retry code_review."); //$NON-NLS-1$
    }

    private static String safeMessage(Throwable failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static final class ResolvedScope
    {
        final Path sourceRoot;
        final List<ModuleInput> modules;

        ResolvedScope(Path sourceRoot, List<ModuleInput> modules)
        {
            this.sourceRoot = sourceRoot;
            this.modules = modules;
        }
    }
}
