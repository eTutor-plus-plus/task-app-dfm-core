package at.jku.dke.dfm.core.models.enums;

/**
 * The kind of a hierarchy level.
 * <p>
 * {@link #SHARED} and {@link #CROSS} levels are references to a declared hierarchy
 * ({@link at.jku.dke.dfm.core.models.ast.HierarchyDefinitionElement}); their name is the name of that hierarchy.
 */
public enum LevelType {
    LEVEL,
    DESCRIPTIVE,
    SHARED,
    CROSS;

    /**
     * Whether a level of this type refers to a declared hierarchy.
     */
    public boolean isReference() {
        return this == SHARED || this == CROSS;
    }
}
