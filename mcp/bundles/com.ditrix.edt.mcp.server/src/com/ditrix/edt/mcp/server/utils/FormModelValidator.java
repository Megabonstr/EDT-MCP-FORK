/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import com._1c.g5.v8.bm.core.IBmObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Read-only structural validation for the editable managed-form model. */
public final class FormModelValidator
{
    private static final String FEATURE_ID = "id"; //$NON-NLS-1$
    private static final String FEATURE_NAME = "name"; //$NON-NLS-1$
    private static final String FEATURE_ATTRIBUTES = "attributes"; //$NON-NLS-1$
    private static final String FEATURE_COMMANDS = "formCommands"; //$NON-NLS-1$
    private static final String FEATURE_AUTO_COMMAND_BAR = "autoCommandBar"; //$NON-NLS-1$
    private static final String FEATURE_DATA_PATH = "dataPath"; //$NON-NLS-1$
    private static final String FEATURE_SEGMENTS = "segments"; //$NON-NLS-1$
    private static final String FEATURE_COMMAND_NAME = "commandName"; //$NON-NLS-1$

    private FormModelValidator()
    {
        // utility class
    }

    /** One normalized validation finding. */
    public static final class Finding
    {
        public final String code;
        public final String path;
        public final String message;

        Finding(String code, String path, String message)
        {
            this.code = code;
            this.path = path;
            this.message = message;
        }

        JsonObject toJson()
        {
            JsonObject json = new JsonObject();
            json.addProperty("code", code); //$NON-NLS-1$
            json.addProperty("severity", "error"); //$NON-NLS-1$ //$NON-NLS-2$
            json.addProperty("path", path); //$NON-NLS-1$
            json.addProperty("message", message); //$NON-NLS-1$
            return json;
        }
    }

    /** Immutable result of one form-model validation pass. */
    public static final class Result
    {
        private final List<Finding> findings;

        Result(List<Finding> findings)
        {
            this.findings = List.copyOf(findings);
        }

        public boolean isValid()
        {
            return findings.isEmpty();
        }

        public List<Finding> findings()
        {
            return findings;
        }

        public JsonArray findingsJson()
        {
            JsonArray array = new JsonArray();
            findings.forEach(f -> array.add(f.toJson()));
            return array;
        }
    }

    /**
     * Validates one transaction-bound editable form without mutating it. The caller must keep the
     * model inside a BM read boundary for the duration of this method.
     */
    public static Result validate(EObject formModel)
    {
        List<Finding> findings = new ArrayList<>();
        if (formModel == null)
        {
            findings.add(new Finding("missing-content", "(form)", //$NON-NLS-1$ //$NON-NLS-2$
                "The form has no editable content model.")); //$NON-NLS-1$
            return new Result(findings);
        }

        validateAttachment(formModel, findings);
        validateAutoCommandBar(formModel, findings);

        List<EObject> objects = allObjects(formModel);
        validateNamesAndIds(formModel, objects, findings);
        Set<String> attributes = namesOf(FormStructureReader.getReferenceList(formModel,
            FEATURE_ATTRIBUTES));
        Set<String> commands = namesOf(FormStructureReader.getReferenceList(formModel,
            FEATURE_COMMANDS));
        for (EObject object : objects)
        {
            validateDataPath(object, attributes, findings);
            validateExtInfo(object, findings);
            validateCommandReference(object, commands, findings);
            validateHandler(object, findings);
        }
        return new Result(findings);
    }

    private static void validateAttachment(EObject formModel, List<Finding> findings)
    {
        if (!(formModel instanceof IBmObject))
        {
            findings.add(new Finding("detached-content", "(form)", //$NON-NLS-1$ //$NON-NLS-2$
                "The editable form content is not attached to the BM model.")); //$NON-NLS-1$
            return;
        }
        try
        {
            String fqn = ((IBmObject)formModel).bmGetFqn();
            if (fqn == null || fqn.isBlank())
            {
                findings.add(new Finding("missing-content-fqn", "(form)", //$NON-NLS-1$ //$NON-NLS-2$
                    "The attached form content has no top-object FQN.")); //$NON-NLS-1$
            }
        }
        catch (RuntimeException e)
        {
            findings.add(new Finding("detached-content", "(form)", //$NON-NLS-1$ //$NON-NLS-2$
                "The editable form content cannot resolve its top-object FQN: " + e.getMessage())); //$NON-NLS-1$
        }
    }

    private static void validateAutoCommandBar(EObject formModel, List<Finding> findings)
    {
        EObject bar = FormStructureReader.getSingleReference(formModel, FEATURE_AUTO_COMMAND_BAR);
        if (bar == null)
        {
            findings.add(new Finding("missing-auto-command-bar", "(form)", //$NON-NLS-1$ //$NON-NLS-2$
                "The form root has no autoCommandBar.")); //$NON-NLS-1$
            return;
        }
        Integer id = integerFeature(bar, FEATURE_ID);
        if (id == null || id.intValue() != -1)
        {
            findings.add(new Finding("invalid-auto-command-bar-id", pathOf(bar), //$NON-NLS-1$
                "The form root autoCommandBar must keep the platform id=-1 sentinel.")); //$NON-NLS-1$
        }
    }

    private static void validateNamesAndIds(EObject formModel, List<EObject> objects,
        List<Finding> findings)
    {
        EClass itemClass = classifier(formModel, "FormItem"); //$NON-NLS-1$
        EClass attributeClass = classifier(formModel, "AbstractFormAttribute"); //$NON-NLS-1$
        EClass commandClass = classifier(formModel, "FormCommand"); //$NON-NLS-1$
        EClass parameterClass = classifier(formModel, "FormParameter"); //$NON-NLS-1$
        validateSpace(objects, itemClass, "item", //$NON-NLS-1$
            FormStructureReader.getSingleReference(formModel, FEATURE_AUTO_COMMAND_BAR), true,
            findings);
        validateSpace(objects, attributeClass, "attribute", null, true, findings); //$NON-NLS-1$
        validateSpace(objects, commandClass, "command", null, true, findings); //$NON-NLS-1$
        // FormParameter has its own name namespace and deliberately carries no id (#396/#456).
        validateSpace(objects, parameterClass, "parameter", null, false, findings); //$NON-NLS-1$
    }

    private static void validateSpace(List<EObject> objects, EClass type, String space,
        EObject sentinel, boolean validateIds, List<Finding> findings)
    {
        if (type == null)
        {
            return;
        }
        Map<Integer, String> ids = new HashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (EObject object : objects)
        {
            if (!type.isInstance(object))
            {
                continue;
            }
            String path = pathOf(object);
            String name = stringFeature(object, FEATURE_NAME);
            if (name.isBlank() || !name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")) //$NON-NLS-1$
            {
                findings.add(new Finding("invalid-" + space + "-name", path, //$NON-NLS-1$ //$NON-NLS-2$
                    "The " + space + " has an invalid or empty programmatic Name.")); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else
            {
                String previous = names.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), path);
                if (previous != null)
                {
                    findings.add(new Finding("duplicate-" + space + "-name", path, //$NON-NLS-1$ //$NON-NLS-2$
                        "Duplicate " + space + " Name '" + name + "'; first seen at " + previous + ".")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
            }

            if (!validateIds || object == sentinel)
            {
                continue;
            }
            Integer id = integerFeature(object, FEATURE_ID);
            if (id == null || id.intValue() <= 0)
            {
                findings.add(new Finding("invalid-" + space + "-id", path, //$NON-NLS-1$ //$NON-NLS-2$
                    "The " + space + " id must be a positive integer.")); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else
            {
                String previous = ids.putIfAbsent(id, path);
                if (previous != null)
                {
                    findings.add(new Finding("duplicate-" + space + "-id", path, //$NON-NLS-1$ //$NON-NLS-2$
                        "Duplicate " + space + " id " + id + "; first seen at " + previous + ".")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
            }
        }
    }

    private static void validateDataPath(EObject object, Set<String> attributes,
        List<Finding> findings)
    {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(FEATURE_DATA_PATH);
        if (feature == null)
        {
            return;
        }
        EObject dataPath = FormStructureReader.getSingleReference(object, FEATURE_DATA_PATH);
        if (dataPath == null)
        {
            return;
        }
        EStructuralFeature segmentsFeature = dataPath.eClass().getEStructuralFeature(FEATURE_SEGMENTS);
        Object raw = segmentsFeature == null ? null : dataPath.eGet(segmentsFeature);
        if (!(raw instanceof List<?>) || ((List<?>)raw).isEmpty())
        {
            findings.add(new Finding("invalid-data-path", pathOf(object), //$NON-NLS-1$
                "dataPath exists but has no path segments.")); //$NON-NLS-1$
            return;
        }
        String head = String.valueOf(((List<?>)raw).get(0));
        if (head.isBlank() || !attributes.contains(head.toLowerCase(java.util.Locale.ROOT)))
        {
            findings.add(new Finding("invalid-data-path", pathOf(object), //$NON-NLS-1$
                "dataPath head '" + head + "' does not resolve to a form attribute.")); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static void validateExtInfo(EObject object, List<Finding> findings)
    {
        if (FormElementWriter.resolveExtInfoEClass(object) != null
            && FormElementWriter.extInfoInstance(object) == null)
        {
            findings.add(new Finding("missing-ext-info", pathOf(object), //$NON-NLS-1$
                "The form element requires extInfo for its current type.")); //$NON-NLS-1$
        }
    }

    private static void validateCommandReference(EObject object, Set<String> commands,
        List<Finding> findings)
    {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(FEATURE_COMMAND_NAME);
        if (feature == null)
        {
            return;
        }
        Object raw = object.eGet(feature);
        if (raw == null)
        {
            return;
        }
        String command = raw instanceof EObject
            ? stringFeature((EObject)raw, FEATURE_NAME) : String.valueOf(raw);
        boolean unresolved = raw instanceof EObject && ((EObject)raw).eIsProxy();
        if (unresolved || command.isBlank()
            || !commands.contains(command.toLowerCase(java.util.Locale.ROOT)))
        {
            findings.add(new Finding("bad-command-reference", pathOf(object), //$NON-NLS-1$
                "commandName '" + command + "' does not resolve to a form command.")); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static void validateHandler(EObject object, List<Finding> findings)
    {
        String className = object.eClass().getName();
        // EDT also persists service containers such as FormCommandHandlerContainer. Their
        // class names contain "Handler", but they do not represent a BSL procedure and have no
        // programmatic name by design. Only named handler model objects carry that contract.
        if (!className.contains("Handler") //$NON-NLS-1$
            || object.eClass().getEStructuralFeature(FEATURE_NAME) == null)
        {
            return;
        }
        if (className.contains("EventHandler")) //$NON-NLS-1$
        {
            EObject event = FormStructureReader.getSingleReference(object, "event"); //$NON-NLS-1$
            if (event == null || event.eIsProxy())
            {
                findings.add(new Finding("invalid-event-reference", pathOf(object), //$NON-NLS-1$
                    "The event handler has no resolvable event metadata reference.")); //$NON-NLS-1$
            }
        }
        String procedure = stringFeature(object, FEATURE_NAME);
        if (procedure.isBlank())
        {
            findings.add(new Finding("invalid-handler-reference", pathOf(object), //$NON-NLS-1$
                "The handler has no BSL procedure name.")); //$NON-NLS-1$
        }
    }

    private static List<EObject> allObjects(EObject formModel)
    {
        List<EObject> objects = new ArrayList<>();
        objects.add(formModel);
        // A live EDT form exposes large computed transient containments (context definitions,
        // standard commands, layouter state). Validation must inspect only authored/persisted
        // content and must not materialize those branches as a side effect of reading.
        for (EObject object : PersistedContents.descendants(formModel))
        {
            objects.add(object);
        }
        return objects;
    }

    private static Set<String> namesOf(List<EObject> objects)
    {
        Set<String> names = new HashSet<>();
        for (EObject object : objects)
        {
            String name = stringFeature(object, FEATURE_NAME);
            if (!name.isBlank())
            {
                names.add(name.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return names;
    }

    private static EClass classifier(EObject formModel, String name)
    {
        if (formModel.eClass().getEPackage() == null)
        {
            return null;
        }
        org.eclipse.emf.ecore.EClassifier classifier = formModel.eClass().getEPackage().getEClassifier(name);
        return classifier instanceof EClass ? (EClass)classifier : null;
    }

    private static Integer integerFeature(EObject object, String name)
    {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        Object value = feature == null ? null : object.eGet(feature);
        return value instanceof Integer ? (Integer)value : null;
    }

    private static String stringFeature(EObject object, String name)
    {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        Object value = feature == null ? null : object.eGet(feature);
        return value instanceof String ? (String)value : ""; //$NON-NLS-1$
    }

    private static String pathOf(EObject object)
    {
        List<String> parts = new ArrayList<>();
        for (EObject current = object; current != null; current = current.eContainer())
        {
            String name = stringFeature(current, FEATURE_NAME);
            parts.add(0, name.isBlank() ? current.eClass().getName() : name);
        }
        return String.join("/", parts); //$NON-NLS-1$
    }
}
