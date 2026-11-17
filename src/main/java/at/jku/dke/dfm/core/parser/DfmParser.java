package at.jku.dke.dfm.core.parser;

import at.jku.dke.dfm.core.models.ast.AbstractElement;

import java.util.List;

/**
 * Parses the DFM notation used by DFM tasks.
 * <p>
 * This class is stateless and thread-safe; a single instance may be shared.
 */
public class DfmParser {
    /**
     * Parses a complete DFM model.
     *
     * @param source the DFM source
     * @return the parsed elements
     * @throws DfmParseException if the source is invalid
     */
    public List<AbstractElement> parse(String source) {
        return parse(source, true);
    }

    /**
     * Parses a part of a DFM model, e.g. an evaluation criterion. In contrast to {@link #parse(String)}, a
     * referenced hierarchy may have fewer than two incoming connections, because the other connections are not
     * part of the fragment.
     *
     * @param source the DFM source
     * @return the parsed elements
     * @throws DfmParseException if the source is invalid
     */
    public List<AbstractElement> parseFragment(String source) {
        return parse(source, false);
    }

    private List<AbstractElement> parse(String source, boolean complete) {
        if (source == null || source.isBlank()) {
            throw new DfmParseException("DFM input must not be empty.");
        }
        return new DfmParserRun(source, complete).parse();
    }
}
