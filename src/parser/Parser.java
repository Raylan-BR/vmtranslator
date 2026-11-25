package parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

public class Parser implements AutoCloseable {

    private static final Set<String> ARITHMETIC_COMMANDS = Set.of(
        "add", "sub", "neg", "eq", "gt", "lt", "and", "or", "not"
    );

    private static final Map<String, CommandType> KEYWORD_COMMANDS = Map.of(
        "push", CommandType.C_PUSH,
        "pop", CommandType.C_POP,
        "label", CommandType.C_LABEL,
        "goto", CommandType.C_GOTO,
        "if-goto", CommandType.C_IF,
        "function", CommandType.C_FUNCTION,
        "call", CommandType.C_CALL,
        "return", CommandType.C_RETURN
    );

    private final BufferedReader reader;
    private String nextValidLine;
    private String[] currentTokens;

    // Construtor principal: aceita qualquer Reader (ideal para testes com StringReader)
    public Parser(Reader source) throws IOException {
        this.reader = (source instanceof BufferedReader)
            ? (BufferedReader) source
            : new BufferedReader(source);
        this.nextValidLine = fetchNextValidLine();
    }

    // Construtor de conveniência para arquivos reais no Main
    public Parser(File vmFile) throws IOException {
        this(new FileReader(vmFile, StandardCharsets.UTF_8));
    }

    public boolean hasMoreLines() {
        return nextValidLine != null;
    }

    public void advance() throws IOException {
        if (!hasMoreLines()) {
            throw new IllegalStateException("Não há mais instruções válidas para avançar.");
        }
        // Divide por qualquer sequência de espaços ou tabulações
        currentTokens = nextValidLine.split("\\s+");
        nextValidLine = fetchNextValidLine();
    }

    public CommandType commandType() {
        ensureCommandLoaded();
        String op = currentTokens[0];

        if (ARITHMETIC_COMMANDS.contains(op)) {
            return CommandType.C_ARITHMETIC;
        }
        CommandType type = KEYWORD_COMMANDS.get(op);
        if (type == null) {
            throw new IllegalArgumentException("Comando VM inválido: " + op);
        }
        return type;
    }

    public String arg1() {
        CommandType type = commandType();
        if (type == CommandType.C_RETURN) {
            throw new IllegalStateException("arg1() não pode ser chamado para RETURN.");
        }
        if (type == CommandType.C_ARITHMETIC) {
            return currentTokens[0];
        }
        return currentTokens[1];
    }

    public int arg2() {
        CommandType type = commandType();
        if (type != CommandType.C_PUSH && type != CommandType.C_POP
                && type != CommandType.C_FUNCTION && type != CommandType.C_CALL) {
            throw new IllegalStateException("arg2() só pode ser chamado para PUSH, POP, FUNCTION ou CALL.");
        }
        return Integer.parseInt(currentTokens[2]);
    }

    private String fetchNextValidLine() throws IOException {
        String rawLine;
        while ((rawLine = reader.readLine()) != null) {
            String cleaned = sanitize(rawLine);
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return null;
    }

    private String sanitize(String line) {
        int commentIndex = line.indexOf("//");
        if (commentIndex != -1) {
            line = line.substring(0, commentIndex);
        }
        return line.trim();
    }

    private void ensureCommandLoaded() {
        if (currentTokens == null) {
            throw new IllegalStateException("Chame advance() antes de consultar o comando atual.");
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
