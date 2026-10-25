# DFM Core

Reusable Java library for parsing, modeling, and visualizing Dimensional Fact Models (DFM).

The library contains the DFM abstract syntax tree, parser, and SVG renderer. It has no Spring or database dependency and can be used by task applications, editors, and other DFM tools.

## Requirements

- Java 21
- Maven 3.9 or newer

## Build and test

```shell
mvn test
```

## Example

```java
var elements = new DfmParser().parse("""
    fact Sales { Amount; };
    dimension Time { Year - Month - Day; };
    Sales - Time;
    """);

var svg = new DfmVisualizationService().render(elements);
```

The public classes are in the `at.jku.dke.dfm.core` package hierarchy. The Maven coordinates are `at.jku.dke.etutor:task-app-dfm-core`.

## Publish

Publishing a GitHub release whose tag matches the version in `pom.xml` deploys the artifact to GitHub Packages. Consumers need package read access and a Maven server entry matching their repository id.
