Writes advanced settings of a Data Composition Schema through the native EDT DCS model. It never
edits `.dcs` XML. The write runs in the same BM transaction and dual owner/`.dcs` export path as the
existing `modify_metadata` DCS writer. No query text is changed by this tool; validate a query separately
with `validate_query` before changing it through `modify_metadata`.

## Contract

- `projectName` — open EDT project.
- `ownerFqn` — `Report.<Name>`, `ExternalReport.<Name>`, or
  `ExternalDataProcessor.<Name>` (type tokens may be Russian). A regular Report may lazily receive its
  main DCS template; external owners must already contain a `DATA_COMPOSITION_SCHEMA` template.
- `settings` — object described below.

`settings.target` is `default` (the default) or `variant`. A variant requires `variantName`; it is created
when absent and updated in place when present. `variantPresentation` is optional. Omitted sections are
preserved. A supplied full section replaces only that section. Unknown/unvisited containment and IDs in
all other sections remain intact.

Supported full sections:

- `totalFields`: `[{dataPath, expression, groups?: [groupName]}]`. Total fields are schema-wide and can
  only be changed with `target:'default'`.
- `selection`: selected fields and nested groups. Items are
  `{kind:'field', field, title?, use?}` or `{kind:'group', field, title?, use?, items:[...]}`.
- `filter`: conditions `{kind:'condition', left, comparison, right?, use?}` and nested AND/OR/NOT groups
  `{kind:'group', groupType:'AND_GROUP'|'OR_GROUP'|'NOT_GROUP', use?, items:[...]}`. `right` may be one
  primitive or an array of primitives.
- `order`: `[{field, direction:'ASC'|'DESC', use?}]`.
- `structure`: nested `group` and `chart` items. A group accepts `id`, `name`, `use`, `groupFields`, local
  `selection`/`filter`/`order`/`conditionalAppearance`, nested `items`, `userSetting`, and `output`. A chart
  accepts `points` and `series`; each is a recursively nestable chart group with `groupFields`.
- `conditionalAppearance`: items with `fields`, optional `filter`, `presentation`, `use`, and
  `userSetting`. A non-empty `appearance` map is currently rejected because EDT's public API does not
  expose a safe version-aware resolver for native typed appearance values.
- `userSettings`: exposure for the settings items and their `selection`, `filter`, and `order` containers.
  Exposure accepts `viewMode` (`NORMAL`, `QUICK_ACCESS`, `INACCESSIBLE`, `AUTO`), `id`, `presentation`.
- `output`: the explicitly supported native enum parameters `ResourcePlacement` (top-level and group)
  and `ChartType.ResourcesPlacement` (top-level, group, and chart), passed as enum literal strings such as
  `ResourcePlacement:'VERTICALLY'` or `ChartType.ResourcesPlacement:'SERIES'`. They are persisted as EDT
  `EnumValue`, not as strings. Other output keys are rejected before mutation.

## Surgical mutation

Use `mutation` instead of replacing a section:

```json
{
  "target": "default",
  "mutation": {
    "section": "order",
    "action": "upsert",
    "selector": "Amount",
    "value": {"field": "Amount", "direction": "DESC", "use": true}
  }
}
```

Sections and selectors: `selection` by field path (nested too), `filter` by left field path (nested too),
`order` by field path, `structure` by stable item `id` (nested groups too), `conditionalAppearance` by its
`userSetting.id`, and `totalFields` by `dataPath`. `remove` requires an existing unique match. `upsert`
replaces a unique match in place or appends a new item. An ambiguous or missing removal selector is refused
and the BM transaction rolls back.

## Example

```json
{
  "projectName": "MyProject",
  "ownerFqn": "Report.Sales",
  "settings": {
    "target": "default",
    "totalFields": [{"dataPath":"Amount","expression":"SUM(Amount)","groups":["Warehouse"]}],
    "selection": [{"field":"Warehouse"},{"field":"Amount"}],
    "filter": [{"left":"Posted","comparison":"EQUAL","right":true}],
    "order": [{"field":"Amount","direction":"DESC"}],
    "structure": [{
      "kind":"group", "id":"warehouse", "name":"Warehouse", "use":true,
      "groupFields":[{"field":"Warehouse","groupType":"ITEMS"}],
      "selection":[{"field":"Amount"}],
      "output":{"ResourcePlacement":"HORIZONTALLY"}
    }]
  }
}
```

Success includes normalized `settingsBefore`, `settingsAfter`, and `changedSettingsPaths`; paths identify
changed top-level sections rather than individual array members. Supported native enum values round-trip
as their literals. An existing platform value outside the supported boolean/number/string/enum corpus is
reported explicitly as `{"unsupportedValueType":"..."}` instead of being misrepresented as writable.
