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

## DFM syntax

A model is a sequence of declarations, each terminated by `;`. Keywords (`fact`, `dimension`) are case-insensitive; names consist of letters, digits and `_`.

| Element | Syntax | Example |
|---|---|---|
| Fact | `fact Name { measure; {descriptive} descriptive; ... };` | `fact Sales { profit; {descriptive} clerk; };` |
| Dimension | `dimension Name { hierarchy; hierarchy; ... };` | `dimension Time { day - month; day - week; };` |
| Fact-dimension connection | `Fact - Dimension;` | `Sales - Time;` |

Within a hierarchy, levels are chained with connections:

| Notation | Meaning |
|---|---|
| `a - b` | simple connection |
| `a = b` | multiple connection |
| `a -> b` | convergence |
| `(a)` | optional level |
| `a (-) b` | optional connection (also `(=)`, `(->)`) |
| `{descriptive} d` | descriptive level |

Dimensions that are not connected to a fact are kept as separate top-level elements.

### Errors

Invalid input results in a `DfmParseException` carrying the 1-based `getLine()` and `getColumn()` of the offending token (`-1` if no position is available, e.g. for empty input). Unexpectedly truncated input is reported at the end of the source.

### Limitations

- A dimension can be connected to a single fact only; the renderer centers the first fact.

`DfmParser` and `DfmVisualizationService` hold no state between calls, so single instances can be shared across threads.

## Graph model and rendering

`DfmGraphBuilder` turns the parsed elements into a layout-independent `DfmGraph` (nodes and edges). Elements with the same name share one node, and a fact is connected to the head of the first hierarchy of each of its dimensions.

```java
var graph = new DfmGraphBuilder().build(elements);
```

`DfmVisualizationService` is a static renderer built on this graph with its own layout. It produces a self-contained SVG, e.g. for feedback in systems that cannot run JavaScript. Interactive front ends (such as the DFM editor) should use the `DfmGraph` and do their own layout and drawing.

## Publish

Publishing a GitHub release whose tag matches the version in `pom.xml` deploys the artifact to GitHub Packages. Consumers need package read access and a Maven server entry matching their repository id.
