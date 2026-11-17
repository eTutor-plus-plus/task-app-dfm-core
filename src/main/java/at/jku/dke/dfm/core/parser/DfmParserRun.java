package at.jku.dke.dfm.core.parser;

import at.jku.dke.dfm.core.models.ast.AbstractElement;
import at.jku.dke.dfm.core.models.ast.DimensionElement;
import at.jku.dke.dfm.core.models.ast.FactDimensionElement;
import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.models.ast.Hierarchy;
import at.jku.dke.dfm.core.models.ast.HierarchyDefinitionElement;
import at.jku.dke.dfm.core.models.ast.Level;
import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.LevelType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A single parse run holding the mutable token state. Instances must not be shared or reused.
 */
final class DfmParserRun {
    private static final int MIN_CONNECTIONS = 2;

    private final List<Token> tokens;
    private final boolean complete;
    private final List<HierarchyReference> references = new ArrayList<>();
    private int position;

    DfmParserRun(String source, boolean complete) {
        this.tokens = tokenize(source);
        this.complete = complete;
    }

    List<AbstractElement> parse() {
        if (peek(TokenType.END)) {
            throw new DfmParseException("DFM input must not be empty.");
        }
        var facts = new ArrayList<FactElement>();
        var dimensions = new ArrayList<DimensionElement>();
        var definitions = new ArrayList<HierarchyDefinitionElement>();
        var connections = new ArrayList<FactDimensionElement>();

        while (!peek(TokenType.END)) {
            if (acceptWord("fact")) {
                facts.add(parseFact());
            } else if (acceptWord("dimension")) {
                dimensions.add(parseDimension());
            } else if (acceptWord("hierarchy")) {
                var nameToken = tokens.get(position);
                var definition = parseHierarchyDefinition();
                if (definitions.stream().anyMatch(candidate -> candidate.getName().equals(definition.getName()))) {
                    throw new DfmParseException("Hierarchy " + definition.getName() + " is declared more than once.",
                        nameToken.line, nameToken.column);
                }
                definitions.add(definition);
            } else {
                connections.add(parseConnection());
            }
            expect(TokenType.SEMICOLON, "Expected ';' after declaration.");
        }
        resolveHierarchyReferences(definitions);

        var owners = new HashMap<DimensionElement, FactElement>();
        for (var connection : connections) {
            var fact = facts.stream().filter(candidate -> candidate.getName().equals(connection.getRelationFromName()))
                .findFirst().orElseThrow(() -> new DfmParseException("Fact not found for connection "
                    + connection.getRelationFromName() + " - " + connection.getRelationToName()));
            var dimension = dimensions.stream().filter(candidate ->
                    candidate.getName().equals(connection.getRelationToName()) && !owners.containsKey(candidate))
                .findFirst().orElseGet(() -> dimensions.stream().filter(candidate ->
                    candidate.getName().equals(connection.getRelationToName()) && owners.get(candidate) == fact)
                .findFirst().orElseThrow(() -> new DfmParseException("Dimension not found for connection "
                    + connection.getRelationFromName() + " - " + connection.getRelationToName())));
            owners.put(dimension, fact);
            fact.getDimensions().add(dimension.withFactConnection(connection));
        }
        dimensions.removeIf(owners::containsKey);
        checkIncomingConnections(facts, dimensions, definitions);

        var result = new ArrayList<AbstractElement>(facts);
        result.addAll(dimensions);
        result.addAll(definitions);
        return result;
    }

    private void resolveHierarchyReferences(List<HierarchyDefinitionElement> definitions) {
        var declared = new HashSet<String>();
        definitions.forEach(definition -> declared.add(definition.getName()));

        var usedAs = new HashMap<String, LevelType>();
        for (var reference : references) {
            if (!declared.contains(reference.name())) {
                throw new DfmParseException("Hierarchy " + reference.name() + " is not declared.",
                    reference.token().line, reference.token().column);
            }
            var firstUse = usedAs.putIfAbsent(reference.name(), reference.type());
            if (firstUse != null && firstUse != reference.type()) {
                throw new DfmParseException("Hierarchy " + reference.name() + " is used as {"
                    + marker(firstUse) + "} and as {" + marker(reference.type())
                    + "}; it can only be one of them.", reference.token().line, reference.token().column);
            }
        }

        var state = new HashMap<String, Boolean>();
        for (var definition : definitions) {
            checkForCycle(definition.getName(), state);
        }
    }

    // A shared hierarchy or cross-dimensional attribute is the target of several connections. They are counted like
    // the edges of the graph: one per distinct connection from a fact and one per distinct connection of a level.
    // A fragment may contain only some of them.
    private void checkIncomingConnections(List<FactElement> facts, List<DimensionElement> unconnected,
                                          List<HierarchyDefinitionElement> definitions) {
        if (!complete) {
            return;
        }
        var incoming = new HashMap<String, Set<List<String>>>();
        var ids = new IdentityHashMap<Object, Integer>();
        var scanned = Collections.newSetFromMap(new IdentityHashMap<DimensionElement, Boolean>());
        for (var fact : facts) {
            for (var dimension : fact.getDimensions()) {
                var chains = dimension.getHierarchies();
                var head = chains.isEmpty() ? null : chains.get(0).getHead();
                if (head != null && head.getLevelType().isReference()) {
                    incoming.computeIfAbsent(head.getName(), name -> new HashSet<>()).add(List.of(
                        String.valueOf(id(ids, fact)), String.valueOf(dimension.getFactConnectionType()),
                        String.valueOf(dimension.isFactConnectionOptional()),
                        String.valueOf(dimension.getFactConnectionLabel())));
                }
                if (scanned.add(dimension.getDefinition())) {
                    collectLevelConnections(id(ids, dimension.getDefinition()), dimension.getHierarchies(), incoming);
                }
            }
        }
        unconnected.forEach(dimension ->
            collectLevelConnections(id(ids, dimension), dimension.getHierarchies(), incoming));
        definitions.forEach(definition ->
            collectLevelConnections(id(ids, definition), definition.getHierarchies(), incoming));

        var checked = new HashSet<String>();
        for (var reference : references) {
            if (!checked.add(reference.name())) {
                continue;
            }
            int count = incoming.getOrDefault(reference.name(), Set.of()).size();
            if (count < MIN_CONNECTIONS) {
                throw new DfmParseException("Hierarchy " + reference.name() + " is the target of "
                    + (count == 0 ? "no connection" : "only one connection") + "; a {" + marker(reference.type())
                    + "} hierarchy needs at least " + MIN_CONNECTIONS + " incoming connections.",
                    reference.token().line, reference.token().column);
            }
        }
    }

    private int id(Map<Object, Integer> ids, Object element) {
        return ids.computeIfAbsent(element, key -> ids.size());
    }

    private void collectLevelConnections(int container, List<Hierarchy> chains,
                                         Map<String, Set<List<String>>> incoming) {
        for (var chain : chains) {
            for (Level level = chain.getHead(); level != null && level.getNextLevel() != null;
                 level = level.getNextLevel()) {
                var next = level.getNextLevel();
                if (next.getLevelType().isReference()) {
                    incoming.computeIfAbsent(next.getName(), name -> new HashSet<>()).add(List.of(
                        String.valueOf(container), level.getName(), String.valueOf(level.getConnectionType()),
                        String.valueOf(level.isConnectionOptional()), String.valueOf(level.getConnectionLabel())));
                }
            }
        }
    }

    // state: true while the hierarchy is being visited, false once it is done
    private void checkForCycle(String hierarchy, Map<String, Boolean> state) {
        if (state.containsKey(hierarchy)) {
            return;
        }
        state.put(hierarchy, true);
        for (var reference : references) {
            if (!hierarchy.equals(reference.owner())) {
                continue;
            }
            if (Boolean.TRUE.equals(state.get(reference.name()))) {
                throw new DfmParseException("Hierarchy " + reference.name() + " must not refer to itself, neither"
                    + " directly nor through other hierarchies.", reference.token().line, reference.token().column);
            }
            checkForCycle(reference.name(), state);
        }
        state.put(hierarchy, false);
    }

    private String marker(LevelType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private FactElement parseFact() {
        var fact = new FactElement(expectWord("Expected fact name."));
        expect(TokenType.LBRACE, "Expected '{' after fact name.");
        while (!accept(TokenType.RBRACE)) {
            boolean descriptive = false;
            if (accept(TokenType.LBRACE)) {
                expectWord("Expected 'descriptive'.");
                expect(TokenType.RBRACE, "Expected '}' after descriptive marker.");
                descriptive = true;
            }
            var name = expectWord("Expected fact field name.");
            if (descriptive) {
                fact.getDescriptives().add(name);
            } else {
                fact.getMeasures().add(name);
            }
            accept(TokenType.SEMICOLON);
        }
        return fact;
    }

    private DimensionElement parseDimension() {
        var dimension = new DimensionElement(expectWord("Expected dimension name."));
        expect(TokenType.LBRACE, "Expected '{' after dimension name.");
        while (!accept(TokenType.RBRACE)) {
            dimension.getHierarchies().add(parseHierarchy(null));
            accept(TokenType.SEMICOLON);
        }
        return dimension;
    }

    private HierarchyDefinitionElement parseHierarchyDefinition() {
        var definition = new HierarchyDefinitionElement(expectWord("Expected hierarchy name."));
        expect(TokenType.LBRACE, "Expected '{' after hierarchy name.");
        if (peek(TokenType.RBRACE)) {
            throw error("A hierarchy must contain at least one chain.");
        }
        while (!accept(TokenType.RBRACE)) {
            var start = tokens.get(position);
            var chain = parseHierarchy(definition.getName());
            var head = chain.getHead();
            if (head == null) {
                throw new DfmParseException("Expected a chain of levels.", start.line, start.column);
            }
            if (head.getLevelType().isReference()) {
                throw new DfmParseException("A hierarchy must start with a level of its own, not with a {"
                    + marker(head.getLevelType()) + "} level.", start.line, start.column);
            }
            var entry = definition.getHead();
            if (entry != null && !entry.getName().equals(head.getName())) {
                throw new DfmParseException("Every chain of hierarchy " + definition.getName()
                    + " must start with " + entry.getName() + ".", start.line, start.column);
            }
            definition.getHierarchies().add(chain);
            accept(TokenType.SEMICOLON);
        }
        return definition;
    }

    private Hierarchy parseHierarchy(String owner) {
        var hierarchy = new Hierarchy("");
        Level tail = null;
        while (!peek(TokenType.SEMICOLON) && !peek(TokenType.RBRACE)) {
            var levelType = LevelType.LEVEL;
            var optionalToken = tokens.get(position);
            boolean optional = accept(TokenType.LPAREN);
            if (accept(TokenType.LBRACE)) {
                var marker = expectWord("Expected 'descriptive', 'shared' or 'cross'.");
                expect(TokenType.RBRACE, "Expected '}' after level marker.");
                levelType = switch (marker.toLowerCase(Locale.ROOT)) {
                    case "shared" -> LevelType.SHARED;
                    case "cross" -> LevelType.CROSS;
                    default -> LevelType.DESCRIPTIVE;
                };
            }
            var nameToken = tokens.get(position);
            var level = new Level(expectWord("Expected hierarchy level name."), null);
            level.setOptional(optional);
            if (optional) {
                expect(TokenType.RPAREN, "Expected ')' after optional level.");
            }
            level.setLevelType(levelType);
            if (tail != null && tail.getConnectionType() == ConnectionType.MULTIPLE
                && !levelType.isReference() && tail.getName().equals(level.getName())) {
                throw new DfmParseException("A recursive connection cannot be multiple.",
                    nameToken.line, nameToken.column);
            }
            if (levelType.isReference()) {
                if (optional) {
                    throw new DfmParseException("A {" + marker(levelType) + "} level cannot be optional;"
                        + " mark the connection to it as optional instead.", optionalToken.line, optionalToken.column);
                }
                references.add(new HierarchyReference(level.getName(), levelType, nameToken, owner));
                if (!peek(TokenType.SEMICOLON) && !peek(TokenType.RBRACE)) {
                    throw error("A {" + marker(levelType) + "} level must be the last level of a chain.");
                }
            }
            if (tail == null) {
                hierarchy.setHead(level);
            } else {
                tail.setNextLevel(level);
            }
            tail = level;

            if (peek(TokenType.SEMICOLON) || peek(TokenType.RBRACE)) {
                break;
            }
            boolean optionalConnection = accept(TokenType.LPAREN);
            var connection = next();
            var connectionType = parseConnectionType(connection);
            if ((connection.type == TokenType.DASH || connection.type == TokenType.EQUALS)
                && accept(TokenType.LBRACKET)) {
                level.setConnectionLabel(expectWord("Expected connection description."));
                expect(TokenType.RBRACKET, "Expected ']' after connection description.");
                connectionType = parseLabeledConnectionEnd(connection);
            }
            level.setConnectionType(connectionType);
            if (optionalConnection) {
                expect(TokenType.RPAREN, "Expected ')' after optional connection.");
                level.setConnectionOptional(true);
            }
        }
        return hierarchy;
    }

    private FactDimensionElement parseConnection() {
        var from = expectWord("Expected fact name in connection.");
        boolean optional = accept(TokenType.LPAREN);
        var type = next();
        if (type.type != TokenType.DASH && type.type != TokenType.EQUALS) {
            throw error("Expected '-' or '=' in fact-dimension connection.");
        }
        String label = null;
        var relationType = parseConnectionType(type);
        if (accept(TokenType.LBRACKET)) {
            label = expectWord("Expected connection description.");
            expect(TokenType.RBRACKET, "Expected ']' after connection description.");
            relationType = parseLabeledConnectionEnd(type);
            if (relationType == ConnectionType.CONVERGENCE) {
                throw error("Convergence is not a fact-dimension connection.");
            }
        }
        if (optional) {
            expect(TokenType.RPAREN, "Expected ')' after optional connection.");
        }
        var connection = new FactDimensionElement(from, expectWord("Expected dimension name in connection."));
        connection.setRelationType(relationType);
        connection.setOptional(optional);
        connection.setLabel(label);
        return connection;
    }

    private ConnectionType parseLabeledConnectionEnd(Token opening) {
        var closing = next();
        if (opening.type == TokenType.EQUALS && closing.type == TokenType.EQUALS) {
            return ConnectionType.MULTIPLE;
        }
        if (opening.type == TokenType.DASH && closing.type == TokenType.DASH) {
            return ConnectionType.SIMPLE;
        }
        if (opening.type == TokenType.DASH && closing.type == TokenType.ARROW) {
            return ConnectionType.CONVERGENCE;
        }
        var expected = opening.type == TokenType.EQUALS ? "'='" : "'-' or '->'";
        throw new DfmParseException("Expected " + expected + " after connection description.",
            closing.line, closing.column);
    }

    private ConnectionType parseConnectionType(Token token) {
        return switch (token.type) {
            case DASH -> ConnectionType.SIMPLE;
            case EQUALS -> ConnectionType.MULTIPLE;
            case ARROW -> ConnectionType.CONVERGENCE;
            default -> throw new DfmParseException("Unknown connection type '" + token.text + "'.",
                token.line, token.column);
        };
    }

    private List<Token> tokenize(String source) {
        var result = new ArrayList<Token>();
        int line = 1;
        int column = 1;
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                if (c == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
                i++;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                    column++;
                }
            } else if (Character.isLetterOrDigit(c) || c == '_') {
                int tokenLine = line;
                int tokenColumn = column;
                int start = i++;
                column++;
                while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) {
                    i++;
                    column++;
                }
                result.add(new Token(TokenType.WORD, source.substring(start, i), tokenLine, tokenColumn));
            } else if (c == '-' && i + 1 < source.length() && source.charAt(i + 1) == '>') {
                result.add(new Token(TokenType.ARROW, "->", line, column));
                i += 2;
                column += 2;
            } else {
                var type = switch (c) {
                    case '{' -> TokenType.LBRACE;
                    case '}' -> TokenType.RBRACE;
                    case ';' -> TokenType.SEMICOLON;
                    case '-' -> TokenType.DASH;
                    case '=' -> TokenType.EQUALS;
                    case '(' -> TokenType.LPAREN;
                    case ')' -> TokenType.RPAREN;
                    case '[' -> TokenType.LBRACKET;
                    case ']' -> TokenType.RBRACKET;
                    default -> throw new DfmParseException("Unexpected character '" + c + "'.", line, column);
                };
                result.add(new Token(type, String.valueOf(c), line, column));
                i++;
                column++;
            }
        }
        result.add(new Token(TokenType.END, "", line, column));
        return result;
    }

    private boolean acceptWord(String word) {
        if (peek(TokenType.WORD) && tokens.get(position).text.equalsIgnoreCase(word)) {
            position++;
            return true;
        }
        return false;
    }

    private String expectWord(String message) {
        if (!peek(TokenType.WORD)) {
            throw error(message);
        }
        return next().text;
    }

    private boolean accept(TokenType type) {
        if (peek(type)) {
            position++;
            return true;
        }
        return false;
    }

    private Token next() {
        var token = tokens.get(position);
        if (token.type != TokenType.END) {
            position++;
        }
        return token;
    }

    private void expect(TokenType type, String message) {
        if (!accept(type)) {
            throw error(message);
        }
    }

    private boolean peek(TokenType type) {
        return tokens.get(position).type == type;
    }

    private DfmParseException error(String message) {
        var token = tokens.get(position);
        return new DfmParseException(message + " At token " + token.text + ".", token.line, token.column);
    }

    private record Token(TokenType type, String text, int line, int column) {
    }

    // owner is the hierarchy declaration the reference occurs in, or null if it occurs in a dimension
    private record HierarchyReference(String name, LevelType type, Token token, String owner) {
    }

    private enum TokenType {
        WORD, LBRACE, RBRACE, SEMICOLON, DASH, EQUALS, ARROW, LPAREN, RPAREN, LBRACKET, RBRACKET, END
    }
}
