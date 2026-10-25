/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.utils.BackgroundJobs.ProgressReporter;

/** Optional runtime bridge to the separately installed MCP:RSV Code Review bundle. */
public class CodeReviewBridge
{
    static final String BUNDLE_ID = "com.radzivillovich.edt.rsv.codereview"; //$NON-NLS-1$
    static final String RUNNER_CLASS =
        "com.radzivillovich.edt.rsv.codereview.internal.BslLanguageServerRunner"; //$NON-NLS-1$
    static final String RUNNER_METHOD = "analyzeToJson"; //$NON-NLS-1$
    private static final Version MINIMUM_VERSION = new Version(1, 4, 0);

    /** Resolved source module passed from the MCP tool into the isolated staging boundary. */
    public static final class ModuleInput
    {
        private final String modulePath;
        private final Path sourcePath;

        public ModuleInput(String modulePath, Path sourcePath)
        {
            this.modulePath = modulePath;
            this.sourcePath = sourcePath;
        }
    }

    /** Verified optional-bundle contract retained between the start call and background work. */
    public static final class PluginContract
    {
        private final Bundle bundle;
        private final String version;
        private final Class<?> runnerClass;
        private final Method runnerMethod;

        private PluginContract(Bundle bundle, String version, Class<?> runnerClass,
            Method runnerMethod)
        {
            this.bundle = bundle;
            this.version = version;
            this.runnerClass = runnerClass;
            this.runnerMethod = runnerMethod;
        }

        public String version()
        {
            return version;
        }
    }

    /** Finds the optional bundle and verifies the exact reflection contract without running it. */
    public PluginContract inspect() throws CodeReviewBridgeException
    {
        Bundle bundle = Platform.getBundle(BUNDLE_ID);
        if (bundle == null)
        {
            throw new CodeReviewBridgeException(
                "MCP:RSV Code Review plugin is not installed. Install Code Review 1.4.0 or " //$NON-NLS-1$
                    + "newer in this EDT, restart EDT, and retry code_review."); //$NON-NLS-1$
        }
        Version version = bundle.getVersion();
        if (version == null || version.compareTo(MINIMUM_VERSION) < 0)
        {
            throw new CodeReviewBridgeException(
                "Installed MCP:RSV Code Review version '" + version //$NON-NLS-1$
                    + "' is incompatible. Install version 1.4.0 or newer and retry code_review."); //$NON-NLS-1$
        }

        try
        {
            if (bundle.getState() != Bundle.ACTIVE && bundle.getState() != Bundle.STARTING)
            {
                bundle.start(Bundle.START_TRANSIENT);
            }
            Class<?> runnerClass = bundle.loadClass(RUNNER_CLASS);
            Method runnerMethod = runnerClass.getMethod(RUNNER_METHOD, File.class, File.class);
            if (!String.class.equals(runnerMethod.getReturnType()))
            {
                throw incompatibleSignature(version, null);
            }
            return new PluginContract(bundle, version.toString(), runnerClass, runnerMethod);
        }
        catch (BundleException | ClassNotFoundException | NoSuchMethodException
            | SecurityException e)
        {
            throw incompatibleSignature(version, e);
        }
    }

    /** Runs the verified public headless entry point and maps its JSON result. */
    public Map<String, Object> review(PluginContract contract, String projectName,
        Path sourceRoot, boolean wholeProject, List<ModuleInput> modules,
        ProgressReporter progress) throws CodeReviewBridgeException, InterruptedException
    {
        Path stageRoot = null;
        Path analysisRoot = sourceRoot;
        List<Path> analysisDirectories = new ArrayList<>();
        Map<String, String> stagedPathMap = new LinkedHashMap<>();
        List<String> requestedModulePaths = new ArrayList<>();
        try
        {
            if (!wholeProject)
            {
                stageRoot = Files.createTempDirectory("edt-mcp-code-review-"); //$NON-NLS-1$
                analysisRoot = stageRoot;
                for (ModuleInput module : modules)
                {
                    requestedModulePaths.add(module.modulePath);
                    Path staged = stageRoot.resolve(module.modulePath.replace('/', File.separatorChar))
                        .normalize();
                    if (!staged.startsWith(stageRoot))
                    {
                        throw new CodeReviewBridgeException(
                            "Resolved staging path escaped its temporary root for modulePath '" //$NON-NLS-1$
                                + module.modulePath + "'. Fix modulePaths and retry code_review."); //$NON-NLS-1$
                    }
                    Files.createDirectories(staged.getParent());
                    Files.copy(module.sourcePath, staged, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                    stagedPathMap.put(CodeReviewReportMapper.normalizeAbsolute(staged),
                        module.modulePath);
                    if (!analysisDirectories.contains(staged.getParent()))
                    {
                        analysisDirectories.add(staged.getParent());
                    }
                }
                progress.add("Prepared " + modules.size() //$NON-NLS-1$
                    + " selected BSL module(s) in an isolated staging directory."); //$NON-NLS-1$
            }
            else
            {
                analysisDirectories.add(sourceRoot);
                progress.add("Prepared an explicit whole-project Code Review scope."); //$NON-NLS-1$
            }

            List<String> rawReports = new ArrayList<>();
            Path workingDirectory = wholeProject ? sourceRoot : stageRoot;
            for (Path analysisDirectory : analysisDirectories)
            {
                rawReports.add(invoke(contract, analysisDirectory.toFile(),
                    workingDirectory.toFile()));
            }
            progress.add("Code Review finished; mapping structured diagnostics."); //$NON-NLS-1$
            return CodeReviewReportMapper.map(rawReports, contract.version, projectName,
                wholeProject, requestedModulePaths, analysisRoot, sourceRoot, stagedPathMap);
        }
        catch (IOException e)
        {
            throw new CodeReviewBridgeException(
                "Code Review bridge filesystem failure: " + safeMessage(e) //$NON-NLS-1$
                    + ". Check EDT project/source access and retry code_review.", e); //$NON-NLS-1$
        }
        finally
        {
            deleteBridgeOwnedTree(stageRoot);
        }
    }

    private static String invoke(PluginContract contract, File analyzeDir, File workingDir)
        throws CodeReviewBridgeException, InterruptedException
    {
        try
        {
            if (contract.bundle.getState() != Bundle.ACTIVE
                && contract.bundle.getState() != Bundle.STARTING)
            {
                contract.bundle.start(Bundle.START_TRANSIENT);
            }
            Object runner = contract.runnerClass.getConstructor().newInstance();
            Object result = contract.runnerMethod.invoke(runner, analyzeDir, workingDir);
            if (!(result instanceof String))
            {
                throw new CodeReviewBridgeException(
                    "Code Review runner returned no JSON text. Reinstall Code Review 1.4.0 or " //$NON-NLS-1$
                        + "newer and retry code_review."); //$NON-NLS-1$
            }
            return (String)result;
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
                throw (InterruptedException)cause;
            }
            throw new CodeReviewBridgeException(
                "Code Review runner failed: " + safeMessage(cause) //$NON-NLS-1$
                    + ". Check the installed Code Review plugin and retry code_review.", cause); //$NON-NLS-1$
        }
        catch (ReflectiveOperationException | BundleException | LinkageError e)
        {
            throw new CodeReviewBridgeException(
                "Could not invoke MCP:RSV Code Review " + contract.version //$NON-NLS-1$
                    + " through its analyzeToJson(File, File) contract: " + safeMessage(e) //$NON-NLS-1$
                    + ". Reinstall a compatible Code Review plugin and restart EDT.", e); //$NON-NLS-1$
        }
    }

    private static CodeReviewBridgeException incompatibleSignature(Version version,
        Throwable cause)
    {
        return new CodeReviewBridgeException(
            "Installed MCP:RSV Code Review version '" + version //$NON-NLS-1$
                + "' does not expose BslLanguageServerRunner.analyzeToJson(File, File) " //$NON-NLS-1$
                + "returning String. Install a compatible version and retry code_review.", cause); //$NON-NLS-1$
    }

    private static void deleteBridgeOwnedTree(Path root)
    {
        if (root == null || !Files.exists(root))
        {
            return;
        }
        try (Stream<Path> paths = Files.walk(root))
        {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try
                {
                    Files.deleteIfExists(path);
                }
                catch (IOException e)
                {
                    Activator.logWarning(
                        "Could not delete Code Review bridge temporary path '" + path //$NON-NLS-1$
                            + "': " + safeMessage(e)); //$NON-NLS-1$
                }
            });
        }
        catch (IOException e)
        {
            Activator.logWarning("Could not clean Code Review bridge temporary directory '" //$NON-NLS-1$
                + root + "': " + safeMessage(e)); //$NON-NLS-1$
        }
    }

    private static String safeMessage(Throwable failure)
    {
        if (failure == null)
        {
            return "unknown failure"; //$NON-NLS-1$
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
