/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.emf.common.util.Enumerator;

import com._1c.g5.v8.dt.dcs.model.core.DataCompositionField;
import com._1c.g5.v8.dt.dcs.model.core.DataCompositionGroupType;
import com._1c.g5.v8.dt.dcs.model.core.DataCompositionParameter;
import com._1c.g5.v8.dt.dcs.model.core.DataCompositionParameterValue;
import com._1c.g5.v8.dt.dcs.model.core.DataCompositionSortDirection;
import com._1c.g5.v8.dt.dcs.model.core.ParameterValues;
import com._1c.g5.v8.dt.dcs.model.core.Presentation;
import com._1c.g5.v8.dt.dcs.model.schema.DataCompositionSchema;
import com._1c.g5.v8.dt.dcs.model.schema.DataCompositionSchemaTotalField;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionAppearanceField;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionAppearanceFields;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionChart;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionChartGroup;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionComparisonType;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionConditionalAppearance;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionConditionalAppearanceItem;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionFilter;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionFilterItem;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionFilterItemGroup;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionFilterItemsGroupType;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionGroup;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionGroupField;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionGroupFields;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionOrder;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionOrderItem;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionResourcesPlacement;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionResourcesPlacementInChart;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionSelectedField;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionSelectedFieldGroup;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionSelectedFields;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionSettings;
import com._1c.g5.v8.dt.dcs.model.settings.DataCompositionSettingsItemViewMode;
import com._1c.g5.v8.dt.dcs.model.settings.FilterItem;
import com._1c.g5.v8.dt.dcs.model.settings.GroupItem;
import com._1c.g5.v8.dt.dcs.model.settings.OrderItem;
import com._1c.g5.v8.dt.dcs.model.settings.SelectedItem;
import com._1c.g5.v8.dt.dcs.model.settings.SettingsVariant;
import com._1c.g5.v8.dt.dcs.model.settings.StructureItem;
import com._1c.g5.v8.dt.mcore.BooleanValue;
import com._1c.g5.v8.dt.mcore.EnumValue;
import com._1c.g5.v8.dt.mcore.McoreFactory;
import com._1c.g5.v8.dt.mcore.NumberValue;
import com._1c.g5.v8.dt.mcore.StringValue;
import com._1c.g5.v8.dt.mcore.Value;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Native-model writer and normalized reader for the advanced part of a DCS schema: total fields,
 * default/variant settings, selection, filter, order, structure/group/chart items, conditional
 * appearance, user-setting exposure, and output placement parameters.
 *
 * <p>The writer never reads or writes XML. It mutates only objects created by the three EDT DCS factories.
 * The caller owns the BM transaction and export boundary. Every request is validated and converted to an
 * immutable plan before {@link #apply(DataCompositionSchema, JsonObject)} touches the model. Omitted
 * sections are preserved. A supplied section replaces only that containment. The optional
 * {@code mutation} form changes one selected item and therefore preserves unvisited containment and IDs.</p>
 */
public final class DcsSettingsWriter
{
    private static final com._1c.g5.v8.dt.dcs.model.settings.DcsFactory SETTINGS_FACTORY =
        com._1c.g5.v8.dt.dcs.model.settings.DcsFactory.eINSTANCE;
    private static final com._1c.g5.v8.dt.dcs.model.core.DcsFactory CORE_FACTORY =
        com._1c.g5.v8.dt.dcs.model.core.DcsFactory.eINSTANCE;

    private DcsSettingsWriter()
    {
        // Utility class.
    }

    /** Result of one advanced-settings write. */
    public static final class Result
    {
        public final String error;
        public final JsonObject before;
        public final JsonObject after;
        public final JsonArray changedPaths;

        private Result(String error, JsonObject before, JsonObject after, JsonArray changedPaths)
        {
            this.error = error;
            this.before = before;
            this.after = after;
            this.changedPaths = changedPaths;
        }

        static Result failed(String error)
        {
            return new Result(ToolResult.error(error).toJson(), null, null, new JsonArray());
        }

        static Result ok(JsonObject before, JsonObject after)
        {
            return new Result(null, before, after, diff(before, after));
        }

        public boolean hasError()
        {
            return error != null;
        }
    }

    /** Applies a fully validated settings specification and returns normalized before/after snapshots. */
    public static Result apply(DataCompositionSchema schema, JsonObject spec)
    {
        if (schema == null)
        {
            return Result.failed("DCS schema is not available."); //$NON-NLS-1$
        }
        Parse parsed = parse(spec);
        if (parsed.error != null)
        {
            return Result.failed(parsed.error);
        }
        Plan plan = parsed.plan;
        JsonObject before = read(schema, plan.target, plan.variantName);

        String preflight = preflightMutation(schema, findSettings(schema, plan.target, plan.variantName),
            plan.mutation);
        if (preflight != null)
        {
            return Result.failed(preflight);
        }

        applyTotalFields(schema, plan);
        DataCompositionSettings settings = resolveSettings(schema, plan);
        if (plan.selection != null)
        {
            settings.setSelection(buildSelection(plan.selection));
        }
        if (plan.filter != null)
        {
            settings.setFilter(buildFilter(plan.filter));
        }
        if (plan.order != null)
        {
            settings.setOrder(buildOrder(plan.order));
        }
        if (plan.structure != null)
        {
            settings.getItems().clear();
            settings.getItems().addAll(buildStructure(plan.structure));
        }
        if (plan.appearance != null)
        {
            settings.setConditionalAppearance(buildAppearance(plan.appearance));
        }
        if (plan.userSettings != null)
        {
            applyUserSettings(settings, plan.userSettings);
        }
        if (plan.output != null)
        {
            settings.setOutputParameters(SETTINGS_FACTORY.createDataCompositionOutputParameterValues());
            applyOutput(settings.getOutputParameters(), plan.output, NativeParameterKind.OUTPUT);
        }
        if (plan.mutation != null)
        {
            String mutationError = applyMutation(schema, settings, plan.mutation);
            if (mutationError != null)
            {
                return Result.failed(mutationError);
            }
        }
        JsonObject after = read(schema, plan.target, plan.variantName);
        return Result.ok(before, after);
    }

    /** Validates the complete request without touching a model; returns a plain error or {@code null}. */
    public static String validate(JsonObject spec)
    {
        return parse(spec).error;
    }

    /** Normalized, stable JSON read-back for every supported write. */
    public static JsonObject read(DataCompositionSchema schema, String target, String variantName)
    {
        JsonObject root = new JsonObject();
        root.addProperty("target", target == null ? "default" : target); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if ("variant".equals(target)) //$NON-NLS-1$
        {
            root.addProperty("variantName", variantName); //$NON-NLS-1$
        }
        // Total fields belong to the schema, not to an individual settings variant.
        if (!"variant".equals(target)) //$NON-NLS-1$
        {
            root.add("totalFields", readTotalFields(schema)); //$NON-NLS-1$
        }
        DataCompositionSettings settings = findSettings(schema, target, variantName);
        if (settings == null)
        {
            root.add("selection", new JsonArray()); //$NON-NLS-1$
            root.add("filter", new JsonArray()); //$NON-NLS-1$
            root.add("order", new JsonArray()); //$NON-NLS-1$
            root.add("structure", new JsonArray()); //$NON-NLS-1$
            root.add("conditionalAppearance", new JsonArray()); //$NON-NLS-1$
            return root;
        }
        root.add("selection", readSelection(settings.getSelection())); //$NON-NLS-1$
        root.add("filter", readFilter(settings.getFilter())); //$NON-NLS-1$
        root.add("order", readOrder(settings.getOrder())); //$NON-NLS-1$
        root.add("structure", readStructure(settings.getItems())); //$NON-NLS-1$
        root.add("conditionalAppearance", readAppearance(settings.getConditionalAppearance())); //$NON-NLS-1$
        root.add("userSettings", readSettingsExposure(settings)); //$NON-NLS-1$
        root.add("output", readOutput(settings.getOutputParameters())); //$NON-NLS-1$
        return root;
    }

    // ---- parsing ----------------------------------------------------------------------------

    private static Parse parse(JsonObject spec)
    {
        if (spec == null)
        {
            return Parse.failed("settings must be a JSON object."); //$NON-NLS-1$
        }
        Plan plan = new Plan();
        plan.target = string(spec, "target", "default"); //$NON-NLS-1$ //$NON-NLS-2$
        if (!"default".equals(plan.target) && !"variant".equals(plan.target)) //$NON-NLS-1$ //$NON-NLS-2$
        {
            return Parse.failed("target must be 'default' or 'variant'."); //$NON-NLS-1$
        }
        plan.variantName = string(spec, "variantName", null); //$NON-NLS-1$
        if ("variant".equals(plan.target) && empty(plan.variantName)) //$NON-NLS-1$
        {
            return Parse.failed("variantName is required when target='variant'."); //$NON-NLS-1$
        }
        plan.variantPresentation = string(spec, "variantPresentation", null); //$NON-NLS-1$
        try
        {
            plan.totalFields = optionalObjectArray(spec, "totalFields"); //$NON-NLS-1$
            plan.selection = optionalObjectArray(spec, "selection"); //$NON-NLS-1$
            plan.filter = optionalObjectArray(spec, "filter"); //$NON-NLS-1$
            plan.order = optionalObjectArray(spec, "order"); //$NON-NLS-1$
            plan.structure = optionalObjectArray(spec, "structure"); //$NON-NLS-1$
            plan.appearance = optionalObjectArray(spec, "conditionalAppearance"); //$NON-NLS-1$
            plan.userSettings = optionalObject(spec, "userSettings"); //$NON-NLS-1$
            plan.output = optionalObject(spec, "output"); //$NON-NLS-1$
            plan.mutation = parseMutation(optionalObject(spec, "mutation")); //$NON-NLS-1$
            validateTotalFields(plan.totalFields);
            validateSelection(plan.selection, "selection"); //$NON-NLS-1$
            validateFilter(plan.filter, "filter"); //$NON-NLS-1$
            validateOrder(plan.order, "order"); //$NON-NLS-1$
            validateStructure(plan.structure, "structure"); //$NON-NLS-1$
            validateAppearance(plan.appearance);
            validateExposure(plan.userSettings, "userSettings"); //$NON-NLS-1$
            validateOutput(plan.output, NativeParameterKind.OUTPUT);
            validateMutation(plan.mutation);
            validateMutationMix(plan);
            if ("variant".equals(plan.target) && (plan.totalFields != null //$NON-NLS-1$
                || (plan.mutation != null && "totalFields".equals(plan.mutation.section)))) //$NON-NLS-1$
            {
                throw bad("totalFields are schema-wide and can only be changed with target='default'."); //$NON-NLS-1$
            }
        }
        catch (IllegalArgumentException e)
        {
            return Parse.failed(e.getMessage());
        }
        if (plan.totalFields == null && plan.selection == null && plan.filter == null && plan.order == null
            && plan.structure == null && plan.appearance == null && plan.userSettings == null
            && plan.output == null && plan.mutation == null && plan.variantPresentation == null)
        {
            return Parse.failed("settings contains no supported change."); //$NON-NLS-1$
        }
        return Parse.ok(plan);
    }

    private static void validateTotalFields(List<JsonObject> items)
    {
        if (items == null)
        {
            return;
        }
        Set<String> paths = new HashSet<>();
        for (int i = 0; i < items.size(); i++)
        {
            JsonObject item = items.get(i);
            String path = required(item, "dataPath", "totalFields[" + i + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            required(item, "expression", "totalFields[" + i + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (!paths.add(path))
            {
                throw bad("Duplicate total field dataPath: " + path); //$NON-NLS-1$
            }
            optionalStringArray(item, "groups"); //$NON-NLS-1$
        }
    }

    private static void validateSelection(List<JsonObject> items, String where)
    {
        if (items == null)
        {
            return;
        }
        for (int i = 0; i < items.size(); i++)
        {
            JsonObject item = items.get(i);
            String at = where + "[" + i + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            String kind = string(item, "kind", "field"); //$NON-NLS-1$ //$NON-NLS-2$
            if (!"field".equals(kind) && !"group".equals(kind)) //$NON-NLS-1$ //$NON-NLS-2$
            {
                throw bad(at + ".kind must be 'field' or 'group'."); //$NON-NLS-1$
            }
            required(item, "field", at); //$NON-NLS-1$
            optionalBoolean(item, "use"); //$NON-NLS-1$
            if ("group".equals(kind)) //$NON-NLS-1$
            {
                List<JsonObject> nested = requiredObjectArray(item, "items", at); //$NON-NLS-1$
                validateSelection(nested, at + ".items"); //$NON-NLS-1$
            }
        }
    }

    private static void validateFilter(List<JsonObject> items, String where)
    {
        if (items == null)
        {
            return;
        }
        for (int i = 0; i < items.size(); i++)
        {
            JsonObject item = items.get(i);
            String at = where + "[" + i + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            String kind = string(item, "kind", "condition"); //$NON-NLS-1$ //$NON-NLS-2$
            optionalBoolean(item, "use"); //$NON-NLS-1$
            if ("condition".equals(kind)) //$NON-NLS-1$
            {
                required(item, "left", at); //$NON-NLS-1$
                enumValue(DataCompositionComparisonType.values(), required(item, "comparison", at), //$NON-NLS-1$
                    at + ".comparison"); //$NON-NLS-1$
                if (item.has("right")) //$NON-NLS-1$
                {
                    valueArray(item.get("right"), at + ".right"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            else if ("group".equals(kind)) //$NON-NLS-1$
            {
                enumValue(DataCompositionFilterItemsGroupType.values(),
                    string(item, "groupType", "AND_GROUP"), at + ".groupType"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                List<JsonObject> nested = requiredObjectArray(item, "items", at); //$NON-NLS-1$
                validateFilter(nested, at + ".items"); //$NON-NLS-1$
            }
            else
            {
                throw bad(at + ".kind must be 'condition' or 'group'."); //$NON-NLS-1$
            }
        }
    }

    private static void validateOrder(List<JsonObject> items, String where)
    {
        if (items == null)
        {
            return;
        }
        for (int i = 0; i < items.size(); i++)
        {
            JsonObject item = items.get(i);
            String at = where + "[" + i + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            required(item, "field", at); //$NON-NLS-1$
            enumValue(DataCompositionSortDirection.values(), string(item, "direction", "ASC"), //$NON-NLS-1$ //$NON-NLS-2$
                at + ".direction"); //$NON-NLS-1$
            optionalBoolean(item, "use"); //$NON-NLS-1$
        }
    }

    private static void validateStructure(List<JsonObject> items, String where)
    {
        validateStructure(items, where, new HashSet<>());
    }

    private static void validateStructure(List<JsonObject> items, String where, Set<String> ids)
    {
        if (items == null)
        {
            return;
        }
        for (int i = 0; i < items.size(); i++)
        {
            JsonObject item = items.get(i);
            String at = where + "[" + i + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            String kind = string(item, "kind", "group"); //$NON-NLS-1$ //$NON-NLS-2$
            if (!"group".equals(kind) && !"chart".equals(kind)) //$NON-NLS-1$ //$NON-NLS-2$
            {
                throw bad(at + ".kind must be 'group' or 'chart'."); //$NON-NLS-1$
            }
            String id = string(item, "id", null); //$NON-NLS-1$
            if (id != null && !ids.add(id))
            {
                throw bad("Duplicate structure id: " + id); //$NON-NLS-1$
            }
            optionalBoolean(item, "use"); //$NON-NLS-1$
            validateSelection(optionalObjectArray(item, "selection"), at + ".selection"); //$NON-NLS-1$ //$NON-NLS-2$
            validateFilter(optionalObjectArray(item, "filter"), at + ".filter"); //$NON-NLS-1$ //$NON-NLS-2$
            validateOrder(optionalObjectArray(item, "order"), at + ".order"); //$NON-NLS-1$ //$NON-NLS-2$
            validateAppearance(optionalObjectArray(item, "conditionalAppearance")); //$NON-NLS-1$
            validateExposure(optionalObject(item, "userSetting"), at + ".userSetting"); //$NON-NLS-1$ //$NON-NLS-2$
            if ("group".equals(kind)) //$NON-NLS-1$
            {
                validateGroupFields(optionalObjectArray(item, "groupFields"), at); //$NON-NLS-1$
                validateStructure(optionalObjectArray(item, "items"), at + ".items", ids); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else
            {
                validateChartGroups(optionalObjectArray(item, "points"), at + ".points"); //$NON-NLS-1$ //$NON-NLS-2$
                validateChartGroups(optionalObjectArray(item, "series"), at + ".series"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            validateOutput(optionalObject(item, "output"), //$NON-NLS-1$
                "chart".equals(kind) ? NativeParameterKind.CHART_OUTPUT : NativeParameterKind.GROUP_OUTPUT); //$NON-NLS-1$
        }
    }

    private static void validateChartGroups(List<JsonObject> groups, String where)
    {
        if (groups == null)
        {
            return;
        }
        for (int i = 0; i < groups.size(); i++)
        {
            JsonObject item = groups.get(i);
            String at = where + "[" + i + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            validateGroupFields(optionalObjectArray(item, "groupFields"), at); //$NON-NLS-1$
            validateChartGroups(optionalObjectArray(item, "items"), at + ".items"); //$NON-NLS-1$ //$NON-NLS-2$
            validateSelection(optionalObjectArray(item, "selection"), at + ".selection"); //$NON-NLS-1$ //$NON-NLS-2$
            validateFilter(optionalObjectArray(item, "filter"), at + ".filter"); //$NON-NLS-1$ //$NON-NLS-2$
            validateOrder(optionalObjectArray(item, "order"), at + ".order"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static void validateGroupFields(List<JsonObject> fields, String where)
    {
        if (fields == null)
        {
            return;
        }
        for (int i = 0; i < fields.size(); i++)
        {
            JsonObject field = fields.get(i);
            String at = where + ".groupFields[" + i + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            required(field, "field", at); //$NON-NLS-1$
            enumValue(DataCompositionGroupType.values(), string(field, "groupType", "ITEMS"), //$NON-NLS-1$ //$NON-NLS-2$
                at + ".groupType"); //$NON-NLS-1$
        }
    }

    private static void validateAppearance(List<JsonObject> items)
    {
        if (items == null)
        {
            return;
        }
        for (int i = 0; i < items.size(); i++)
        {
            JsonObject item = items.get(i);
            String at = "conditionalAppearance[" + i + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            List<String> fields = optionalStringArray(item, "fields"); //$NON-NLS-1$
            if (fields == null || fields.isEmpty())
            {
                throw bad(at + ".fields must contain at least one field path."); //$NON-NLS-1$
            }
            validateFilter(optionalObjectArray(item, "filter"), at + ".filter"); //$NON-NLS-1$ //$NON-NLS-2$
            JsonObject appearance = optionalObject(item, "appearance"); //$NON-NLS-1$
            if (appearance != null && !appearance.isEmpty())
            {
                throw bad(at + ".appearance parameter values are not supported yet; omit appearance " //$NON-NLS-1$
                    + "until a public native value resolver is available."); //$NON-NLS-1$
            }
            validateExposure(optionalObject(item, "userSetting"), at + ".userSetting"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static void validateExposure(JsonObject exposure, String where)
    {
        if (exposure == null)
        {
            return;
        }
        if (exposure.has("viewMode")) //$NON-NLS-1$
        {
            enumValue(DataCompositionSettingsItemViewMode.values(), string(exposure, "viewMode", null), //$NON-NLS-1$
                where + ".viewMode"); //$NON-NLS-1$
        }
        for (String section : new String[] {"selection", "filter", "order", "items"}) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        {
            if (exposure.has(section))
            {
                validateExposure(optionalObject(exposure, section), where + "." + section); //$NON-NLS-1$
            }
        }
    }

    private static void validateOutput(JsonObject output, NativeParameterKind kind)
    {
        if (output == null)
        {
            return;
        }
        for (String key : output.keySet())
        {
            JsonElement value = output.get(key);
            if (empty(key))
            {
                throw bad("output parameter names must be non-empty."); //$NON-NLS-1$
            }
            if (!supportsOutput(kind, key))
            {
                throw unsupportedOutput(kind, key);
            }
            if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString())
            {
                throw bad("Supported output placement values must be enum literal strings."); //$NON-NLS-1$
            }
            outputEnum(kind, key, value.getAsString());
        }
    }

    private static Mutation parseMutation(JsonObject object)
    {
        if (object == null)
        {
            return null;
        }
        Mutation mutation = new Mutation();
        mutation.section = string(object, "section", null); //$NON-NLS-1$
        mutation.action = string(object, "action", null); //$NON-NLS-1$
        mutation.selector = string(object, "selector", null); //$NON-NLS-1$
        mutation.value = optionalObject(object, "value"); //$NON-NLS-1$
        return mutation;
    }

    private static void validateMutation(Mutation mutation)
    {
        if (mutation == null)
        {
            return;
        }
        if (!Set.of("selection", "filter", "order", "structure", "conditionalAppearance", "totalFields") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
            .contains(mutation.section))
        {
            throw bad("mutation.section must be selection, filter, order, structure, " //$NON-NLS-1$
                + "conditionalAppearance, or totalFields."); //$NON-NLS-1$
        }
        if (!"upsert".equals(mutation.action) && !"remove".equals(mutation.action)) //$NON-NLS-1$ //$NON-NLS-2$
        {
            throw bad("mutation.action must be 'upsert' or 'remove'."); //$NON-NLS-1$
        }
        if (empty(mutation.selector))
        {
            throw bad("mutation.selector is required."); //$NON-NLS-1$
        }
        if ("upsert".equals(mutation.action) && mutation.value == null) //$NON-NLS-1$
        {
            throw bad("mutation.value is required for upsert."); //$NON-NLS-1$
        }
        if (mutation.value != null)
        {
            List<JsonObject> one = List.of(mutation.value);
            switch (mutation.section)
            {
                case "selection": validateSelection(one, "mutation.value"); break; //$NON-NLS-1$ //$NON-NLS-2$
                case "filter": validateFilter(one, "mutation.value"); break; //$NON-NLS-1$ //$NON-NLS-2$
                case "order": validateOrder(one, "mutation.value"); break; //$NON-NLS-1$ //$NON-NLS-2$
                case "structure": validateStructure(one, "mutation.value"); break; //$NON-NLS-1$ //$NON-NLS-2$
                case "conditionalAppearance": validateAppearance(one); break; //$NON-NLS-1$
                case "totalFields": validateTotalFields(one); break; //$NON-NLS-1$
                default: break;
            }
            validateMutationIdentity(mutation);
        }
    }

    private static void validateMutationIdentity(Mutation mutation)
    {
        if (!"upsert".equals(mutation.action)) //$NON-NLS-1$
        {
            return;
        }
        if ("selection".equals(mutation.section)) //$NON-NLS-1$
        {
            requireMutationIdentity(mutation, "field"); //$NON-NLS-1$
        }
        else if ("filter".equals(mutation.section)) //$NON-NLS-1$
        {
            if ("group".equals(string(mutation.value, "kind", "condition"))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            {
                throw bad("filter mutation upsert supports condition values only; groups have no " //$NON-NLS-1$
                    + "stable left-field selector."); //$NON-NLS-1$
            }
            requireMutationIdentity(mutation, "left"); //$NON-NLS-1$
        }
        else if ("order".equals(mutation.section)) //$NON-NLS-1$
        {
            requireMutationIdentity(mutation, "field"); //$NON-NLS-1$
        }
        else if ("totalFields".equals(mutation.section)) //$NON-NLS-1$
        {
            requireMutationIdentity(mutation, "dataPath"); //$NON-NLS-1$
        }
        else if ("structure".equals(mutation.section)) //$NON-NLS-1$
        {
            String id = string(mutation.value, "id", null); //$NON-NLS-1$
            if (id != null && !mutation.selector.equals(id))
            {
                throw bad("mutation.value.id must match mutation.selector for structure upsert."); //$NON-NLS-1$
            }
        }
        else if ("conditionalAppearance".equals(mutation.section)) //$NON-NLS-1$
        {
            JsonObject exposure = optionalObject(mutation.value, "userSetting"); //$NON-NLS-1$
            String id = exposure == null ? null : string(exposure, "id", null); //$NON-NLS-1$
            if (id != null && !mutation.selector.equals(id))
            {
                throw bad("mutation.value.userSetting.id must match mutation.selector for " //$NON-NLS-1$
                    + "conditionalAppearance upsert."); //$NON-NLS-1$
            }
        }
    }

    private static void requireMutationIdentity(Mutation mutation, String property)
    {
        String value = string(mutation.value, property, null);
        if (!mutation.selector.equals(value))
        {
            throw bad("mutation.value." + property + " must match mutation.selector for " //$NON-NLS-1$ //$NON-NLS-2$
                + mutation.section + " upsert."); //$NON-NLS-1$
        }
    }

    private static void validateMutationMix(Plan plan)
    {
        if (plan.mutation == null)
        {
            return;
        }
        boolean sameSectionReplacement =
            ("selection".equals(plan.mutation.section) && plan.selection != null) //$NON-NLS-1$
                || ("filter".equals(plan.mutation.section) && plan.filter != null) //$NON-NLS-1$
                || ("order".equals(plan.mutation.section) && plan.order != null) //$NON-NLS-1$
                || ("structure".equals(plan.mutation.section) && plan.structure != null) //$NON-NLS-1$
                || ("conditionalAppearance".equals(plan.mutation.section) && plan.appearance != null) //$NON-NLS-1$
                || ("totalFields".equals(plan.mutation.section) && plan.totalFields != null); //$NON-NLS-1$
        if (sameSectionReplacement)
        {
            throw bad("mutation cannot be combined with a full replacement of the same section."); //$NON-NLS-1$
        }
    }

    // ---- model application ------------------------------------------------------------------

    private static DataCompositionSettings resolveSettings(DataCompositionSchema schema, Plan plan)
    {
        if ("default".equals(plan.target)) //$NON-NLS-1$
        {
            if (schema.getDefaultSettings() == null)
            {
                schema.setDefaultSettings(SETTINGS_FACTORY.createDataCompositionSettings());
            }
            return schema.getDefaultSettings();
        }
        SettingsVariant variant = null;
        for (SettingsVariant candidate : schema.getSettingsVariants())
        {
            if (plan.variantName.equals(candidate.getName()))
            {
                variant = candidate;
                break;
            }
        }
        if (variant == null)
        {
            variant = SETTINGS_FACTORY.createSettingsVariant();
            variant.setName(plan.variantName);
            schema.getSettingsVariants().add(variant);
        }
        if (plan.variantPresentation != null)
        {
            variant.setPresentation(title(plan.variantPresentation));
        }
        if (variant.getSettings() == null)
        {
            variant.setSettings(SETTINGS_FACTORY.createDataCompositionSettings());
        }
        return variant.getSettings();
    }

    private static DataCompositionSettings findSettings(DataCompositionSchema schema, String target,
        String variantName)
    {
        if (!"variant".equals(target)) //$NON-NLS-1$
        {
            return schema.getDefaultSettings();
        }
        for (SettingsVariant variant : schema.getSettingsVariants())
        {
            if (variantName != null && variantName.equals(variant.getName()))
            {
                return variant.getSettings();
            }
        }
        return null;
    }

    private static void applyTotalFields(DataCompositionSchema schema, Plan plan)
    {
        if (plan.totalFields == null)
        {
            return;
        }
        schema.getTotalFields().clear();
        for (JsonObject item : plan.totalFields)
        {
            DataCompositionSchemaTotalField field =
                com._1c.g5.v8.dt.dcs.model.schema.DcsFactory.eINSTANCE.createDataCompositionSchemaTotalField();
            field.setDataPath(string(item, "dataPath", null)); //$NON-NLS-1$
            field.setExpression(string(item, "expression", null)); //$NON-NLS-1$
            List<String> groups = optionalStringArray(item, "groups"); //$NON-NLS-1$
            if (groups != null)
            {
                field.getGroups().addAll(groups);
            }
            schema.getTotalFields().add(field);
        }
    }

    private static DataCompositionSelectedFields buildSelection(List<JsonObject> specs)
    {
        DataCompositionSelectedFields selection = SETTINGS_FACTORY.createDataCompositionSelectedFields();
        for (JsonObject spec : specs)
        {
            selection.getItems().add(buildSelectedItem(spec));
        }
        return selection;
    }

    private static SelectedItem buildSelectedItem(JsonObject spec)
    {
        String kind = string(spec, "kind", "field"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("group".equals(kind)) //$NON-NLS-1$
        {
            DataCompositionSelectedFieldGroup group = SETTINGS_FACTORY.createDataCompositionSelectedFieldGroup();
            group.setUse(bool(spec, "use", true)); //$NON-NLS-1$
            group.setField(field(string(spec, "field", null))); //$NON-NLS-1$
            String presentation = string(spec, "title", null); //$NON-NLS-1$
            if (presentation != null)
            {
                group.setTitle(title(presentation));
            }
            for (JsonObject nested : requiredObjectArray(spec, "items", "selection group")) //$NON-NLS-1$ //$NON-NLS-2$
            {
                group.getItems().add(buildSelectedItem(nested));
            }
            return group;
        }
        DataCompositionSelectedField selected = SETTINGS_FACTORY.createDataCompositionSelectedField();
        selected.setUse(bool(spec, "use", true)); //$NON-NLS-1$
        selected.setField(field(string(spec, "field", null))); //$NON-NLS-1$
        String presentation = string(spec, "title", null); //$NON-NLS-1$
        if (presentation != null)
        {
            selected.setTitle(title(presentation));
        }
        return selected;
    }

    private static DataCompositionFilter buildFilter(List<JsonObject> specs)
    {
        DataCompositionFilter filter = SETTINGS_FACTORY.createDataCompositionFilter();
        for (JsonObject spec : specs)
        {
            filter.getItems().add(buildFilterItem(spec));
        }
        return filter;
    }

    private static FilterItem buildFilterItem(JsonObject spec)
    {
        if ("group".equals(string(spec, "kind", "condition"))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        {
            DataCompositionFilterItemGroup group = SETTINGS_FACTORY.createDataCompositionFilterItemGroup();
            group.setUse(bool(spec, "use", true)); //$NON-NLS-1$
            group.setGroupType(enumValue(DataCompositionFilterItemsGroupType.values(),
                string(spec, "groupType", "AND_GROUP"), "groupType")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            for (JsonObject nested : requiredObjectArray(spec, "items", "filter group")) //$NON-NLS-1$ //$NON-NLS-2$
            {
                group.getItems().add(buildFilterItem(nested));
            }
            return group;
        }
        DataCompositionFilterItem item = SETTINGS_FACTORY.createDataCompositionFilterItem();
        item.setUse(bool(spec, "use", true)); //$NON-NLS-1$
        item.setLeft(field(string(spec, "left", null))); //$NON-NLS-1$
        item.setComparisonType(enumValue(DataCompositionComparisonType.values(),
            string(spec, "comparison", "EQUAL"), "comparison")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (spec.has("right")) //$NON-NLS-1$
        {
            item.getRight().addAll(valueArray(spec.get("right"), "right")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return item;
    }

    private static DataCompositionOrder buildOrder(List<JsonObject> specs)
    {
        DataCompositionOrder order = SETTINGS_FACTORY.createDataCompositionOrder();
        for (JsonObject spec : specs)
        {
            order.getItems().add(buildOrderItem(spec));
        }
        return order;
    }

    private static DataCompositionOrderItem buildOrderItem(JsonObject spec)
    {
        DataCompositionOrderItem item = SETTINGS_FACTORY.createDataCompositionOrderItem();
        item.setUse(bool(spec, "use", true)); //$NON-NLS-1$
        item.setField(field(string(spec, "field", null))); //$NON-NLS-1$
        item.setOrderType(enumValue(DataCompositionSortDirection.values(),
            string(spec, "direction", "ASC"), "direction")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return item;
    }

    private static List<StructureItem> buildStructure(List<JsonObject> specs)
    {
        List<StructureItem> result = new ArrayList<>();
        for (JsonObject spec : specs)
        {
            result.add(buildStructureItem(spec));
        }
        return result;
    }

    private static StructureItem buildStructureItem(JsonObject spec)
    {
        if ("chart".equals(string(spec, "kind", "group"))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        {
            DataCompositionChart chart = SETTINGS_FACTORY.createDataCompositionChart();
            chart.setId(string(spec, "id", UUID.randomUUID().toString())); //$NON-NLS-1$
            chart.setName(string(spec, "name", "")); //$NON-NLS-1$ //$NON-NLS-2$
            chart.setUse(bool(spec, "use", true)); //$NON-NLS-1$
            for (JsonObject point : listOrEmpty(optionalObjectArray(spec, "points"))) //$NON-NLS-1$
            {
                chart.getPoints().add(buildChartGroup(point));
            }
            for (JsonObject series : listOrEmpty(optionalObjectArray(spec, "series"))) //$NON-NLS-1$
            {
                chart.getSeries().add(buildChartGroup(series));
            }
            applyCommon(chart, spec);
            return chart;
        }
        DataCompositionGroup group = SETTINGS_FACTORY.createDataCompositionGroup();
        group.setId(string(spec, "id", UUID.randomUUID().toString())); //$NON-NLS-1$
        group.setName(string(spec, "name", "")); //$NON-NLS-1$ //$NON-NLS-2$
        group.setUse(bool(spec, "use", true)); //$NON-NLS-1$
        group.setGroupFields(buildGroupFields(listOrEmpty(optionalObjectArray(spec, "groupFields")))); //$NON-NLS-1$
        for (JsonObject nested : listOrEmpty(optionalObjectArray(spec, "items"))) //$NON-NLS-1$
        {
            group.getItems().add(buildStructureItem(nested));
        }
        applyCommon(group, spec);
        return group;
    }

    private static DataCompositionChartGroup buildChartGroup(JsonObject spec)
    {
        DataCompositionChartGroup group = SETTINGS_FACTORY.createDataCompositionChartGroup();
        group.setId(string(spec, "id", UUID.randomUUID().toString())); //$NON-NLS-1$
        group.setName(string(spec, "name", "")); //$NON-NLS-1$ //$NON-NLS-2$
        group.setUse(bool(spec, "use", true)); //$NON-NLS-1$
        group.setGroupFields(buildGroupFields(listOrEmpty(optionalObjectArray(spec, "groupFields")))); //$NON-NLS-1$
        for (JsonObject nested : listOrEmpty(optionalObjectArray(spec, "items"))) //$NON-NLS-1$
        {
            group.getItems().add(buildChartGroup(nested));
        }
        List<JsonObject> selection = optionalObjectArray(spec, "selection"); //$NON-NLS-1$
        List<JsonObject> filter = optionalObjectArray(spec, "filter"); //$NON-NLS-1$
        List<JsonObject> order = optionalObjectArray(spec, "order"); //$NON-NLS-1$
        if (selection != null) group.setSelection(buildSelection(selection));
        if (filter != null) group.setFilter(buildFilter(filter));
        if (order != null) group.setOrder(buildOrder(order));
        return group;
    }

    private static DataCompositionGroupFields buildGroupFields(List<JsonObject> specs)
    {
        DataCompositionGroupFields fields = SETTINGS_FACTORY.createDataCompositionGroupFields();
        for (JsonObject spec : specs)
        {
            DataCompositionGroupField item = SETTINGS_FACTORY.createDataCompositionGroupField();
            item.setUse(bool(spec, "use", true)); //$NON-NLS-1$
            item.setField(field(string(spec, "field", null))); //$NON-NLS-1$
            item.setGroupType(enumValue(DataCompositionGroupType.values(),
                string(spec, "groupType", "ITEMS"), "groupType")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            fields.getItems().add(item);
        }
        return fields;
    }

    private static void applyCommon(StructureItem structure, JsonObject spec)
    {
        List<JsonObject> selection = optionalObjectArray(spec, "selection"); //$NON-NLS-1$
        List<JsonObject> filter = optionalObjectArray(spec, "filter"); //$NON-NLS-1$
        List<JsonObject> order = optionalObjectArray(spec, "order"); //$NON-NLS-1$
        List<JsonObject> appearance = optionalObjectArray(spec, "conditionalAppearance"); //$NON-NLS-1$
        JsonObject exposure = optionalObject(spec, "userSetting"); //$NON-NLS-1$
        JsonObject output = optionalObject(spec, "output"); //$NON-NLS-1$
        if (structure instanceof DataCompositionGroup)
        {
            DataCompositionGroup group = (DataCompositionGroup)structure;
            if (selection != null) group.setSelection(buildSelection(selection));
            if (filter != null) group.setFilter(buildFilter(filter));
            if (order != null) group.setOrder(buildOrder(order));
            if (appearance != null) group.setConditionalAppearance(buildAppearance(appearance));
            if (exposure != null) applyItemExposure(group, exposure);
            if (output != null)
            {
                group.setOutputParameters(SETTINGS_FACTORY.createDataCompositionGroupOutputParameterValues());
                applyOutput(group.getOutputParameters(), output, NativeParameterKind.GROUP_OUTPUT);
            }
        }
        else if (structure instanceof DataCompositionChart)
        {
            DataCompositionChart chart = (DataCompositionChart)structure;
            if (selection != null) chart.setSelection(buildSelection(selection));
            if (appearance != null) chart.setConditionalAppearance(buildAppearance(appearance));
            if (exposure != null) applyItemExposure(chart, exposure);
            if (output != null)
            {
                chart.setOutputParameters(SETTINGS_FACTORY.createDataCompositionChartOutputParameterValues());
                applyOutput(chart.getOutputParameters(), output, NativeParameterKind.CHART_OUTPUT);
            }
        }
    }

    private static DataCompositionConditionalAppearance buildAppearance(List<JsonObject> specs)
    {
        DataCompositionConditionalAppearance result = SETTINGS_FACTORY.createDataCompositionConditionalAppearance();
        for (JsonObject spec : specs)
        {
            DataCompositionConditionalAppearanceItem item =
                SETTINGS_FACTORY.createDataCompositionConditionalAppearanceItem();
            item.setUse(bool(spec, "use", true)); //$NON-NLS-1$
            DataCompositionAppearanceFields fields = SETTINGS_FACTORY.createDataCompositionAppearanceFields();
            for (String path : optionalStringArray(spec, "fields")) //$NON-NLS-1$
            {
                DataCompositionAppearanceField appearanceField = SETTINGS_FACTORY.createDataCompositionAppearanceField();
                appearanceField.setUse(true);
                appearanceField.setField(field(path));
                fields.getItems().add(appearanceField);
            }
            item.setSelection(fields);
            List<JsonObject> filter = optionalObjectArray(spec, "filter"); //$NON-NLS-1$
            if (filter != null) item.setFilter(buildFilter(filter));
            String presentation = string(spec, "presentation", null); //$NON-NLS-1$
            if (presentation != null) item.setPresentation(title(presentation));
            JsonObject appearance = optionalObject(spec, "appearance"); //$NON-NLS-1$
            if (appearance != null && !appearance.isEmpty())
            {
                throw bad("conditionalAppearance.appearance parameter values are not supported yet; " //$NON-NLS-1$
                    + "omit appearance until a public native value resolver is available."); //$NON-NLS-1$
            }
            JsonObject exposure = optionalObject(spec, "userSetting"); //$NON-NLS-1$
            if (exposure != null) applyAppearanceExposure(item, exposure);
            result.getItems().add(item);
        }
        return result;
    }

    private static void applyUserSettings(DataCompositionSettings settings, JsonObject spec)
    {
        applyExposureToSettings(settings, spec);
        if (settings.getSelection() != null) applyContainerExposure(settings.getSelection(), optionalObject(spec, "selection")); //$NON-NLS-1$
        if (settings.getFilter() != null) applyContainerExposure(settings.getFilter(), optionalObject(spec, "filter")); //$NON-NLS-1$
        if (settings.getOrder() != null) applyContainerExposure(settings.getOrder(), optionalObject(spec, "order")); //$NON-NLS-1$
    }

    private static void applyOutput(ParameterValues values, JsonObject spec, NativeParameterKind kind)
    {
        values.getItems().clear();
        for (String key : spec.keySet())
        {
            DataCompositionParameter parameter = CORE_FACTORY.createDataCompositionParameter();
            parameter.setValue(key);
            DataCompositionParameterValue item = CORE_FACTORY.createDataCompositionParameterValue();
            item.setUse(true);
            item.setParameter(parameter);
            item.getValues().add(outputValue(kind, key, spec.get(key)));
            values.getItems().add(item);
        }
    }

    private static Value outputValue(NativeParameterKind kind, String key, JsonElement element)
    {
        Enumerator literal = outputEnum(kind, key, element.getAsString());
        EnumValue result = McoreFactory.eINSTANCE.createEnumValue();
        result.setValue(literal);
        return result;
    }

    private static Enumerator outputEnum(NativeParameterKind kind, String key, String token)
    {
        if ("ResourcePlacement".equals(key) //$NON-NLS-1$
            && kind != NativeParameterKind.CHART_OUTPUT)
        {
            return enumValue(DataCompositionResourcesPlacement.values(), token, "output." + key); //$NON-NLS-1$
        }
        if ("ChartType.ResourcesPlacement".equals(key)) //$NON-NLS-1$
        {
            return enumValue(DataCompositionResourcesPlacementInChart.values(), token, "output." + key); //$NON-NLS-1$
        }
        throw unsupportedOutput(kind, key);
    }

    private static boolean supportsOutput(NativeParameterKind kind, String key)
    {
        return "ResourcePlacement".equals(key) && kind != NativeParameterKind.CHART_OUTPUT //$NON-NLS-1$
            || "ChartType.ResourcesPlacement".equals(key); //$NON-NLS-1$
    }

    private static IllegalArgumentException unsupportedOutput(NativeParameterKind kind, String key)
    {
        return bad("Unsupported output parameter '" + key + "' in " + kind //$NON-NLS-1$ //$NON-NLS-2$
            + "; supported typed parameters are ResourcePlacement and ChartType.ResourcesPlacement."); //$NON-NLS-1$
    }

    // ---- surgical mutation ------------------------------------------------------------------

    private static String preflightMutation(DataCompositionSchema schema, DataCompositionSettings settings,
        Mutation mutation)
    {
        if (mutation == null)
        {
            return null;
        }
        int matches = 0;
        switch (mutation.section)
        {
            case "selection": //$NON-NLS-1$
                if (settings != null && settings.getSelection() != null)
                {
                    List<SelectedItem> selected = new ArrayList<>();
                    collectSelected(settings.getSelection().getItems(), mutation.selector, selected);
                    matches = selected.size();
                }
                break;
            case "filter": //$NON-NLS-1$
                if (settings != null && settings.getFilter() != null)
                {
                    List<FilterItem> filters = new ArrayList<>();
                    collectFilters(settings.getFilter().getItems(), mutation.selector, filters);
                    matches = filters.size();
                }
                break;
            case "order": //$NON-NLS-1$
                if (settings != null && settings.getOrder() != null)
                {
                    for (OrderItem item : settings.getOrder().getItems())
                        if (item instanceof DataCompositionOrderItem && mutation.selector.equals(
                            fieldValue(((DataCompositionOrderItem)item).getField()))) matches++;
                }
                break;
            case "structure": //$NON-NLS-1$
                if (settings != null)
                {
                    List<StructureItem> structures = new ArrayList<>();
                    collectStructure(settings.getItems(), mutation.selector, structures);
                    matches = structures.size();
                    if (matches <= 1 && "upsert".equals(mutation.action)) //$NON-NLS-1$
                    {
                        String conflict = validateStructureUpsert(settings.getItems(), mutation,
                            matches == 1 ? structures.get(0) : null);
                        if (conflict != null) return conflict;
                    }
                }
                else if ("upsert".equals(mutation.action)) //$NON-NLS-1$
                {
                    String conflict = validateStructureUpsert(List.of(), mutation, null);
                    if (conflict != null) return conflict;
                }
                break;
            case "conditionalAppearance": //$NON-NLS-1$
                if (settings != null && settings.getConditionalAppearance() != null)
                    for (DataCompositionConditionalAppearanceItem item : settings.getConditionalAppearance().getItems())
                        if (mutation.selector.equals(item.getUserSettingID())) matches++;
                break;
            case "totalFields": //$NON-NLS-1$
                for (DataCompositionSchemaTotalField item : schema.getTotalFields())
                    if (mutation.selector.equals(item.getDataPath())) matches++;
                break;
            default: break;
        }
        if (matches > 1)
        {
            return ambiguous(mutation);
        }
        return matches == 0 && "remove".equals(mutation.action) ? missing(mutation) : null; //$NON-NLS-1$
    }

    private static String validateStructureUpsert(List<StructureItem> existing, Mutation mutation,
        StructureItem replaced)
    {
        JsonObject valueSpec = mutation.value.deepCopy();
        valueSpec.addProperty("id", mutation.selector); //$NON-NLS-1$
        Set<String> candidateIds = new HashSet<>();
        try
        {
            validateStructure(List.of(valueSpec), "mutation.value", candidateIds); //$NON-NLS-1$
        }
        catch (IllegalArgumentException e)
        {
            return e.getMessage();
        }
        Set<String> existingIds = new HashSet<>();
        collectStructureIds(existing, replaced, existingIds);
        for (String id : candidateIds)
        {
            if (existingIds.contains(id)) return "Duplicate structure id: " + id; //$NON-NLS-1$
        }
        return null;
    }

    private static void collectStructureIds(List<StructureItem> items, StructureItem skipped,
        Set<String> ids)
    {
        for (StructureItem item : items)
        {
            if (item == skipped) continue;
            String id = structureId(item);
            if (!empty(id)) ids.add(id);
            if (item instanceof DataCompositionGroup)
                collectStructureIds(((DataCompositionGroup)item).getItems(), skipped, ids);
        }
    }

    private static String applyMutation(DataCompositionSchema schema, DataCompositionSettings settings,
        Mutation mutation)
    {
        switch (mutation.section)
        {
            case "selection": //$NON-NLS-1$
                if (settings.getSelection() == null) settings.setSelection(buildSelection(List.of()));
                return mutateSelection(settings.getSelection().getItems(), mutation);
            case "filter": //$NON-NLS-1$
                if (settings.getFilter() == null) settings.setFilter(buildFilter(List.of()));
                return mutateFilter(settings.getFilter().getItems(), mutation);
            case "order": //$NON-NLS-1$
                if (settings.getOrder() == null) settings.setOrder(buildOrder(List.of()));
                return mutateOrder(settings.getOrder().getItems(), mutation);
            case "structure": //$NON-NLS-1$
                return mutateStructure(settings.getItems(), mutation);
            case "conditionalAppearance": //$NON-NLS-1$
                if (settings.getConditionalAppearance() == null)
                    settings.setConditionalAppearance(buildAppearance(List.of()));
                return mutateAppearance(settings.getConditionalAppearance(), mutation);
            case "totalFields": //$NON-NLS-1$
                return mutateTotalFields(schema, mutation);
            default:
                return "Unsupported mutation section: " + mutation.section; //$NON-NLS-1$
        }
    }

    private static String mutateSelection(List<SelectedItem> items, Mutation mutation)
    {
        List<SelectedItem> matches = new ArrayList<>();
        collectSelected(items, mutation.selector, matches);
        if (matches.size() > 1) return ambiguous(mutation);
        if ("remove".equals(mutation.action)) //$NON-NLS-1$
        {
            if (matches.isEmpty()) return missing(mutation);
            removeSelected(items, matches.get(0));
            return null;
        }
        SelectedItem replacement = buildSelectedItem(withMutationIdentity(mutation, "field")); //$NON-NLS-1$
        if (matches.isEmpty()) items.add(replacement); else replaceSelected(items, matches.get(0), replacement);
        return null;
    }

    private static void collectSelected(List<SelectedItem> items, String selector, List<SelectedItem> matches)
    {
        for (SelectedItem item : items)
        {
            if (selector.equals(selectedField(item))) matches.add(item);
            if (item instanceof DataCompositionSelectedFieldGroup)
                collectSelected(((DataCompositionSelectedFieldGroup)item).getItems(), selector, matches);
        }
    }

    private static boolean removeSelected(List<SelectedItem> items, SelectedItem target)
    {
        if (items.remove(target)) return true;
        for (SelectedItem item : items)
            if (item instanceof DataCompositionSelectedFieldGroup
                && removeSelected(((DataCompositionSelectedFieldGroup)item).getItems(), target)) return true;
        return false;
    }

    private static boolean replaceSelected(List<SelectedItem> items, SelectedItem target, SelectedItem value)
    {
        int index = items.indexOf(target);
        if (index >= 0) { items.set(index, value); return true; }
        for (SelectedItem item : items)
            if (item instanceof DataCompositionSelectedFieldGroup
                && replaceSelected(((DataCompositionSelectedFieldGroup)item).getItems(), target, value)) return true;
        return false;
    }

    private static String mutateFilter(List<FilterItem> items, Mutation mutation)
    {
        List<FilterItem> matches = new ArrayList<>();
        collectFilters(items, mutation.selector, matches);
        if (matches.size() > 1) return ambiguous(mutation);
        if ("remove".equals(mutation.action)) //$NON-NLS-1$
        {
            if (matches.isEmpty()) return missing(mutation);
            removeFilter(items, matches.get(0));
            return null;
        }
        FilterItem replacement = buildFilterItem(withMutationIdentity(mutation, "left")); //$NON-NLS-1$
        if (matches.isEmpty()) items.add(replacement); else replaceFilter(items, matches.get(0), replacement);
        return null;
    }

    private static void collectFilters(List<FilterItem> items, String selector, List<FilterItem> matches)
    {
        for (FilterItem item : items)
        {
            if (item instanceof DataCompositionFilterItem
                && selector.equals(fieldValue(((DataCompositionFilterItem)item).getLeft()))) matches.add(item);
            if (item instanceof DataCompositionFilterItemGroup)
                collectFilters(((DataCompositionFilterItemGroup)item).getItems(), selector, matches);
        }
    }

    private static boolean removeFilter(List<FilterItem> items, FilterItem target)
    {
        if (items.remove(target)) return true;
        for (FilterItem item : items)
            if (item instanceof DataCompositionFilterItemGroup
                && removeFilter(((DataCompositionFilterItemGroup)item).getItems(), target)) return true;
        return false;
    }

    private static boolean replaceFilter(List<FilterItem> items, FilterItem target, FilterItem value)
    {
        int index = items.indexOf(target);
        if (index >= 0) { items.set(index, value); return true; }
        for (FilterItem item : items)
            if (item instanceof DataCompositionFilterItemGroup
                && replaceFilter(((DataCompositionFilterItemGroup)item).getItems(), target, value)) return true;
        return false;
    }

    private static String mutateOrder(List<OrderItem> items, Mutation mutation)
    {
        List<OrderItem> matches = new ArrayList<>();
        for (OrderItem item : items)
            if (item instanceof DataCompositionOrderItem
                && mutation.selector.equals(fieldValue(((DataCompositionOrderItem)item).getField()))) matches.add(item);
        if (matches.size() > 1) return ambiguous(mutation);
        if ("remove".equals(mutation.action)) //$NON-NLS-1$
        {
            if (matches.isEmpty()) return missing(mutation);
            items.remove(matches.get(0));
        }
        else
        {
            OrderItem value = buildOrderItem(withMutationIdentity(mutation, "field")); //$NON-NLS-1$
            if (matches.isEmpty()) items.add(value); else items.set(items.indexOf(matches.get(0)), value);
        }
        return null;
    }

    private static String mutateStructure(List<StructureItem> items, Mutation mutation)
    {
        List<StructureItem> matches = new ArrayList<>();
        collectStructure(items, mutation.selector, matches);
        if (matches.size() > 1) return ambiguous(mutation);
        if ("remove".equals(mutation.action)) //$NON-NLS-1$
        {
            if (matches.isEmpty()) return missing(mutation);
            removeStructure(items, matches.get(0));
        }
        else
        {
            JsonObject valueSpec = withMutationIdentity(mutation, "id"); //$NON-NLS-1$
            StructureItem value = buildStructureItem(valueSpec);
            if (matches.isEmpty()) items.add(value); else replaceStructure(items, matches.get(0), value);
        }
        return null;
    }

    private static void collectStructure(List<StructureItem> items, String selector, List<StructureItem> matches)
    {
        for (StructureItem item : items)
        {
            if (selector.equals(structureId(item))) matches.add(item);
            if (item instanceof DataCompositionGroup)
                collectStructure(((DataCompositionGroup)item).getItems(), selector, matches);
        }
    }

    private static boolean removeStructure(List<StructureItem> items, StructureItem target)
    {
        if (items.remove(target)) return true;
        for (StructureItem item : items)
            if (item instanceof DataCompositionGroup
                && removeStructure(((DataCompositionGroup)item).getItems(), target)) return true;
        return false;
    }

    private static boolean replaceStructure(List<StructureItem> items, StructureItem target, StructureItem value)
    {
        int index = items.indexOf(target);
        if (index >= 0) { items.set(index, value); return true; }
        for (StructureItem item : items)
            if (item instanceof DataCompositionGroup
                && replaceStructure(((DataCompositionGroup)item).getItems(), target, value)) return true;
        return false;
    }

    private static String mutateAppearance(DataCompositionConditionalAppearance container, Mutation mutation)
    {
        List<DataCompositionConditionalAppearanceItem> matches = new ArrayList<>();
        for (DataCompositionConditionalAppearanceItem item : container.getItems())
            if (mutation.selector.equals(item.getUserSettingID())) matches.add(item);
        if (matches.size() > 1) return ambiguous(mutation);
        if ("remove".equals(mutation.action)) //$NON-NLS-1$
        {
            if (matches.isEmpty()) return missing(mutation);
            container.getItems().remove(matches.get(0));
        }
        else
        {
            JsonObject valueSpec = mutation.value.deepCopy();
            JsonObject exposure = optionalObject(valueSpec, "userSetting"); //$NON-NLS-1$
            if (exposure == null)
            {
                exposure = new JsonObject();
                valueSpec.add("userSetting", exposure); //$NON-NLS-1$
            }
            exposure.addProperty("id", mutation.selector); //$NON-NLS-1$
            DataCompositionConditionalAppearanceItem value = buildAppearance(List.of(valueSpec))
                .getItems().get(0);
            if (matches.isEmpty()) container.getItems().add(value);
            else container.getItems().set(container.getItems().indexOf(matches.get(0)), value);
        }
        return null;
    }

    private static String mutateTotalFields(DataCompositionSchema schema, Mutation mutation)
    {
        List<DataCompositionSchemaTotalField> matches = new ArrayList<>();
        for (DataCompositionSchemaTotalField item : schema.getTotalFields())
            if (mutation.selector.equals(item.getDataPath())) matches.add(item);
        if (matches.size() > 1) return ambiguous(mutation);
        if ("remove".equals(mutation.action)) //$NON-NLS-1$
        {
            if (matches.isEmpty()) return missing(mutation);
            schema.getTotalFields().remove(matches.get(0));
        }
        else
        {
            DataCompositionSchemaTotalField value =
                com._1c.g5.v8.dt.dcs.model.schema.DcsFactory.eINSTANCE.createDataCompositionSchemaTotalField();
            JsonObject valueSpec = withMutationIdentity(mutation, "dataPath"); //$NON-NLS-1$
            value.setDataPath(string(valueSpec, "dataPath", null)); //$NON-NLS-1$
            value.setExpression(string(valueSpec, "expression", null)); //$NON-NLS-1$
            List<String> groups = optionalStringArray(valueSpec, "groups"); //$NON-NLS-1$
            if (groups != null) value.getGroups().addAll(groups);
            if (matches.isEmpty()) schema.getTotalFields().add(value);
            else schema.getTotalFields().set(schema.getTotalFields().indexOf(matches.get(0)), value);
        }
        return null;
    }

    private static JsonObject withMutationIdentity(Mutation mutation, String property)
    {
        JsonObject value = mutation.value.deepCopy();
        value.addProperty(property, mutation.selector);
        return value;
    }

    // ---- normalized read-back ---------------------------------------------------------------

    private static JsonArray readTotalFields(DataCompositionSchema schema)
    {
        JsonArray result = new JsonArray();
        for (DataCompositionSchemaTotalField field : schema.getTotalFields())
        {
            JsonObject item = new JsonObject();
            item.addProperty("dataPath", field.getDataPath()); //$NON-NLS-1$
            item.addProperty("expression", field.getExpression()); //$NON-NLS-1$
            JsonArray groups = new JsonArray();
            for (String group : field.getGroups()) groups.add(group);
            item.add("groups", groups); //$NON-NLS-1$
            result.add(item);
        }
        return result;
    }

    private static JsonArray readSelection(DataCompositionSelectedFields selection)
    {
        JsonArray result = new JsonArray();
        if (selection == null) return result;
        for (SelectedItem item : selection.getItems()) result.add(readSelectedItem(item));
        return result;
    }

    private static JsonObject readSelectedItem(SelectedItem selected)
    {
        JsonObject item = new JsonObject();
        if (selected instanceof DataCompositionSelectedFieldGroup)
        {
            DataCompositionSelectedFieldGroup group = (DataCompositionSelectedFieldGroup)selected;
            item.addProperty("kind", "group"); //$NON-NLS-1$ //$NON-NLS-2$
            item.addProperty("field", fieldValue(group.getField())); //$NON-NLS-1$
            item.addProperty("use", group.isUse()); //$NON-NLS-1$
            addTitle(item, group.getTitle());
            JsonArray nested = new JsonArray();
            for (SelectedItem child : group.getItems()) nested.add(readSelectedItem(child));
            item.add("items", nested); //$NON-NLS-1$
        }
        else if (selected instanceof DataCompositionSelectedField)
        {
            DataCompositionSelectedField field = (DataCompositionSelectedField)selected;
            item.addProperty("kind", "field"); //$NON-NLS-1$ //$NON-NLS-2$
            item.addProperty("field", fieldValue(field.getField())); //$NON-NLS-1$
            item.addProperty("use", field.isUse()); //$NON-NLS-1$
            addTitle(item, field.getTitle());
        }
        return item;
    }

    private static JsonArray readFilter(DataCompositionFilter filter)
    {
        JsonArray result = new JsonArray();
        if (filter == null) return result;
        for (FilterItem item : filter.getItems()) result.add(readFilterItem(item));
        return result;
    }

    private static JsonObject readFilterItem(FilterItem filter)
    {
        JsonObject item = new JsonObject();
        if (filter instanceof DataCompositionFilterItemGroup)
        {
            DataCompositionFilterItemGroup group = (DataCompositionFilterItemGroup)filter;
            item.addProperty("kind", "group"); //$NON-NLS-1$ //$NON-NLS-2$
            item.addProperty("groupType", literal(group.getGroupType())); //$NON-NLS-1$
            item.addProperty("use", group.isUse()); //$NON-NLS-1$
            JsonArray nested = new JsonArray();
            for (FilterItem child : group.getItems()) nested.add(readFilterItem(child));
            item.add("items", nested); //$NON-NLS-1$
        }
        else if (filter instanceof DataCompositionFilterItem)
        {
            DataCompositionFilterItem condition = (DataCompositionFilterItem)filter;
            item.addProperty("kind", "condition"); //$NON-NLS-1$ //$NON-NLS-2$
            item.addProperty("left", fieldValue(condition.getLeft())); //$NON-NLS-1$
            item.addProperty("comparison", literal(condition.getComparisonType())); //$NON-NLS-1$
            item.addProperty("use", condition.isUse()); //$NON-NLS-1$
            JsonArray right = new JsonArray();
            for (Value value : condition.getRight()) right.add(readValue(value));
            item.add("right", right); //$NON-NLS-1$
        }
        return item;
    }

    private static JsonArray readOrder(DataCompositionOrder order)
    {
        JsonArray result = new JsonArray();
        if (order == null) return result;
        for (OrderItem ordered : order.getItems())
        {
            if (ordered instanceof DataCompositionOrderItem)
            {
                DataCompositionOrderItem value = (DataCompositionOrderItem)ordered;
                JsonObject item = new JsonObject();
                item.addProperty("field", fieldValue(value.getField())); //$NON-NLS-1$
                item.addProperty("direction", literal(value.getOrderType())); //$NON-NLS-1$
                item.addProperty("use", value.isUse()); //$NON-NLS-1$
                result.add(item);
            }
        }
        return result;
    }

    private static JsonArray readStructure(List<StructureItem> items)
    {
        JsonArray result = new JsonArray();
        for (StructureItem item : items) result.add(readStructureItem(item));
        return result;
    }

    private static JsonObject readStructureItem(StructureItem structure)
    {
        JsonObject item = new JsonObject();
        if (structure instanceof DataCompositionGroup)
        {
            DataCompositionGroup group = (DataCompositionGroup)structure;
            item.addProperty("kind", "group"); //$NON-NLS-1$ //$NON-NLS-2$
            item.addProperty("id", group.getId()); //$NON-NLS-1$
            item.addProperty("name", group.getName()); //$NON-NLS-1$
            item.addProperty("use", group.isUse()); //$NON-NLS-1$
            item.add("groupFields", readGroupFields(group.getGroupFields())); //$NON-NLS-1$
            item.add("selection", readSelection(group.getSelection())); //$NON-NLS-1$
            item.add("filter", readFilter(group.getFilter())); //$NON-NLS-1$
            item.add("order", readOrder(group.getOrder())); //$NON-NLS-1$
            item.add("items", readStructure(group.getItems())); //$NON-NLS-1$
            item.add("conditionalAppearance", readAppearance(group.getConditionalAppearance())); //$NON-NLS-1$
            item.add("userSetting", readItemExposure(group.getViewMode(), group.getUserSettingID(), //$NON-NLS-1$
                group.getUserSettingPresentation()));
            item.add("output", readOutput(group.getOutputParameters())); //$NON-NLS-1$
        }
        else if (structure instanceof DataCompositionChart)
        {
            DataCompositionChart chart = (DataCompositionChart)structure;
            item.addProperty("kind", "chart"); //$NON-NLS-1$ //$NON-NLS-2$
            item.addProperty("id", chart.getId()); //$NON-NLS-1$
            item.addProperty("name", chart.getName()); //$NON-NLS-1$
            item.addProperty("use", chart.isUse()); //$NON-NLS-1$
            item.add("points", readChartGroups(chart.getPoints())); //$NON-NLS-1$
            item.add("series", readChartGroups(chart.getSeries())); //$NON-NLS-1$
            item.add("selection", readSelection(chart.getSelection())); //$NON-NLS-1$
            item.add("conditionalAppearance", readAppearance(chart.getConditionalAppearance())); //$NON-NLS-1$
            item.add("userSetting", readItemExposure(chart.getViewMode(), chart.getUserSettingID(), //$NON-NLS-1$
                chart.getUserSettingPresentation()));
            item.add("output", readOutput(chart.getOutputParameters())); //$NON-NLS-1$
        }
        return item;
    }

    private static JsonArray readChartGroups(List<DataCompositionChartGroup> groups)
    {
        JsonArray result = new JsonArray();
        for (DataCompositionChartGroup group : groups)
        {
            JsonObject item = new JsonObject();
            item.addProperty("id", group.getId()); //$NON-NLS-1$
            item.addProperty("name", group.getName()); //$NON-NLS-1$
            item.addProperty("use", group.isUse()); //$NON-NLS-1$
            item.add("groupFields", readGroupFields(group.getGroupFields())); //$NON-NLS-1$
            item.add("selection", readSelection(group.getSelection())); //$NON-NLS-1$
            item.add("filter", readFilter(group.getFilter())); //$NON-NLS-1$
            item.add("order", readOrder(group.getOrder())); //$NON-NLS-1$
            item.add("items", readChartGroups(group.getItems())); //$NON-NLS-1$
            result.add(item);
        }
        return result;
    }

    private static JsonArray readGroupFields(DataCompositionGroupFields fields)
    {
        JsonArray result = new JsonArray();
        if (fields == null) return result;
        for (GroupItem groupItem : fields.getItems())
        {
            if (groupItem instanceof DataCompositionGroupField)
            {
                DataCompositionGroupField field = (DataCompositionGroupField)groupItem;
                JsonObject item = new JsonObject();
                item.addProperty("field", fieldValue(field.getField())); //$NON-NLS-1$
                item.addProperty("groupType", literal(field.getGroupType())); //$NON-NLS-1$
                item.addProperty("use", field.isUse()); //$NON-NLS-1$
                result.add(item);
            }
        }
        return result;
    }

    private static JsonArray readAppearance(DataCompositionConditionalAppearance appearance)
    {
        JsonArray result = new JsonArray();
        if (appearance == null) return result;
        for (DataCompositionConditionalAppearanceItem value : appearance.getItems())
        {
            JsonObject item = new JsonObject();
            item.addProperty("use", value.isUse()); //$NON-NLS-1$
            JsonArray fields = new JsonArray();
            if (value.getSelection() != null)
                for (DataCompositionAppearanceField field : value.getSelection().getItems())
                    fields.add(fieldValue(field.getField()));
            item.add("fields", fields); //$NON-NLS-1$
            item.add("filter", readFilter(value.getFilter())); //$NON-NLS-1$
            if (value.getPresentation() != null) item.addProperty("presentation", presentation(value.getPresentation())); //$NON-NLS-1$
            item.add("appearance", readOutput(value.getAppearance())); //$NON-NLS-1$
            item.add("userSetting", readItemExposure(value.getViewMode(), value.getUserSettingID(), //$NON-NLS-1$
                value.getUserSettingPresentation()));
            result.add(item);
        }
        return result;
    }

    private static JsonObject readSettingsExposure(DataCompositionSettings settings)
    {
        JsonObject result = readItemExposure(settings.getItemsViewMode(), settings.getItemsUserSettingID(),
            settings.getItemsUserSettingPresentation());
        if (settings.getSelection() != null) result.add("selection", readContainerExposure(settings.getSelection())); //$NON-NLS-1$
        if (settings.getFilter() != null) result.add("filter", readContainerExposure(settings.getFilter())); //$NON-NLS-1$
        if (settings.getOrder() != null) result.add("order", readContainerExposure(settings.getOrder())); //$NON-NLS-1$
        return result;
    }

    private static JsonObject readContainerExposure(Object container)
    {
        if (container instanceof DataCompositionSelectedFields)
        {
            DataCompositionSelectedFields c = (DataCompositionSelectedFields)container;
            return readItemExposure(c.getViewMode(), c.getUserSettingID(), c.getUserSettingPresentation());
        }
        if (container instanceof DataCompositionFilter)
        {
            DataCompositionFilter c = (DataCompositionFilter)container;
            return readItemExposure(c.getViewMode(), c.getUserSettingID(), c.getUserSettingPresentation());
        }
        DataCompositionOrder c = (DataCompositionOrder)container;
        return readItemExposure(c.getViewMode(), c.getUserSettingID(), c.getUserSettingPresentation());
    }

    private static JsonObject readItemExposure(DataCompositionSettingsItemViewMode mode, String id,
        Presentation presentation)
    {
        JsonObject result = new JsonObject();
        if (mode != null) result.addProperty("viewMode", literal(mode)); //$NON-NLS-1$
        if (!empty(id)) result.addProperty("id", id); //$NON-NLS-1$
        if (presentation != null) result.addProperty("presentation", presentation(presentation)); //$NON-NLS-1$
        return result;
    }

    private static JsonObject readOutput(ParameterValues values)
    {
        JsonObject result = new JsonObject();
        if (values == null) return result;
        for (DataCompositionParameterValue item : values.getItems())
        {
            if (item.getParameter() != null && !empty(item.getParameter().getValue()) && !item.getValues().isEmpty())
                result.add(item.getParameter().getValue(), readValue(item.getValues().get(0)));
        }
        return result;
    }

    // ---- exposure helpers -------------------------------------------------------------------

    private static void applyExposureToSettings(DataCompositionSettings settings, JsonObject spec)
    {
        DataCompositionSettingsItemViewMode mode = exposureMode(spec);
        if (mode != null) settings.setItemsViewMode(mode);
        String id = string(spec, "id", null); //$NON-NLS-1$
        if (id != null) settings.setItemsUserSettingID(id);
        String presentation = string(spec, "presentation", null); //$NON-NLS-1$
        if (presentation != null) settings.setItemsUserSettingPresentation(title(presentation));
    }

    private static void applyContainerExposure(Object container, JsonObject spec)
    {
        if (spec == null) return;
        DataCompositionSettingsItemViewMode mode = exposureMode(spec);
        String id = string(spec, "id", null); //$NON-NLS-1$
        String presentation = string(spec, "presentation", null); //$NON-NLS-1$
        if (container instanceof DataCompositionSelectedFields)
        {
            DataCompositionSelectedFields c = (DataCompositionSelectedFields)container;
            if (mode != null) c.setViewMode(mode); if (id != null) c.setUserSettingID(id);
            if (presentation != null) c.setUserSettingPresentation(title(presentation));
        }
        else if (container instanceof DataCompositionFilter)
        {
            DataCompositionFilter c = (DataCompositionFilter)container;
            if (mode != null) c.setViewMode(mode); if (id != null) c.setUserSettingID(id);
            if (presentation != null) c.setUserSettingPresentation(title(presentation));
        }
        else
        {
            DataCompositionOrder c = (DataCompositionOrder)container;
            if (mode != null) c.setViewMode(mode); if (id != null) c.setUserSettingID(id);
            if (presentation != null) c.setUserSettingPresentation(title(presentation));
        }
    }

    private static void applyItemExposure(Object item, JsonObject spec)
    {
        DataCompositionSettingsItemViewMode mode = exposureMode(spec);
        String id = string(spec, "id", null); //$NON-NLS-1$
        String presentation = string(spec, "presentation", null); //$NON-NLS-1$
        if (item instanceof DataCompositionGroup)
        {
            DataCompositionGroup c = (DataCompositionGroup)item;
            if (mode != null) c.setViewMode(mode); if (id != null) c.setUserSettingID(id);
            if (presentation != null) c.setUserSettingPresentation(title(presentation));
        }
        else
        {
            DataCompositionChart c = (DataCompositionChart)item;
            if (mode != null) c.setViewMode(mode); if (id != null) c.setUserSettingID(id);
            if (presentation != null) c.setUserSettingPresentation(title(presentation));
        }
    }

    private static void applyAppearanceExposure(DataCompositionConditionalAppearanceItem item, JsonObject spec)
    {
        DataCompositionSettingsItemViewMode mode = exposureMode(spec);
        if (mode != null) item.setViewMode(mode);
        String id = string(spec, "id", null); //$NON-NLS-1$
        if (id != null) item.setUserSettingID(id);
        String presentation = string(spec, "presentation", null); //$NON-NLS-1$
        if (presentation != null) item.setUserSettingPresentation(title(presentation));
    }

    private static DataCompositionSettingsItemViewMode exposureMode(JsonObject spec)
    {
        String mode = string(spec, "viewMode", null); //$NON-NLS-1$
        return mode == null ? null : enumValue(DataCompositionSettingsItemViewMode.values(), mode, "viewMode"); //$NON-NLS-1$
    }

    // ---- primitive/model helpers -------------------------------------------------------------

    private static DataCompositionField field(String path)
    {
        DataCompositionField field = CORE_FACTORY.createDataCompositionField();
        field.setValue(path);
        return field;
    }

    private static Presentation title(String text)
    {
        Presentation result = CORE_FACTORY.createPresentation();
        result.setValue(text);
        return result;
    }

    private static Value value(JsonElement element, String where)
    {
        if (element == null || element.isJsonNull())
        {
            StringValue result = com._1c.g5.v8.dt.mcore.McoreFactory.eINSTANCE.createStringValue();
            result.setValue(""); //$NON-NLS-1$
            return result;
        }
        if (!element.isJsonPrimitive()) throw bad(where + " must be a string, number, or boolean."); //$NON-NLS-1$
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean())
        {
            BooleanValue result = com._1c.g5.v8.dt.mcore.McoreFactory.eINSTANCE.createBooleanValue();
            result.setValue(primitive.getAsBoolean());
            return result;
        }
        if (primitive.isNumber())
        {
            NumberValue result = com._1c.g5.v8.dt.mcore.McoreFactory.eINSTANCE.createNumberValue();
            result.setValue(primitive.getAsBigDecimal());
            return result;
        }
        StringValue result = com._1c.g5.v8.dt.mcore.McoreFactory.eINSTANCE.createStringValue();
        result.setValue(primitive.getAsString());
        return result;
    }

    private static List<Value> valueArray(JsonElement element, String where)
    {
        List<Value> result = new ArrayList<>();
        if (element != null && element.isJsonArray())
        {
            int i = 0;
            for (JsonElement item : element.getAsJsonArray()) result.add(value(item, where + "[" + i++ + "]")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else
        {
            result.add(value(element, where));
        }
        return result;
    }

    private static JsonElement readValue(Value value)
    {
        if (value instanceof DataCompositionField) return new JsonPrimitive(((DataCompositionField)value).getValue());
        if (value instanceof StringValue) return new JsonPrimitive(((StringValue)value).getValue());
        if (value instanceof BooleanValue) return new JsonPrimitive(((BooleanValue)value).isValue());
        if (value instanceof EnumValue) return new JsonPrimitive(literal(((EnumValue)value).getValue()));
        if (value instanceof NumberValue)
        {
            BigDecimal number = ((NumberValue)value).getValue();
            return number == null ? new JsonPrimitive(0) : new JsonPrimitive(number);
        }
        JsonObject unsupported = new JsonObject();
        unsupported.addProperty("unsupportedValueType", //$NON-NLS-1$
            value == null ? "null" : value.eClass().getName()); //$NON-NLS-1$
        return unsupported;
    }

    private static String fieldValue(Value value)
    {
        return value instanceof DataCompositionField ? ((DataCompositionField)value).getValue() : ""; //$NON-NLS-1$
    }

    private static String selectedField(SelectedItem item)
    {
        if (item instanceof DataCompositionSelectedField) return fieldValue(((DataCompositionSelectedField)item).getField());
        if (item instanceof DataCompositionSelectedFieldGroup) return fieldValue(((DataCompositionSelectedFieldGroup)item).getField());
        return ""; //$NON-NLS-1$
    }

    private static String structureId(StructureItem item)
    {
        if (item instanceof DataCompositionGroup) return ((DataCompositionGroup)item).getId();
        if (item instanceof DataCompositionChart) return ((DataCompositionChart)item).getId();
        return ""; //$NON-NLS-1$
    }

    private static void addTitle(JsonObject object, Presentation title)
    {
        if (title != null) object.addProperty("title", presentation(title)); //$NON-NLS-1$
    }

    private static String presentation(Presentation value)
    {
        return value == null || value.getValue() == null ? "" : value.getValue(); //$NON-NLS-1$
    }

    private static String literal(Enumerator value)
    {
        return value == null ? "" //$NON-NLS-1$
            : value instanceof Enum<?> ? ((Enum<?>)value).name() : value.getLiteral();
    }

    private static <E extends Enum<E> & Enumerator> E enumValue(E[] values, String token, String where)
    {
        for (E value : values)
            if (value.name().equalsIgnoreCase(token) || value.getLiteral().equalsIgnoreCase(token)) return value;
        List<String> allowed = new ArrayList<>();
        for (E value : values) allowed.add(value.getLiteral());
        throw bad(where + " has invalid value '" + token + "'; expected one of " + allowed + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static JsonArray diff(JsonObject before, JsonObject after)
    {
        JsonArray result = new JsonArray();
        Set<String> keys = new java.util.LinkedHashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys)
        {
            JsonElement oldValue = before.get(key);
            JsonElement newValue = after.get(key);
            if (oldValue == null ? newValue != null : !oldValue.equals(newValue)) result.add("/" + key); //$NON-NLS-1$
        }
        return result;
    }

    private static String missing(Mutation mutation)
    {
        return "mutation selector '" + mutation.selector + "' did not match " + mutation.section + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static String ambiguous(Mutation mutation)
    {
        return "mutation selector '" + mutation.selector + "' matched more than one " + mutation.section //$NON-NLS-1$ //$NON-NLS-2$
            + " item; use a unique selector."; //$NON-NLS-1$
    }

    private static List<JsonObject> optionalObjectArray(JsonObject object, String key)
    {
        if (object == null || !object.has(key)) return null;
        return requiredObjectArray(object, key, key);
    }

    private static List<JsonObject> requiredObjectArray(JsonObject object, String key, String where)
    {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) throw bad(where + "." + key + " must be an array of objects."); //$NON-NLS-1$ //$NON-NLS-2$
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray())
        {
            if (!item.isJsonObject()) throw bad(where + "." + key + " must contain only objects."); //$NON-NLS-1$ //$NON-NLS-2$
            result.add(item.getAsJsonObject());
        }
        return result;
    }

    private static JsonObject optionalObject(JsonObject object, String key)
    {
        if (object == null || !object.has(key)) return null;
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonObject()) throw bad(key + " must be an object."); //$NON-NLS-1$
        return element.getAsJsonObject();
    }

    private static List<String> optionalStringArray(JsonObject object, String key)
    {
        if (object == null || !object.has(key)) return null;
        JsonElement element = object.get(key);
        if (!element.isJsonArray()) throw bad(key + " must be an array of strings."); //$NON-NLS-1$
        List<String> result = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray())
        {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString() || empty(item.getAsString()))
                throw bad(key + " must contain non-empty strings."); //$NON-NLS-1$
            result.add(item.getAsString());
        }
        return result;
    }

    private static String required(JsonObject object, String key, String where)
    {
        String result = string(object, key, null);
        if (empty(result)) throw bad(where + "." + key + " is required."); //$NON-NLS-1$ //$NON-NLS-2$
        return result;
    }

    private static String string(JsonObject object, String key, String defaultValue)
    {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return defaultValue;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw bad(key + " must be a string."); //$NON-NLS-1$
        String result = value.getAsString().trim();
        return result.isEmpty() ? defaultValue : result;
    }

    private static Boolean optionalBoolean(JsonObject object, String key)
    {
        if (!object.has(key)) return null;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw bad(key + " must be a boolean."); //$NON-NLS-1$
        return value.getAsBoolean();
    }

    private static boolean bool(JsonObject object, String key, boolean defaultValue)
    {
        Boolean result = optionalBoolean(object, key);
        return result == null ? defaultValue : result;
    }

    private static boolean empty(String value)
    {
        return value == null || value.isBlank();
    }

    private static IllegalArgumentException bad(String message)
    {
        return new IllegalArgumentException(message);
    }

    private static <T> List<T> listOrEmpty(List<T> list)
    {
        return list == null ? List.of() : list;
    }

    private static final class Plan
    {
        String target;
        String variantName;
        String variantPresentation;
        List<JsonObject> totalFields;
        List<JsonObject> selection;
        List<JsonObject> filter;
        List<JsonObject> order;
        List<JsonObject> structure;
        List<JsonObject> appearance;
        JsonObject userSettings;
        JsonObject output;
        Mutation mutation;
    }

    private static final class Mutation
    {
        String section;
        String action;
        String selector;
        JsonObject value;
    }

    private enum NativeParameterKind
    {
        OUTPUT,
        GROUP_OUTPUT,
        CHART_OUTPUT
    }

    private static final class Parse
    {
        final Plan plan;
        final String error;

        private Parse(Plan plan, String error)
        {
            this.plan = plan;
            this.error = error;
        }

        static Parse ok(Plan plan)
        {
            return new Parse(plan, null);
        }

        static Parse failed(String error)
        {
            return new Parse(null, error);
        }
    }
}
