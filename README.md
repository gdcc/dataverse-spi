# dataverse-spi

This repository contains the SPI (Service Provider Interface) module for Dataverse.
It's the universal module to create plugins for Dataverse, as it allows coordinated data exchange with the core.
In addition, it helps the core to detect any plugins an administrator adds to their Dataverse installation.

To see it in action, check out the existing pluggable exporters:
[GDCC/dataverse-exporters](https://github.com/gdcc/dataverse-exporters)

## Maven Coordinates

The (current) artifact is published as `io.gdcc:dataverse-spi`.
Use it in Maven like this:

```xml
<dependency>
    <groupId>io.gdcc</groupId>
    <artifactId>dataverse-spi</artifactId>
    <version>x.y.z</version>
</dependency>
```

Please note: if you're using the [GDCC Maven Parent](https://github.com/gdcc/maven-parent), you can omit the version.

## License

Licensed under the same terms as the Dataverse core project: [Apache 2.0](./LICENSE).

## Context & History

This module did not appear out of thin air.
Before it was moved to this project with an independent release cycle and potential governance, it was part of the Dataverse core.

You can find the first ever commit that started it all here: [IQSS/dataverse@e560a34e](https://github.com/IQSS/dataverse/commit/e560a34e89b12a08b0e936e0cc8bd429f7a8c7c5).
In an effort back in 2022, funded by DANS and undertaken by Jim Myers, this package originally formed as a separate Maven module.
You can find the history and context in core pull request [IQSS/dataverse#9175](https://github.com/IQSS/dataverse/pull/9175).

In 2026, it was decided within the [Dataverse Core Dev Team](https://dataverse.org/about) to move the Maven module into a separate repository, enabling an independent release cycle, tags, the works.
If you are interested in any commit history that happened before the initial Maven module creation, you can dig your way back from [IQSS/dataverse@fa0e2812](https://github.com/IQSS/dataverse/tree/fa0e28124a15b0db8042959b9fee536591f26f8d/modules/dataverse-spi)

## Goals

This project is the main contract between the core development team and the plugin authors in the community.
The contract must be stable, well-defined, documented and at runtime, the core needs to negotiate compatibility with a plugin.

It must keep in mind to minimize the necessary maintenance work for both the core team and the plugin authors.
As any plugin provided by the community is likely to struggle to allocate developer resources, backward compatibility is paramount.

This project provides the following:

1. A community-based **Standard on Plugin Compatibility Metadata** the Dataverse core can read, even without loading classes.
   This is important to avoid loading a plugin which may break the core.
   It also allows future extension points like checking plugin signatures or licenses.
   Tooling to enable plugin authors to create the metadata more easily is a plus.  
   (TODO: link to document)
2. An **SPI Loader Module** as a core-side helper, making it easier in Dataverse to load plugins.
   Using a single place to define a custom classloader, reusable in the different core areas that are to support plugins reduces overhead.
   Also, including the code to deal with reading and checking plugin metadata in sync with the standard avoids diverging.  
   (TODO: link to code)
3. A **Development Policy** for SPI module and SPI API contract coding.
   It's imperative to define a solid foundation developers can follow, avoiding too many breaking changes later on.  
   (TODO: link to document)
4. An **SPI Core Module**.
   Acting as a place to keep common resources like DTOs, configuration, etc it is the "shared fate" for all other SPI modules.
   It must be kept as stable and unbreakable as possible, with a most conservative habit of changing it.
   Any SPI module and plugin will require updating when adding breaking changes to it.  
   (TODO: link to code)
5. A **Release Workflow** for SPI modules, based on a *Git tag per module (version)*.
   As modules must be releasable on their own in case of changes to them, we must not create a workflow that forces lock-step versioning.
   Instead, this project uses Git tags following this pattern: `spi-<module>-<version>`.
   Using Continuous Integration (CI) will allow releasing SPI modules independently and easily for SPI maintainers either on tag creation or manually.  
   (TODO: link to document / workflows)

On the other hand, what this project will not provide:

1. Platform information.
   Platform alignment is defined by Dataverse core releases, not by this repository.
   This project provides compile-time SPI contracts and plugin metadata/compatibility negotiation.
   A Dataverse release may additionally publish a platform BOM describing the runtime stack (e.g., application server / Jakarta EE implementation).
2. Maven parent POM
   This project does not provide a Maven parent for build conventions (plugins, formatting, etc.).
   Use the GDCC Maven Parent [io.gdcc:parent](https://github.com/gdcc/maven-parent) for standardized build hygiene and common tooling.

## Versioning Policy

This project follows [Semantic Versioning](https://semver.org/).

- **Major releases** are required for breaking changes.
- **Minor releases** add new functionality in a backwards-compatible way.
- **Patch releases** happen as needed and will mostly cover dependency upgrades or small (bug) fixes.

Over time, additional interfaces may be added to support a more pluggable Dataverse core.
If you have ideas for new extension points, please feel free to open an issue.
Pull requests are welcome, but given the foundational nature of the SPI, changes will be reviewed carefully with compatibility in mind.