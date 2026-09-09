/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.junit.Test;

/** Headless structural-finding tests for {@link FormModelValidator}. */
public class FormModelValidatorTest
{
    @Test
    public void testMinimalNativeShapeKeepsCommandBarInvariant()
    {
        EObject form = FormElementWriter.createContentForm(null, null, null, false);
        Set<String> codes = codes(FormModelValidator.validate(form));
        assertTrue(codes.contains("detached-content")); //$NON-NLS-1$
        assertFalse(codes.contains("missing-auto-command-bar")); //$NON-NLS-1$
        assertFalse(codes.contains("invalid-auto-command-bar-id")); //$NON-NLS-1$
    }

    @Test
    public void testDuplicateAttributeNamesAndIdsAreReported()
    {
        EObject form = FormElementWriter.createContentForm(null, null, null, false);
        create(form, FormElementWriter.Kind.ATTRIBUTE, "First", null, null); //$NON-NLS-1$
        create(form, FormElementWriter.Kind.ATTRIBUTE, "Second", null, null); //$NON-NLS-1$
        List<EObject> attributes = FormStructureReader.getReferenceList(form, "attributes"); //$NON-NLS-1$
        EObject first = attributes.get(0);
        EObject second = attributes.get(1);
        set(second, "name", get(first, "name")); //$NON-NLS-1$ //$NON-NLS-2$
        set(second, "id", get(first, "id")); //$NON-NLS-1$ //$NON-NLS-2$

        Set<String> codes = codes(FormModelValidator.validate(form));
        assertTrue(codes.contains("duplicate-attribute-name")); //$NON-NLS-1$
        assertTrue(codes.contains("duplicate-attribute-id")); //$NON-NLS-1$
    }

    @Test
    public void testParameterNamesUseTheirOwnNamespaceAndHaveNoIdInvariant()
    {
        EObject form = FormElementWriter.createContentForm(null, null, null, false);
        create(form, FormElementWriter.Kind.PARAMETER, "First", null, null); //$NON-NLS-1$
        create(form, FormElementWriter.Kind.PARAMETER, "Second", null, null); //$NON-NLS-1$
        List<EObject> parameters = FormStructureReader.getReferenceList(form, "parameters"); //$NON-NLS-1$
        set(parameters.get(1), "name", get(parameters.get(0), "name")); //$NON-NLS-1$ //$NON-NLS-2$

        Set<String> codes = codes(FormModelValidator.validate(form));
        assertTrue(codes.contains("duplicate-parameter-name")); //$NON-NLS-1$
        assertFalse(codes.contains("invalid-parameter-id")); //$NON-NLS-1$
    }

    @Test
    public void testParameterAndAttributeMayShareAName()
    {
        EObject form = FormElementWriter.createContentForm(null, null, null, false);
        create(form, FormElementWriter.Kind.ATTRIBUTE, "Context", null, null); //$NON-NLS-1$
        create(form, FormElementWriter.Kind.PARAMETER, "Context", null, null); //$NON-NLS-1$

        Set<String> codes = codes(FormModelValidator.validate(form));
        assertFalse(codes.contains("duplicate-attribute-name")); //$NON-NLS-1$
        assertFalse(codes.contains("duplicate-parameter-name")); //$NON-NLS-1$
    }

    @Test
    public void testBindingAndExtInfoFindingsAreIndependent()
    {
        EObject form = FormElementWriter.createContentForm(null, null, null, false);
        create(form, FormElementWriter.Kind.ATTRIBUTE, "Price", null, null); //$NON-NLS-1$
        create(form, FormElementWriter.Kind.FIELD, "PriceField", null, "Price"); //$NON-NLS-1$ //$NON-NLS-2$
        EObject field = FormElementWriter.findFormItem(form, "PriceField"); //$NON-NLS-1$
        EObject dataPath = FormStructureReader.getSingleReference(field, "dataPath"); //$NON-NLS-1$
        @SuppressWarnings("unchecked")
        EList<String> segments = (EList<String>)get(dataPath, "segments"); //$NON-NLS-1$
        segments.set(0, "Missing"); //$NON-NLS-1$
        field.eUnset(feature(field, "extInfo")); //$NON-NLS-1$

        Set<String> codes = codes(FormModelValidator.validate(form));
        assertTrue(codes.contains("invalid-data-path")); //$NON-NLS-1$
        assertTrue(codes.contains("missing-ext-info")); //$NON-NLS-1$
    }

    @Test
    public void testBadCommandAndHandlerReferencesAreReported()
    {
        EObject form = FormElementWriter.createContentForm(null, null, null, false);
        create(form, FormElementWriter.Kind.COMMAND, "Refresh", null, null); //$NON-NLS-1$
        create(form, FormElementWriter.Kind.BUTTON, "RefreshButton", null, "Refresh"); //$NON-NLS-1$ //$NON-NLS-2$
        EObject button = FormElementWriter.findFormItem(form, "RefreshButton"); //$NON-NLS-1$
        EReference commandRef = (EReference)feature(button, "commandName"); //$NON-NLS-1$
        EObject existingCommand = FormStructureReader.getReferenceList(form, "formCommands").get(0); //$NON-NLS-1$
        EObject missingCommand = existingCommand.eClass().getEPackage().getEFactoryInstance()
            .create(existingCommand.eClass());
        set(missingCommand, "name", "MissingCommand"); //$NON-NLS-1$ //$NON-NLS-2$
        button.eSet(commandRef, missingCommand);

        String handlerError = FormElementWriter.createHandler(existingCommand, "Action", //$NON-NLS-1$
            "RefreshAction", null, null, new String[1]); //$NON-NLS-1$
        assertEquals(handlerError, null, handlerError);
        assertEquals(0, countCode(FormModelValidator.validate(form),
            "invalid-handler-reference")); //$NON-NLS-1$
        EObject action = FormStructureReader.getSingleReference(existingCommand, "action"); //$NON-NLS-1$
        EObject handler = FormStructureReader.getSingleReference(action, "handler"); //$NON-NLS-1$
        handler.eUnset(feature(handler, "name")); //$NON-NLS-1$

        FormModelValidator.Result result = FormModelValidator.validate(form);
        Set<String> codes = codes(result);
        assertTrue(codes.contains("bad-command-reference")); //$NON-NLS-1$
        assertTrue(codes.contains("invalid-handler-reference")); //$NON-NLS-1$
        assertEquals(1, countCode(result, "invalid-handler-reference")); //$NON-NLS-1$
    }

    @Test
    public void testMissingBarIsReportedWithoutMutation()
    {
        EObject form = FormElementWriter.createContentForm(null, null, null, false);
        form.eUnset(feature(form, "autoCommandBar")); //$NON-NLS-1$
        int before = form.eContents().size();
        FormModelValidator.Result result = FormModelValidator.validate(form);
        assertTrue(codes(result).contains("missing-auto-command-bar")); //$NON-NLS-1$
        assertEquals(before, form.eContents().size());
    }

    private static void create(EObject form, FormElementWriter.Kind kind, String name,
        String parent, String bind)
    {
        String error = FormElementWriter.createMember(form, kind, name, parent, bind,
            null, null, false, new String[1]);
        assertEquals(error, null, error);
    }

    private static Set<String> codes(FormModelValidator.Result result)
    {
        return result.findings().stream().map(f -> f.code).collect(Collectors.toSet());
    }

    private static long countCode(FormModelValidator.Result result, String code)
    {
        return result.findings().stream().filter(f -> code.equals(f.code)).count();
    }

    private static EStructuralFeature feature(EObject object, String name)
    {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        if (feature == null)
        {
            throw new AssertionError("Missing feature: " + name); //$NON-NLS-1$
        }
        return feature;
    }

    private static Object get(EObject object, String name)
    {
        return object.eGet(feature(object, name));
    }

    private static void set(EObject object, String name, Object value)
    {
        object.eSet(feature(object, name), value);
    }
}
