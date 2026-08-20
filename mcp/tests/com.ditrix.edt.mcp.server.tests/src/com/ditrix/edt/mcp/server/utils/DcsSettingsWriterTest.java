/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com._1c.g5.v8.dt.dcs.model.schema.DataCompositionSchema;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionGroup;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionSettings;
import com._1c.g5.v8.dt.dcs.model.settings.SettingsVariant;
import com._1c.g5.v8.dt.mcore.EnumValue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests the complete native advanced DCS settings write/read/mutation surface. */
public class DcsSettingsWriterTest
{
    private static JsonObject json(String value)
    {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static DataCompositionSchema schema()
    {
        return com._1c.g5.v8.dt.dcs.model.schema.DcsFactory.eINSTANCE.createDataCompositionSchema();
    }

    @Test
    public void testWritesAndReadsEveryFullSection()
    {
        DataCompositionSchema schema = schema();
        JsonObject spec = json("{" //$NON-NLS-1$
            + "\"totalFields\":[{\"dataPath\":\"Amount\",\"expression\":\"SUM(Amount)\",\"groups\":[\"Warehouse\"]}]," //$NON-NLS-1$
            + "\"selection\":[{\"kind\":\"group\",\"field\":\"Warehouse\",\"items\":[{\"field\":\"Amount\"}]}]," //$NON-NLS-1$
            + "\"filter\":[{\"kind\":\"group\",\"groupType\":\"AND_GROUP\",\"items\":[{\"left\":\"Posted\",\"comparison\":\"EQUAL\",\"right\":true}]}]," //$NON-NLS-1$
            + "\"order\":[{\"field\":\"Amount\",\"direction\":\"DESC\"}]," //$NON-NLS-1$
            + "\"structure\":[{\"kind\":\"group\",\"id\":\"g1\",\"name\":\"Warehouse\",\"groupFields\":[{\"field\":\"Warehouse\",\"groupType\":\"ITEMS\"}],\"items\":[{\"kind\":\"chart\",\"id\":\"c1\",\"points\":[{\"id\":\"p1\",\"groupFields\":[{\"field\":\"Warehouse\"}]}],\"series\":[{\"id\":\"s1\",\"groupFields\":[{\"field\":\"Period\",\"groupType\":\"HIERARCHY\"}]}]}]}]," //$NON-NLS-1$
            + "\"conditionalAppearance\":[{\"fields\":[\"Amount\"],\"filter\":[{\"left\":\"Amount\",\"comparison\":\"GREATER\",\"right\":0}],\"userSetting\":{\"id\":\"negative\",\"viewMode\":\"QUICK_ACCESS\"}}]," //$NON-NLS-1$
            + "\"userSettings\":{\"id\":\"structure\",\"viewMode\":\"NORMAL\",\"selection\":{\"id\":\"fields\",\"viewMode\":\"QUICK_ACCESS\"}}," //$NON-NLS-1$
            + "\"output\":{\"ResourcePlacement\":\"HORIZONTALLY\"," //$NON-NLS-1$
            + "\"ChartType.ResourcesPlacement\":\"AUTO\"}}" ); //$NON-NLS-1$

        DcsSettingsWriter.Result result = DcsSettingsWriter.apply(schema, spec);
        assertFalse(result.error, result.hasError());
        assertNotNull(schema.getDefaultSettings());
        assertEquals(1, schema.getTotalFields().size());
        assertEquals(1, schema.getDefaultSettings().getItems().size());
        assertTrue(schema.getDefaultSettings().getItems().get(0) instanceof DataCompositionGroup);

        JsonObject after = result.after;
        assertEquals("Amount", after.getAsJsonArray("totalFields").get(0).getAsJsonObject() //$NON-NLS-1$ //$NON-NLS-2$
            .get("dataPath").getAsString()); //$NON-NLS-1$
        assertEquals("DESC", after.getAsJsonArray("order").get(0).getAsJsonObject() //$NON-NLS-1$ //$NON-NLS-2$
            .get("direction").getAsString()); //$NON-NLS-1$
        assertEquals("chart", after.getAsJsonArray("structure").get(0).getAsJsonObject() //$NON-NLS-1$ //$NON-NLS-2$
            .getAsJsonArray("items").get(0).getAsJsonObject().get("kind").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("HORIZONTALLY", after.getAsJsonObject("output") //$NON-NLS-1$
            .get("ResourcePlacement").getAsString()); //$NON-NLS-1$
        assertEquals("AUTO", after.getAsJsonObject("output") //$NON-NLS-1$ //$NON-NLS-2$
            .get("ChartType.ResourcesPlacement").getAsString()); //$NON-NLS-1$
        assertTrue(schema.getDefaultSettings().getOutputParameters().getItems().get(0).getValues().get(0)
            instanceof EnumValue);
        assertTrue(schema.getDefaultSettings().getOutputParameters().getItems().get(1).getValues().get(0)
            instanceof EnumValue);
        assertTrue(result.changedPaths.toString().contains("/totalFields")); //$NON-NLS-1$
        assertEquals(after, DcsSettingsWriter.read(schema, "default", null)); //$NON-NLS-1$
    }

    @Test
    public void testVariantIsCreatedAndDefaultSettingsArePreserved()
    {
        DataCompositionSchema schema = schema();
        DcsSettingsWriter.Result initial = DcsSettingsWriter.apply(schema,
            json("{\"selection\":[{\"field\":\"DefaultField\"}]}")); //$NON-NLS-1$
        assertFalse(initial.error, initial.hasError());
        DataCompositionSettings defaultSettings = schema.getDefaultSettings();

        DcsSettingsWriter.Result variantResult = DcsSettingsWriter.apply(schema,
            json("{\"target\":\"variant\",\"variantName\":\"ByWarehouse\",\"variantPresentation\":\"By warehouse\",\"selection\":[{\"field\":\"Warehouse\"}]}")); //$NON-NLS-1$
        assertFalse(variantResult.error, variantResult.hasError());
        assertSame(defaultSettings, schema.getDefaultSettings());
        assertEquals(1, schema.getSettingsVariants().size());
        SettingsVariant variant = schema.getSettingsVariants().get(0);
        assertEquals("ByWarehouse", variant.getName()); //$NON-NLS-1$
        assertEquals("By warehouse", variant.getPresentation().getValue()); //$NON-NLS-1$
        assertEquals("DefaultField", DcsSettingsWriter.read(schema, "default", null) //$NON-NLS-1$ //$NON-NLS-2$
            .getAsJsonArray("selection").get(0).getAsJsonObject().get("field").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testVariantCannotWriteSchemaWideTotalFields()
    {
        String error = DcsSettingsWriter.validate(json(
            "{\"target\":\"variant\",\"variantName\":\"ByWarehouse\"," //$NON-NLS-1$
                + "\"totalFields\":[{\"dataPath\":\"Amount\",\"expression\":\"SUM(Amount)\"}]}")); //$NON-NLS-1$
        assertTrue(error, error.contains("schema-wide")); //$NON-NLS-1$
    }

    @Test
    public void testSurgicalMutationPreservesUnvisitedContainmentAndIds()
    {
        DataCompositionSchema schema = schema();
        DcsSettingsWriter.apply(schema, json("{\"selection\":[{\"field\":\"Warehouse\"},{\"field\":\"Amount\"}]," //$NON-NLS-1$
            + "\"structure\":[{\"kind\":\"group\",\"id\":\"stable-id\",\"groupFields\":[{\"field\":\"Warehouse\"}]}]}")); //$NON-NLS-1$
        Object untouchedStructure = schema.getDefaultSettings().getItems().get(0);
        Object untouchedWarehouse = schema.getDefaultSettings().getSelection().getItems().get(0);

        DcsSettingsWriter.Result result = DcsSettingsWriter.apply(schema,
            json("{\"mutation\":{\"section\":\"selection\",\"action\":\"upsert\",\"selector\":\"Amount\",\"value\":{\"field\":\"Amount\",\"title\":\"Total\"}}}")); //$NON-NLS-1$
        assertFalse(result.error, result.hasError());
        assertSame(untouchedStructure, schema.getDefaultSettings().getItems().get(0));
        assertSame(untouchedWarehouse, schema.getDefaultSettings().getSelection().getItems().get(0));
        assertEquals("stable-id", ((DataCompositionGroup)untouchedStructure).getId()); //$NON-NLS-1$
        assertEquals("Total", result.after.getAsJsonArray("selection").get(1).getAsJsonObject() //$NON-NLS-1$ //$NON-NLS-2$
            .get("title").getAsString()); //$NON-NLS-1$
        assertFalse(result.changedPaths.toString().contains("/structure")); //$NON-NLS-1$
    }

    @Test
    public void testStructureUpsertPreservesSelectorIdentity()
    {
        DataCompositionSchema schema = schema();
        DcsSettingsWriter.apply(schema,
            json("{\"structure\":[{\"kind\":\"group\",\"id\":\"stable-id\",\"name\":\"Before\"}]}")); //$NON-NLS-1$

        DcsSettingsWriter.Result replaced = DcsSettingsWriter.apply(schema,
            json("{\"mutation\":{\"section\":\"structure\",\"action\":\"upsert\",\"selector\":\"stable-id\"," //$NON-NLS-1$
                + "\"value\":{\"kind\":\"group\",\"name\":\"After\"}}}")); //$NON-NLS-1$
        assertFalse(replaced.error, replaced.hasError());
        assertEquals("stable-id", replaced.after.getAsJsonArray("structure").get(0).getAsJsonObject() //$NON-NLS-1$ //$NON-NLS-2$
            .get("id").getAsString()); //$NON-NLS-1$

        DcsSettingsWriter.Result inserted = DcsSettingsWriter.apply(schema,
            json("{\"mutation\":{\"section\":\"structure\",\"action\":\"upsert\",\"selector\":\"new-id\"," //$NON-NLS-1$
                + "\"value\":{\"kind\":\"group\",\"name\":\"New\"}}}")); //$NON-NLS-1$
        assertFalse(inserted.error, inserted.hasError());
        assertEquals("new-id", inserted.after.getAsJsonArray("structure").get(1).getAsJsonObject() //$NON-NLS-1$ //$NON-NLS-2$
            .get("id").getAsString()); //$NON-NLS-1$
    }

    @Test
    public void testConditionalAppearanceUpsertPreservesSelectorIdentity()
    {
        DataCompositionSchema schema = schema();
        DcsSettingsWriter.apply(schema, json("{\"conditionalAppearance\":[{\"fields\":[\"Amount\"]," //$NON-NLS-1$
            + "\"userSetting\":{\"id\":\"stable-rule\"}}]}")); //$NON-NLS-1$

        DcsSettingsWriter.Result result = DcsSettingsWriter.apply(schema,
            json("{\"mutation\":{\"section\":\"conditionalAppearance\",\"action\":\"upsert\"," //$NON-NLS-1$
                + "\"selector\":\"stable-rule\",\"value\":{\"fields\":[\"Warehouse\"]}}}")); //$NON-NLS-1$
        assertFalse(result.error, result.hasError());
        assertEquals("stable-rule", result.after.getAsJsonArray("conditionalAppearance").get(0).getAsJsonObject() //$NON-NLS-1$ //$NON-NLS-2$
            .getAsJsonObject("userSetting").get("id").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testMutationIdentityConflictsAndNestedDuplicateStructureIdsAreRejected()
    {
        String structureConflict = DcsSettingsWriter.validate(json(
            "{\"mutation\":{\"section\":\"structure\",\"action\":\"upsert\",\"selector\":\"selected\"," //$NON-NLS-1$
                + "\"value\":{\"kind\":\"group\",\"id\":\"different\"}}}")); //$NON-NLS-1$
        assertTrue(structureConflict, structureConflict.contains("must match mutation.selector")); //$NON-NLS-1$

        String appearanceConflict = DcsSettingsWriter.validate(json(
            "{\"mutation\":{\"section\":\"conditionalAppearance\",\"action\":\"upsert\",\"selector\":\"selected\"," //$NON-NLS-1$
                + "\"value\":{\"fields\":[\"Amount\"],\"userSetting\":{\"id\":\"different\"}}}}")); //$NON-NLS-1$
        assertTrue(appearanceConflict, appearanceConflict.contains("must match mutation.selector")); //$NON-NLS-1$

        for (String spec : new String[] {
            "{\"mutation\":{\"section\":\"selection\",\"action\":\"upsert\",\"selector\":\"selected\",\"value\":{\"field\":\"different\"}}}", //$NON-NLS-1$
            "{\"mutation\":{\"section\":\"filter\",\"action\":\"upsert\",\"selector\":\"selected\",\"value\":{\"left\":\"different\",\"comparison\":\"EQUAL\"}}}", //$NON-NLS-1$
            "{\"mutation\":{\"section\":\"order\",\"action\":\"upsert\",\"selector\":\"selected\",\"value\":{\"field\":\"different\"}}}", //$NON-NLS-1$
            "{\"mutation\":{\"section\":\"totalFields\",\"action\":\"upsert\",\"selector\":\"selected\",\"value\":{\"dataPath\":\"different\",\"expression\":\"SUM(Amount)\"}}}" //$NON-NLS-1$
        })
        {
            String conflict = DcsSettingsWriter.validate(json(spec));
            assertTrue(conflict, conflict.contains("must match mutation.selector")); //$NON-NLS-1$
        }

        String filterGroup = DcsSettingsWriter.validate(json("{\"mutation\":{\"section\":\"filter\"," //$NON-NLS-1$
            + "\"action\":\"upsert\",\"selector\":\"Amount\",\"value\":{\"kind\":\"group\"," //$NON-NLS-1$
            + "\"items\":[]}}}")); //$NON-NLS-1$
        assertTrue(filterGroup, filterGroup.contains("condition values only")); //$NON-NLS-1$

        String duplicate = DcsSettingsWriter.validate(json("{\"structure\":[" //$NON-NLS-1$
            + "{\"kind\":\"group\",\"id\":\"duplicate\",\"items\":[{\"kind\":\"group\",\"id\":\"child\"}]}," //$NON-NLS-1$
            + "{\"kind\":\"group\",\"id\":\"other\",\"items\":[{\"kind\":\"chart\",\"id\":\"duplicate\"}]}]}")); //$NON-NLS-1$
        assertTrue(duplicate, duplicate.contains("Duplicate structure id: duplicate")); //$NON-NLS-1$
    }

    @Test
    public void testStructureUpsertChecksInjectedAndUntouchedIdsGlobally()
    {
        DataCompositionSchema schema = schema();
        DcsSettingsWriter.apply(schema, json("{\"structure\":[" //$NON-NLS-1$
            + "{\"kind\":\"group\",\"id\":\"stable\",\"items\":[{\"kind\":\"group\",\"id\":\"owned\"}]}," //$NON-NLS-1$
            + "{\"kind\":\"group\",\"id\":\"other\",\"items\":[{\"kind\":\"group\",\"id\":\"outside\"}]}]}")); //$NON-NLS-1$
        String before = DcsSettingsWriter.read(schema, "default", null).toString(); //$NON-NLS-1$

        DcsSettingsWriter.Result injectedDuplicate = DcsSettingsWriter.apply(schema, json(
            "{\"mutation\":{\"section\":\"structure\",\"action\":\"upsert\",\"selector\":\"new\"," //$NON-NLS-1$
                + "\"value\":{\"kind\":\"group\",\"items\":[{\"kind\":\"group\",\"id\":\"new\"}]}}}")); //$NON-NLS-1$
        assertTrue(injectedDuplicate.hasError());
        assertTrue(injectedDuplicate.error, injectedDuplicate.error.contains("Duplicate structure id: new")); //$NON-NLS-1$
        assertEquals(before, DcsSettingsWriter.read(schema, "default", null).toString()); //$NON-NLS-1$

        DcsSettingsWriter.Result untouchedDuplicate = DcsSettingsWriter.apply(schema, json(
            "{\"mutation\":{\"section\":\"structure\",\"action\":\"upsert\",\"selector\":\"stable\"," //$NON-NLS-1$
                + "\"value\":{\"kind\":\"group\",\"items\":[{\"kind\":\"group\",\"id\":\"outside\"}]}}}")); //$NON-NLS-1$
        assertTrue(untouchedDuplicate.hasError());
        assertTrue(untouchedDuplicate.error, untouchedDuplicate.error.contains("Duplicate structure id: outside")); //$NON-NLS-1$
        assertEquals(before, DcsSettingsWriter.read(schema, "default", null).toString()); //$NON-NLS-1$

        DcsSettingsWriter.Result validReplacement = DcsSettingsWriter.apply(schema, json(
            "{\"mutation\":{\"section\":\"structure\",\"action\":\"upsert\",\"selector\":\"stable\"," //$NON-NLS-1$
                + "\"value\":{\"kind\":\"group\",\"items\":[{\"kind\":\"group\",\"id\":\"owned\"}]}}}")); //$NON-NLS-1$
        assertFalse(validReplacement.error, validReplacement.hasError());
    }

    @Test
    public void testInvalidEnumIsRejectedBeforeAnyMutation()
    {
        DataCompositionSchema schema = schema();
        DcsSettingsWriter.Result result = DcsSettingsWriter.apply(schema,
            json("{\"selection\":[{\"field\":\"WouldMutate\"}],\"order\":[{\"field\":\"Amount\",\"direction\":\"SIDEWAYS\"}]}")); //$NON-NLS-1$
        assertTrue(result.hasError());
        assertNull(schema.getDefaultSettings());
        assertTrue(schema.getTotalFields().isEmpty());
    }

    @Test
    public void testUnsupportedTypedParametersAreRejectedBeforeAnyMutation()
    {
        DataCompositionSchema schema = schema();
        DcsSettingsWriter.Result output = DcsSettingsWriter.apply(schema,
            json("{\"selection\":[{\"field\":\"WouldMutate\"}],\"output\":{\"ShowTotals\":true}}")); //$NON-NLS-1$
        assertTrue(output.hasError());
        assertTrue(output.error, output.error.contains("Unsupported output parameter 'ShowTotals'")); //$NON-NLS-1$
        assertNull(schema.getDefaultSettings());

        DcsSettingsWriter.Result appearance = DcsSettingsWriter.apply(schema,
            json("{\"selection\":[{\"field\":\"WouldMutate\"}],\"conditionalAppearance\":[" //$NON-NLS-1$
                + "{\"fields\":[\"Amount\"],\"appearance\":{\"TextColor\":\"Red\"}}]}")); //$NON-NLS-1$
        assertTrue(appearance.hasError());
        assertTrue(appearance.error, appearance.error.contains("appearance parameter values are not supported yet")); //$NON-NLS-1$
        assertNull(schema.getDefaultSettings());
    }

    @Test
    public void testInvalidAndAmbiguousSelectorsAreRejected()
    {
        DataCompositionSchema schema = schema();
        DcsSettingsWriter.apply(schema, json("{\"filter\":[{\"left\":\"Amount\",\"comparison\":\"GREATER\",\"right\":0}," //$NON-NLS-1$
            + "{\"left\":\"Amount\",\"comparison\":\"LESS\",\"right\":100}]}")); //$NON-NLS-1$
        String before = DcsSettingsWriter.read(schema, "default", null).toString(); //$NON-NLS-1$
        DcsSettingsWriter.Result ambiguous = DcsSettingsWriter.apply(schema,
            json("{\"mutation\":{\"section\":\"filter\",\"action\":\"remove\",\"selector\":\"Amount\"}}")); //$NON-NLS-1$
        assertTrue(ambiguous.hasError());
        assertEquals(before, DcsSettingsWriter.read(schema, "default", null).toString()); //$NON-NLS-1$

        DcsSettingsWriter.Result missing = DcsSettingsWriter.apply(schema,
            json("{\"mutation\":{\"section\":\"order\",\"action\":\"remove\",\"selector\":\"Unknown\"}}")); //$NON-NLS-1$
        assertTrue(missing.hasError());
    }

    @Test
    public void testDcsWriterIntegrationAndFactoryKinds()
    {
        DataCompositionSchema schema = schema();
        DcsWriter.Result result = DcsWriter.apply(schema,
            json("{\"settings\":{\"selection\":[{\"field\":\"Amount\"}],\"totalFields\":[{\"dataPath\":\"Amount\",\"expression\":\"SUM(Amount)\"}]}}"), null); //$NON-NLS-1$
        assertFalse(result.error, result.hasError());
        assertNotNull(result.settingsAfter);
        assertEquals("com._1c.g5.v8.dt.dcs.model.settings.impl", //$NON-NLS-1$
            schema.getDefaultSettings().getClass().getPackageName());
        assertEquals("com._1c.g5.v8.dt.dcs.model.schema.impl", //$NON-NLS-1$
            schema.getTotalFields().get(0).getClass().getPackageName());
    }
}
