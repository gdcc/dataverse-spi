# SPI Development Policy

Status: **Draft**
Version: 2026-01-14

This document defines how the Dataverse SPI is evolved to maximize long-term compatibility for community plugins.

## Scope

This policy applies to all artifacts published from this repository, in particular:

- SPI API modules (consumed by plugin authors)
- shared SPI modules (e.g., `spi-core`, if/when introduced)
- core-side helper modules (e.g., plugin metadata reader / loader), if published here

## Goals

1. Keep plugins working across many Dataverse *minor* releases with minimal or no maintenance.
2. Make incompatibilities detectable *before* classloading, with clear error messages.
3. Ensure the SPI remains a stable, well-defined boundary (core internals must not leak into it).

## Versioning rules (SemVer)

This project follows [Semantic Versioning](https://semver.org/).

- **Major**: breaking API / binary compatibility changes in public SPI contracts.
- **Minor**: additive, backwards-compatible changes (new types, new optional capabilities, new default methods).
- **Patch**: bug fixes and dependency upgrades that do not change public SPI API behavior/contracts in a breaking way.

### Practical mapping to Java compatibility

The JVM cares about **binary compatibility**. In practice:

- Adding a method to an interface is **breaking** unless it is a `default` method.
- Removing types/methods or changing signatures is **breaking**.
- Changing `public static final` constants can be breaking in subtle ways due to constant inlining.

When in doubt: assume a public signature change is breaking.

## API surface definition

“Public SPI surface” means any `public` type in packages intended for plugin consumption, including:

- public classes, interfaces, enums, annotations, records
- their public/protected methods, fields, constructors
- types referenced in public signatures (parameter types, return types, generic type arguments)

Only the public SPI surface is considered a compatibility contract.

## Design rules for compatibility

### 1) Prefer additive evolution
To add features without breaking plugins, prefer:

- adding new types (interfaces/classes/records)
- adding new optional interfaces (“capability interfaces”)
- adding `default` methods to existing interfaces (only when the default behavior is safe)

Avoid:
- changing existing method signatures
- removing methods/types
- requiring plugins to implement new methods in existing interfaces

### 2) Use multi-generation interfaces for breaking redesigns
When a breaking redesign is unavoidable for a specific extension point:

- introduce a new interface generation (e.g., `ExporterV2`)
- keep supporting the previous generation (`ExporterV1`) for a long transition period
- the Dataverse core may provide adapters/bridges to map V1 implementations into the current runtime model

This is the primary mechanism that enables “unmaintained community plugins keep working”.

### 3) Keep `spi-core` minimal and conservative
If/when a shared `spi-core` exists:

- keep it small and “boringly stable”
- changes should be almost exclusively additive
- treat breaking changes as exceptional and justify them explicitly

Because it is shared fate, breaking it creates maximum ecosystem churn.

### 4) Prevent core internals from leaking into SPI
Public SPI contracts must not reference Dataverse core internal types.

- Do not use `com.dataverse...` (or equivalent core packages) in SPI signatures.
- Prefer SPI-owned DTOs/value types for cross-boundary data.
- Avoid exposing third-party library types in SPI signatures unless explicitly approved (see “Allowed external types”).

### 5) Allowed external types (avoid dependency and classloading traps)
SPI contracts form a **binary/runtime boundary** between the Dataverse core and independently built plugins.
Every type that appears in a public SPI signature becomes part of that boundary.

Using external (non-JDK, non-SPI) types in the SPI is sometimes convenient, but it comes with concrete risks:

#### Why external types are risky

1. **Runtime classpath conflicts**
    - The Dataverse core provides one set of libraries at runtime (e.g., Jakarta EE APIs and implementations via the application server).
    - A plugin may accidentally bundle a different version of the same library (or compile against a different version).
    - This can lead to runtime linkage errors such as `NoSuchMethodError`, `NoClassDefFoundError`, or `ClassCastException`.

2. **Class identity across classloaders**
    - In a plugin system, different classloaders may load “the same” class name from different JARs.
    - Even if bytecode looks identical, the JVM treats these as **different types** if loaded by different classloaders.
    - Passing such objects across the SPI boundary can fail at runtime even when everything compiles.

3. **Hidden coupling to implementation and container behavior**
    - Some APIs look like plain types but are commonly used together with container lifecycles (e.g., CDI/JAX-RS request/response handling).
    - Exposing those types in SPI signatures can unintentionally couple plugins to the Dataverse runtime environment, making upgrades harder.

4. **Upgrade blast radius**
    - External types in SPI signatures force plugin authors to track upgrades even when they do not use new functionality.
    - This increases ecosystem churn and harms community plugins with limited maintenance capacity.

For these reasons, SPI contracts must primarily use:
- JDK types (`java.*`)
- SPI-owned types (`io.gdcc.spi.*`)

#### What kinds of external types are usually acceptable

External types are most acceptable when they are:
- **API-only**, not implementation types (no container-specific classes)
- **value-like / data-only** (no lifecycle or runtime-managed behavior)
- **stable across minor releases**
- **unlikely to be provided by plugins themselves** (or clearly documented as `provided`)

Examples that may be acceptable (with explicit allowlisting):
- `jakarta.ws.rs.core.MediaType` (a value-like representation of a media type)
- JSON-P DOM types such as `jakarta.json.JsonObject` and `jakarta.json.JsonArray` (data-only trees)

Even for these, plugin builds should depend on the API with `scope=provided`, and plugins should avoid bundling conflicting copies.

#### What kinds of external types are generally not acceptable

The following categories are high-risk and should not appear in public SPI signatures:

- **Transport/container types** (strongly couple plugins to runtime):
    - Most JAX-RS types beyond value-like primitives (e.g., `Response`, request/URI/context objects)
    - CDI/injection types (`jakarta.inject.*`, `jakarta.enterprise.*`)
- **Persistence/lifecycle-managed types**:
    - JPA (`jakarta.persistence.*`) entities, `EntityManager`, etc.
- **Framework-specific types** (tie SPI to a particular library ecosystem):
    - e.g., Jackson (`com.fasterxml.*`), Spring (`org.springframework.*`), etc.

If these are needed, prefer SPI-owned abstractions (DTOs, simple interfaces) and keep framework/container usage inside the Dataverse core.

#### Allowlist policy

If external APIs are allowed in public SPI signatures, they must be explicitly allowlisted and treated as part of the compatibility contract.

Current allowlist:
- `jakarta.ws.rs.core.MediaType`
- `jakarta.json.JsonArray`
- `jakarta.json.JsonObject`

Any addition to the allowlist must be reviewed with extra scrutiny and accompanied by enforcement (e.g., architectural tests) to prevent accidental expansion.
Within the SPI project, automated tests should make sure to not accidentally break the allowlist.

### 6) Deprecation policy
- Mark old API as `@Deprecated` before removal.
- Document migration paths.
- Do not remove deprecated APIs in minor/patch releases.
- Removal is a **major** change unless the type was explicitly experimental.

## Testing and enforcement

### 1) API compatibility checks
Use an API compatibility tool (e.g., Revapi or japicmp) in CI to detect:
- removed/changed methods
- signature changes
- binary incompatible modifications

CI should fail if the changes are not consistent with the intended version bump.

### 2) “No-leak” architectural checks
Use an architectural test to ensure the public SPI surface does not expose forbidden packages/types (e.g., disallow most `jakarta.ws.rs..` usage except `MediaType`).
Tools to use: Maven Enforcer, ArchUnit tests.

### 3) Compatibility fixture plugins
Maintain small “fixture plugins” compiled against:
- the oldest supported SPI generation
- the newest supported SPI generation

Run them against current core builds to verify loading and basic behavior.

## Contribution requirements (PR checklist)

- Public API change documented: what changed, why, and impact on plugins.
- SemVer impact assessed (patch/minor/major).
- Compatibility tool results checked and marked as passing.
- Migration path provided for breaking redesigns (prefer `V2` interfaces + adapters).
- If adding external types to SPI signatures: rationale + allowlist update + enforcement test update.