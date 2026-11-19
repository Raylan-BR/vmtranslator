package parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class Parser implements AutoCloseable {

    private static final Set<String> ARITHMETIC_COMMANDS = Set.of(
        "add", "sub", "neg", "eq", "gt", "lt", "and", "or", "not"
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

        if ("push".equals(op)) {
            return CommandType.C_PUSH;
        }
        if ("pop".equals(op)) {
            return CommandType.C_POP;
        }
        if (ARITHMETIC_COMMANDS.contains(op)) {
            return CommandType.C_ARITHMETIC;
        }

        throw new IllegalArgumentException("Comando VM inválido ou não suportado nesta etapa: " + op);
    }

    public String arg1() {
        CommandType type = commandType();
        if (type == CommandType.C_ARITHMETIC) {
            return currentTokens[0];
        }
        return currentTokens[1];
    }

    public int arg2() {
        CommandType type = commandType();
        if (type != CommandType.C_PUSH && type != CommandType.C_POP) {
            throw new IllegalStateException("arg2() só pode ser chamado para comandos PUSH ou POP.");
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
