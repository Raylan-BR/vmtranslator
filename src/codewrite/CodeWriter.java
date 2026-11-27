package codewrite;

import java.io.IOException;
import java.io.Writer;

public class CodeWriter implements AutoCloseable {

    private final Writer out;
    private int labelCounter = 0;
    private String fileName;

    // Prefixo dos rótulos fora de funções; não colide com funções Jack, que sempre têm ponto (Classe.metodo).
    private static final String NO_FUNCTION = "Global";
    private String currentFunction = NO_FUNCTION;

    public CodeWriter(Writer out) {
        this.out = out;
    }

    public void writeArithmetic(String cmd) throws IOException {
        switch (cmd) {
            case "add" -> binary("D+M");
            case "sub" -> binary("M-D"); // x - y: M é x (abaixo), D é y (topo)
            case "and" -> binary("D&M");
            case "or"  -> binary("D|M");
            case "neg" -> unary("-M");
            case "not" -> unary("!M");
            case "eq"  -> compare("JEQ");
            case "gt"  -> compare("JGT");
            case "lt"  -> compare("JLT");
            default -> throw new IllegalArgumentException("Comando aritmético inválido: " + cmd);
        }
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFunctionName(String functionName) {
        this.currentFunction = functionName;
    }

    public void writeLabel(String label) throws IOException {
        emit("(" + scoped(label) + ")");
    }

    public void writeGoto(String label) throws IOException {
        emit("@" + scoped(label), "0;JMP");
    }

    // Desempilha o topo e salta se ele for diferente de zero (qualquer valor != 0 é verdadeiro).
    public void writeIf(String label) throws IOException {
        emit("@SP", "AM=M-1", "D=M", "@" + scoped(label), "D;JNE");
    }

    // Rótulo no formato Funcao$LABEL, para que LOOP em funções diferentes não colida.
    private String scoped(String label) {
        return currentFunction + "$" + label;
    }

    public void writePush(String segment, int index) throws IOException {
        switch (segment) {
            case "constant" -> emit("@" + index, "D=A");
            case "local", "argument", "this", "that" ->
                emit("@" + index, "D=A", "@" + baseOf(segment), "A=D+M", "D=M");
            default -> emit("@" + fixedAddress(segment, index), "D=M");
        }
        emit("@SP", "A=M", "M=D", "@SP", "M=M+1");
    }

    public void writePop(String segment, int index) throws IOException {
        switch (segment) {
            case "local", "argument", "this", "that" -> emit(
                // Endereço calculado vai para R13 porque D será usado pelo valor desempilhado.
                "@" + index, "D=A", "@" + baseOf(segment), "D=D+M", "@R13", "M=D",
                "@SP", "AM=M-1", "D=M", "@R13", "A=M", "M=D");
            case "constant" -> throw new IllegalArgumentException("pop constant não é permitido");
            default -> emit("@SP", "AM=M-1", "D=M", "@" + fixedAddress(segment, index), "M=D");
        }
    }
    public void writeFunction(String functionName, int nVars) throws IOException {
        setFunctionName(functionName);
        emit("(" + functionName + ")");
        for (int i = 0; i < nVars; i++) {
            writePush("constant", 0);
        }
    }
    private static String baseOf(String segment) {
        return switch (segment) {
            case "local" -> "LCL";
            case "argument" -> "ARG";
            case "this" -> "THIS";
            default -> "THAT";
        };
    }

    // Segmentos com endereço conhecido em tempo de tradução: temp (R5-R12), pointer (THIS/THAT) e static.
    private String fixedAddress(String segment, int index) {
        switch (segment) {
            case "temp" -> {
                checkRange(segment, index, 7);
                return String.valueOf(5 + index);
            }
            case "pointer" -> {
                checkRange(segment, index, 1);
                return index == 0 ? "THIS" : "THAT";
            }
            case "static" -> {
                if (fileName == null) throw new IllegalStateException("Chame setFileName() antes de usar static.");
                return fileName + "." + index;
            }
            default -> throw new IllegalArgumentException("Segmento inválido: " + segment);
        }
    }

    private static void checkRange(String segment, int index, int max) {
        if (index < 0 || index > max) {
            throw new IllegalArgumentException("Índice fora do intervalo para " + segment + ": " + index);
        }
    }

    // Desempilha y em D e deixa A apontando para x, que recebe o resultado.
    private void binary(String comp) throws IOException {
        emit("@SP", "AM=M-1", "D=M", "A=A-1", "M=" + comp);
    }

    private void unary(String comp) throws IOException {
        emit("@SP", "A=M-1", "M=" + comp);
    }

    // shortcut: x - y pode estourar 16 bits em gt/lt com sinais opostos e magnitudes grandes; tratar se algum teste exigir.
    private void compare(String jump) throws IOException {
        String label = "COMP_TRUE_" + labelCounter++;
        emit("@SP", "AM=M-1", "D=M", "A=A-1", "D=M-D", "M=-1",
             "@" + label, "D;" + jump,
             "@SP", "A=M-1", "M=0",
             "(" + label + ")");
    }

    private void emit(String... lines) throws IOException {
        for (String line : lines) {
            out.write(line);
            out.write('\n');
        }
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
