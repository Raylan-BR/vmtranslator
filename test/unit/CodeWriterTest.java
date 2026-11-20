package unit;

import codewrite.CodeWriter;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CodeWriterTest {

    // Executa um comando sobre a pilha [x, y] (ou [x] se unário) e devolve {topo, SP}.
    private static int[] run(String cmd, int... stack) throws IOException {
        StringWriter sw = new StringWriter();
        try (CodeWriter cw = new CodeWriter(sw)) {
            cw.writeArithmetic(cmd);
        }
        short[] ram = new short[512];
        ram[0] = (short) (256 + stack.length);
        for (int i = 0; i < stack.length; i++) ram[256 + i] = (short) stack[i];
        execute(sw.toString(), ram);
        return new int[] { ram[ram[0] - 1], ram[0] };
    }

    // Mini-emulador Hack: suporta apenas as instruções que o CodeWriter emite.
    private static void execute(String asm, short[] ram) {
        List<String> prog = new ArrayList<>();
        Map<String, Integer> labels = new HashMap<>();
        for (String l : asm.split("\n")) {
            if (l.startsWith("(")) labels.put(l.substring(1, l.length() - 1), prog.size());
            else prog.add(l);
        }
        short a = 0, d = 0;
        for (int pc = 0; pc < prog.size(); pc++) {
            String ins = prog.get(pc);
            if (ins.startsWith("@")) {
                String s = ins.substring(1);
                a = (short) (s.equals("SP") ? 0 : labels.getOrDefault(s, -1));
                continue;
            }
            String dest = "", comp = ins, jump = "";
            if (comp.contains(";")) { jump = comp.split(";")[1]; comp = comp.split(";")[0]; }
            if (comp.contains("=")) { dest = comp.split("=")[0]; comp = comp.split("=")[1]; }
            short m = ram[a];
            short v = (short) switch (comp) {
                case "M" -> m;
                case "D" -> d;
                case "M-1" -> m - 1;
                case "A-1" -> a - 1;
                case "D+M" -> d + m;
                case "M-D" -> m - d;
                case "D&M" -> d & m;
                case "D|M" -> d | m;
                case "-M" -> -m;
                case "!M" -> ~m;
                case "-1" -> -1;
                case "0" -> 0;
                default -> throw new IllegalStateException("comp não suportado: " + comp);
            };
            if (dest.contains("M")) ram[a] = v;
            if (dest.contains("D")) d = v;
            if (dest.contains("A")) a = v;
            boolean jmp = switch (jump) {
                case "JEQ" -> v == 0;
                case "JGT" -> v > 0;
                case "JLT" -> v < 0;
                case "" -> false;
                default -> throw new IllegalStateException("jump não suportado: " + jump);
            };
            if (jmp) pc = a - 1;
        }
    }

    @Test
    void operacoesBinariasRespeitamOrdemDosOperandos() throws IOException {
        assertArrayEquals(new int[] { 15, 257 }, run("add", 10, 5));
        assertArrayEquals(new int[] { 5, 257 }, run("sub", 10, 5));
        assertArrayEquals(new int[] { -5, 257 }, run("sub", 5, 10));
        assertArrayEquals(new int[] { 0b1000, 257 }, run("and", 0b1100, 0b1010));
        assertArrayEquals(new int[] { 0b1110, 257 }, run("or", 0b1100, 0b1010));
    }

    @Test
    void operacoesUnariasNaoAlteramSP() throws IOException {
        assertArrayEquals(new int[] { -7, 257 }, run("neg", 7));
        assertArrayEquals(new int[] { -1, 257 }, run("not", 0));
    }

    @Test
    void comparacoesRetornamMenosUmOuZero() throws IOException {
        assertEquals(-1, run("eq", 17, 17)[0]);
        assertEquals(0, run("eq", 17, 16)[0]);
        assertEquals(-1, run("gt", 32767, 32766)[0]);
        assertEquals(0, run("gt", 32766, 32767)[0]);
        assertEquals(0, run("gt", 5, 5)[0]);
        assertEquals(-1, run("lt", 891, 892)[0]);
        assertEquals(0, run("lt", 892, 891)[0]);
        assertEquals(0, run("lt", 5, 5)[0]);
        assertEquals(257, run("lt", 1, 2)[1]);
    }

    @Test
    void cadaComparacaoGeraRotuloUnico() throws IOException {
        StringWriter sw = new StringWriter();
        try (CodeWriter cw = new CodeWriter(sw)) {
            for (String c : new String[] { "eq", "gt", "lt", "eq" }) cw.writeArithmetic(c);
        }
        List<String> labels = sw.toString().lines().filter(l -> l.startsWith("(")).toList();
        assertEquals(4, labels.size());
        assertEquals(4, labels.stream().distinct().count());
    }

    @Test
    void comandoInvalidoLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> new CodeWriter(new StringWriter()).writeArithmetic("mul"));
    }
}
