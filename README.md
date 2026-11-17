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

A model is a sequence of declarations, each terminated by `;`. Keywords (`fact`, `dimension`, `hierarchy`) are case-insensitive; names consist of letters, digits and `_`.

| Element | Syntax | Example |
|---|---|---|
| Fact | `fact Name { measure; {descriptive} descriptive; ... };` | `fact Sales { profit; {descriptive} clerk; };` |
| Dimension | `dimension Name { hierarchy; hierarchy; ... };` | `dimension Time { day - month; day - week; };` |
| Hierarchy declaration | `hierarchy Name { chain; chain; ... };` | `hierarchy city { city - country; city - {descriptive} zipCode; };` |
| Fact-dimension connection | `Fact - Dimension;`, `Fact = Dimension;`, optionally in parentheses and with a description | `Sales (-[soldat]-) Time;` |

Within a hierarchy, levels are chained with connections:

| Notation | Meaning |
|---|---|
| `a - b` | simple connection |
| `a = b` | multiple connection |
| `a -> b` | convergence |
| `(a)` | optional level |
| `a (-) b` | optional connection (also `(=)`, `(->)`) |
| `{descriptive} d` | descriptive level |
| `{shared} h` | reference to the declared hierarchy `h` (shared hierarchy) |
| `{cross} h` | reference to the declared hierarchy `h` (cross-dimensional attribute) |
| `a -[text]- b` | connection with a description (also `=[text]=` and `-[text]->`; `a (-[text]-) b` if optional) |

The description is a single name (letters, digits, `_`). The opening and closing connector must belong together: `-…-`, `=…=` or `-…->` between levels, and `-…-` or `=…=` for fact-dimension connections. Parentheses make a connection optional, e.g. `Sales (-) Time;` or `Sales (=[role]=) Time;`. The description, type and optionality are part of the connection and are compared when evaluating a fact.

Several connections can refer to the same dimension of a fact, for example `Invoice -[shipping]- Customer; Invoice -[billing]- Customer;`. Distinct parallel edges are retained in the graph; exact duplicates produced by repeated hierarchy prefixes are merged.

Dimensions that are not connected to a fact are kept as separate top-level elements.

### Recursive hierarchy

A level may refer to itself with a role. The graph then contains a self-loop edge at that level, and the renderer draws it as a loop labeled with the role:

```
dimension SalesRepresentative {
  salesRepresentative -[supervisor]-> salesRepresentative;
  salesRepresentative (-[alternate]-) salesRepresentative;
};
```

A self-reference without a role (`a - a`) is ignored. Recursive connections may be optional and may have distinct parallel roles, but cannot be multiple (`=`). Multiple connections between different levels remain supported.

### Shared hierarchies and cross-dimensional attributes

A hierarchy that several dimensions use, or an attribute that depends on levels of different dimensions, is declared once with `hierarchy` and referenced with `{shared}` or `{cross}`:

```
fact Sales { profit; };

hierarchy VAT { VAT - vatClass; };
hierarchy city {
  city = region;
  city - country - continent;
  city - country - {cross} VAT;
  city - {descriptive} zipCode;
  city - {descriptive} population;
};

dimension city { {shared} city; };
dimension customer { customer -[customerCity]- {shared} city; };
dimension product { product - category - {cross} VAT; };

Sales -[storeCity]- city;
Sales - customer;
Sales - product;
```

- A `hierarchy` declaration contains one or more chains. Every chain starts with the first level of the first chain, the entry level of the hierarchy. A chain cannot start with a `{shared}` or `{cross}` level.
- `{shared} h` and `{cross} h` refer to the declaration `hierarchy h`, which is required; it may appear before or after the reference. The reference must be the last level of its chain and cannot be optional (use `(-)` for the connection to it). Connections to it can have a description, as in `customer -[customerCity]- {shared} city`.
- References are allowed in dimensions and in other hierarchy declarations. A hierarchy must not refer to itself, directly or through other hierarchies.
- A hierarchy is either shared or cross-dimensional: it cannot be referenced with both markers.
- A referenced hierarchy must be the target of at least two connections, because a shared hierarchy or cross-dimensional attribute joins several branches. The connections are counted like the edges of the graph: one per connection from a fact to a dimension that starts with the reference (several roles such as `Invoice -[shipping]- Customer; Invoice -[billing]- Customer;` count separately), and one per distinct connection from a level to the reference, in dimensions and in other hierarchy declarations. A hierarchy with a single connection is rejected. `DfmParser.parseFragment` skips this check for parts of a model, e.g. evaluation criteria.
- Hierarchy names are unique among the hierarchy declarations. They are independent of dimension and level names, so `hierarchy city` and `dimension city` can coexist.
- A declared hierarchy that is never referenced is still part of the model.

`DfmParser` returns the declarations as `HierarchyDefinitionElement` elements after the facts and unconnected dimensions. `HierarchyDefinitionElement.structurallyEquals` ignores the order of the chains. Levels of the types `LevelType.SHARED` and `LevelType.CROSS` carry the name of the referenced hierarchy.

### Comments

`//` starts a line comment that runs to the end of the line. Comments are allowed anywhere whitespace is, and are ignored by the parser; line and column numbers in errors still refer to the original source. Input that contains only comments is rejected like empty input.

```
// sales model
fact Sales { profit; };   // measure
```

### Errors

Invalid input results in a `DfmParseException` carrying the 1-based `getLine()` and `getColumn()` of the offending token (`-1` if no position is available, e.g. for empty input). Unexpectedly truncated input is reported at the end of the source.

### Limitations

- A dimension can be connected to a single fact only; the renderer centers the first fact.

`DfmParser` and `DfmVisualizationService` hold no state between calls, so single instances can be shared across threads.

## Graph model and rendering

`DfmGraphBuilder` turns the parsed elements into a layout-independent `DfmGraph` (nodes and edges). Same-named levels share a node within one dimension, but remain separate across dimensions. The levels of a declared hierarchy form one node each, independent of the referencing dimensions (id `hierarchy-<name>-level-<level>`). A `{shared}` or `{cross}` reference has no node of its own: its edge ends at the entry level of the declared hierarchy, whose node has the kind `NodeKind.SHARED` or `NodeKind.CROSS`. The renderer draws it as a double circle (shared) or as a circle with an arc (cross-dimensional attribute). A fact is connected to the head of the first hierarchy of each of its dimensions.

```java
var graph = new DfmGraphBuilder().build(elements);
```

`DfmVisualizationService` is a static renderer built on this graph with its own layout. It produces a self-contained SVG, e.g. for feedback in systems that cannot run JavaScript. Interactive front ends (such as the DFM editor) should use the `DfmGraph` and do their own layout and drawing.

## Publish

Publishing a GitHub release whose tag matches the version in `pom.xml` deploys the artifact to GitHub Packages. Consumers need package read access and a Maven server entry matching their repository id.
