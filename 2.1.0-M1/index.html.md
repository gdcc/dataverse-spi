---
date_published: 2026-09-11
date_modified: 2026-09-11
canonical_url: https://dataverse.org/index.html
---

# Dataverse SPI Plugin API

This site provides documentation for the SPI (Service Provider Interface) module for Dataverse.

## [Project Contents](#project-contents)

This project offers a universal Java module to create plugins for Dataverse:

- It provides *API contracts* a plugin author implements to create a new plugin.
- It provides an easy-to-use *annotation* `@DataversePlugin`, marking an implementation class as such a plugin.
- It generates *plugin metadata* automatically to make a plugin author's life as convenient as possible.
- It allows coordinated data exchange with the core using a *core provider* concept.
- It helps the core to detect any plugins an administrator adds to their Dataverse installation and validate its compatibility.

## [Audience](#audience)

This documentation is intended for developers who want to:

- build Dataverse plugins
- understand the available SPI contracts
- explore integration points
- browse the API reference

## [Maven Coordinates](#maven-coordinates)

The (current) artifact is published as `io.gdcc:dataverse-spi` to *Maven Central*. Use it in Maven like this:

```xml
<dependency>
    <groupId>io.gdcc</groupId>
    <artifactId>dataverse-spi</artifactId>
    <version>x.y.z</version>
</dependency>
```

Nnote: if you're using the GDCC Maven Parent, you may omit the version.

## [Documentation](#documentation)

- [Examples](examples.html)[1]
- [Modules](modules.html)[2]
- [Javadocs](apidocs/index.html)[3]

## [License](#license)

Licensed under the same terms as the Dataverse core project: [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt)[4].

## [Context & History](#context--history)

This module did not appear out of thin air. Before it was moved to this project with an independent release cycle and potential governance, it was part of the Dataverse core.

You can find the first ever commit that started it all here: [IQSS/dataverse@e560a34e](https://github.com/IQSS/dataverse/commit/e560a34e89b12a08b0e936e0cc8bd429f7a8c7c5)[5]. In an effort back in 2022, funded by DANS and undertaken by Jim Myers, this package originally formed as a separate Maven module. You can find the history and context in core pull request [IQSS/dataverse#9175](https://github.com/IQSS/dataverse/pull/9175)[6].

In 2026, it was decided within the [Dataverse Core Dev Team](https://dataverse.org/about)[7] to move the Maven module into a separate repository, enabling an independent release cycle, tags, the works. If you are interested in any commit history that happened before the initial Maven module creation, you can dig your way back from [IQSS/dataverse@fa0e2812](https://github.com/IQSS/dataverse/tree/fa0e28124a15b0db8042959b9fee536591f26f8d/modules/dataverse-spi)[8]
