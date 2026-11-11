package at.jku.dke.dfm.core.graph;

/**
 * The kinds of nodes in a {@link DfmGraph}.
 * <p>
 * {@link #SHARED} and {@link #CROSS} mark the entry level of a declared hierarchy that is referenced with
 * {@code {shared}} or {@code {cross}}; such a node has incoming edges from several places.
 */
public enum NodeKind {
    FACT,
    LEVEL,
    DESCRIPTIVE,
    SHARED,
    CROSS
}
