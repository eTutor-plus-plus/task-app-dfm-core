package at.jku.dke.dfm.core.parser;

import at.jku.dke.dfm.core.models.ast.AbstractElement;
import at.jku.dke.dfm.core.models.ast.DimensionElement;
import at.jku.dke.dfm.core.models.ast.FactDimensionElement;
import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.models.ast.Hierarchy;
import at.jku.dke.dfm.core.models.ast.Level;
import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.LevelType;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the DFM notation used by DFM tasks.
 */
public class DfmParser {
    private List<Token> tokens;
    private int position;

    public List<AbstractElement> parse(String source) {
        if (source == null || source.isBlank()) {
            throw new DfmParseException("DFM input must not be empty.");
        }
        tokens = tokenize(source);
        position = 0;
        var facts = new ArrayList<FactElement>();
        var dimensions = new ArrayList<DimensionElement>();
        var connections = new ArrayList<FactDimensionElement>();

        while (!peek(TokenType.END)) {
            if (acceptWord("fact")) {
                facts.add(parseFact());
            } else if (acceptWord("dimension")) {
                dimensions.add(parseDimension());
            } else {
                connections.add(parseConnection());
            }
            expect(TokenType.SEMICOLON, "Expected ';' after declaration.");
        }

        for (var connection : connections) {
            var fact = facts.stream().filter(candidate -> candidate.getName().equals(connection.getRelationFromName()))
                .findFirst().orElseThrow(() -> new DfmParseException("Fact not found for connection "
                    + connection.getRelationFromName() + " - " + connection.getRelationToName()));
            var dimension = dimensions.stream().filter(candidate -> candidate.getName().equals(connection.getRelationToName()))
                .findFirst().orElseThrow(() -> new DfmParseException("Dimension not found for connection "
                    + connection.getRelationFromName() + " - " + connection.getRelationToName()));
            fact.getDimensions().add(dimension);
            dimensions.remove(dimension);
        }

        var result = new ArrayList<AbstractElement>(facts);
        result.addAll(dimensions);
        return result;
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
            dimension.getHierarchies().add(parseHierarchy());
            accept(TokenType.SEMICOLON);
        }
        return dimension;
    }

    private Hierarchy parseHierarchy() {
        var hierarchy = new Hierarchy("");
        Level tail = null;
        while (!peek(TokenType.SEMICOLON) && !peek(TokenType.RBRACE)) {
            boolean descriptive = false;
            boolean optional = accept(TokenType.LPAREN);
            if (accept(TokenType.LBRACE)) {
                expectWord("Expected 'descriptive'.");
                expect(TokenType.RBRACE, "Expected '}' after descriptive marker.");
                descriptive = true;
            }
            var level = new Level(expectWord("Expected hierarchy level name."), null);
            level.setOptional(optional);
            if (optional) {
                expect(TokenType.RPAREN, "Expected ')' after optional level.");
            }
            level.setLevelType(descriptive ? LevelType.DESCRIPTIVE : LevelType.LEVEL);
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
            level.setConnectionType(parseConnectionType(connection));
            if (optionalConnection) {
                expect(TokenType.RPAREN, "Expected ')' after optional connection.");
                level.setConnectionOptional(true);
            }
        }
        return hierarchy;
    }

    private FactDimensionElement parseConnection() {
        var from = expectWord("Expected fact name in connection.");
        var type = next();
        if (type.type != TokenType.DASH) {
            throw error("Expected '-' in fact-dimension connection.");
        }
        var connection = new FactDimensionElement(from, expectWord("Expected dimension name in connection."));
        connection.setRelationType(ConnectionType.SIMPLE);
        return connection;
    }

    private ConnectionType parseConnectionType(Token token) {
        return switch (token.type) {
            case DASH -> ConnectionType.SIMPLE;
            case EQUALS -> ConnectionType.MULTIPLE;
            case ARROW -> ConnectionType.CONVERGENCE;
            default -> throw error("Unknown connection type '" + token.text + "'.");
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
        return tokens.get(position++).text;
    }

    private boolean accept(TokenType type) {
        if (peek(type)) {
            position++;
            return true;
        }
        return false;
    }

    private Token next() {
        return tokens.get(position++);
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

    private enum TokenType {
        WORD, LBRACE, RBRACE, SEMICOLON, DASH, EQUALS, ARROW, LPAREN, RPAREN, END
    }
}
