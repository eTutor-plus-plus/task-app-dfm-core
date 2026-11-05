package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.LevelType;

import java.util.Objects;

/**
 * A level in a hierarchy.
 */
public class Level {
    private final String name;
    private boolean optional;
    private ConnectionType connectionType;
    private boolean connectionOptional;
    private String connectionLabel;
    private Level nextLevel;
    private LevelType levelType = LevelType.LEVEL;

    public Level(String name, ConnectionType connectionType) {
        this.name = name;
        this.connectionType = connectionType;
    }

    public String getName() {
        return name;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(ConnectionType connectionType) {
        this.connectionType = connectionType;
    }

    public boolean isConnectionOptional() {
        return connectionOptional;
    }

    public void setConnectionOptional(boolean connectionOptional) {
        this.connectionOptional = connectionOptional;
    }

    /**
     * The description of the connection to the next level, or {@code null} if the connection has none.
     */
    public String getConnectionLabel() {
        return connectionLabel;
    }

    public void setConnectionLabel(String connectionLabel) {
        this.connectionLabel = connectionLabel;
    }

    public Level getNextLevel() {
        return nextLevel;
    }

    public void setNextLevel(Level nextLevel) {
        this.nextLevel = nextLevel;
    }

    public LevelType getLevelType() {
        return levelType;
    }

    public void setLevelType(LevelType levelType) {
        this.levelType = levelType;
    }

    public boolean structurallyEquals(Level other) {
        if (other == null || !name.equals(other.name)
            || optional != other.optional
            || connectionType != other.connectionType
            || connectionOptional != other.connectionOptional
            || !Objects.equals(connectionLabel, other.connectionLabel)
            || levelType != other.levelType) {
            return false;
        }
        if (nextLevel == null) {
            return other.nextLevel == null;
        }
        return nextLevel.structurallyEquals(other.nextLevel);
    }
}
