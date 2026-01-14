# Plugin Metadata Specification

Status: **Draft**
Version: 2026-01-14

This document defines the metadata format that Dataverse plugins must provide so the Dataverse core can:

- identify plugins reliably
- negotiate compatibility without classloading plugin classes
- provide actionable error messages to administrators

## Design principles

- **Readable without classloading**: core must be able to evaluate compatibility by reading files from the plugin JAR.
- **Stable identifiers**: plugins have stable IDs across releases.
- **Version ranges**: plugins declare supported SPI/core ranges using semver-compatible ranges.
- **Extensible**: new optional fields can be added without breaking existing plugins.

## Metadata file location and format

Plugins must include a properties file at:

`META-INF/dataverse-plugin.properties`

Format: Java `.properties` (key/value, UTF-8 recommended).

## Required fields

| Key        | Description                                                                                  | Example                 |
|------------|----------------------------------------------------------------------------------------------|-------------------------|
| `id`       | Stable plugin identifier (lowercase, URL-safe recommended). Must not change across releases. | `gdcc-example-exporter` |
| `name`     | Human-readable name.                                                                         | `Example Exporter`      |
| `version`  | Plugin version (semantic versioning recommended).                                            | `1.3.0`                 |
| `spiRange` | Supported SPI version range.                                                                 | `>=2.1.0 <3.0.0`        |

TODO: Add more ranges for versions of Java, Jakarta EE, Dataverse, Payara, etc.? (Maybe optional?)

### Range syntax

The `spiRange` uses a restricted semver range syntax:

- Comparators: `>=`, `>`, `<=`, `<`, `=`
- Conditions separated by spaces are treated as logical AND

Examples:
- `>=2.1.0 <3.0.0`
- `=2.2.1`
- `>=2.5.0`

If the core cannot parse the range, the plugin must be rejected with a clear error.

## Strongly recommended fields

| Key           | Description                                        | Example                           |
|---------------|----------------------------------------------------|-----------------------------------|
| `vendor`      | Author/organization name.                          | `GDCC`                            |
| `website`     | Project URL.                                       | `https://example.invalid/plugin`  |
| `description` | Short text shown in logs/admin UI.                 | `Exports datasets in XYZ format.` |
| `type`        | Comma-separated plugin categories (informational). | `exporter`                        |

## Optional compatibility fields (future-facing)

These fields enable more nuanced negotiation as the SPI grows.

| Key         | Description                                                              | Example                     |
|-------------|--------------------------------------------------------------------------|-----------------------------|
| `requires`  | Comma-separated capability identifiers required by the plugin.           | `CONFIG_LOOKUP,EXPORTER_V2` |
| `provides`  | Comma-separated capabilities provided by the plugin.                     | `EXPORTER_V1`               |
| `javaRange` | Supported Java runtime range for the plugin.                             | `>=17`                      |
| `coreRange` | Supported Dataverse core range (optional; prefer SPI-based negotiation). | `>=7.1.0 <8.0.0`            |

## Loader behavior requirements

When scanning plugin JARs, the loader must:

1. Locate `META-INF/dataverse-plugin.properties`.
2. If missing: reject plugin (or skip with a clear message, depending on policy).
3. Parse required keys: `id`, `name`, `version`, `spiRange`.
4. Determine the core-provided SPI version.
5. Evaluate `spiRange`:
   - if satisfied: plugin is eligible for classloading/discovery
   - if not satisfied: reject plugin without classloading
6. Log actionable messages including:
   - plugin `id` and `version`
   - declared `spiRange`
   - core SPI version
   - plugin JAR path

### Failure message guidance (example)

Plugin `gdcc-example-exporter` v`1.3.0` is incompatible with this Dataverse installation.  
Requires SPI `>=2.1.0 <3.0.0`, but core provides SPI `3.0.1`.  
Plugin path: `/path/to/plugins/example-exporter.jar`.

## Relationship to service discovery

This metadata file does not replace Java service discovery (e.g., AutoService / ServiceLoader). It complements it:

- metadata is used for identification and compatibility negotiation
- service discovery is used to locate actual implementations (exporters, etc.) after a plugin passes compatibility checks
- metadata defined here can be reused after loading for display purposes (e. g. names to be displayed in the UI)

## Example metadata file
```properties
type=exporter
id=gdcc-example-exporter
name=Example Exporter
version=1.3.0
spiRange=>=2.1.0 <3.0.0
description=Exports datasets in Example Format.
vendor=GDCC
website=https://example.invalid/plugin
```

## Notes on stability and governance

- `id` must remain stable across releases.
- `version` should follow semantic versioning.
- New optional keys may be introduced at any time.
- Required keys may only change in a major SPI change, and should be avoided.
