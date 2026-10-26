package at.jku.dke.dfm.core.parser;

import at.jku.dke.dfm.core.models.ast.AbstractElement;

import java.util.List;

/**
 * Parses the DFM notation used by DFM tasks.
 * <p>
 * This class is stateless and thread-safe; a single instance may be shared.
 */
public class DfmParser {
    public List<AbstractElement> parse(String source) {
        if (source == null || source.isBlank()) {
            throw new DfmParseException("DFM input must not be empty.");
        }
        return new DfmParserRun(source).parse();
    }
}
