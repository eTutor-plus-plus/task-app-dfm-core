package at.jku.dke.dfm.core.parser;

/**
 * Indicates invalid DFM source text.
 */
public class DfmParseException extends RuntimeException {
    private final int line;
    private final int column;

    public DfmParseException(String message) {
        super(message);
        this.line = -1;
        this.column = -1;
    }

    public DfmParseException(String message, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
