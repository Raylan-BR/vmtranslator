package codewrite;

import java.io.IOException;
import java.io.Writer;

public class CodeWriter implements AutoCloseable {

    private final Writer out;
    private int labelCounter = 0;

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
