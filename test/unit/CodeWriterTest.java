package unit;

import codewrite.CodeWriter;
import parser.Parser;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
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

    // Traduz um programa VM completo usando Parser + CodeWriter.
    private static String translate(String vm, String fileName) throws IOException {
        StringWriter sw = new StringWriter();
        try (Parser parser = new Parser(new StringReader(vm)); CodeWriter cw = new CodeWriter(sw)) {
            cw.setFileName(fileName);
            while (parser.hasMoreLines()) {
                parser.advance();
                switch (parser.commandType()) {
                    case C_ARITHMETIC -> cw.writeArithmetic(parser.arg1());
                    case C_PUSH -> cw.writePush(parser.arg1(), parser.arg2());
                    case C_POP -> cw.writePop(parser.arg1(), parser.arg2());
                    case C_LABEL -> cw.writeLabel(parser.arg1());
                    case C_GOTO -> cw.writeGoto(parser.arg1());
                    case C_IF -> cw.writeIf(parser.arg1());
                    case C_FUNCTION -> cw.writeFunction(parser.arg1(), parser.arg2());
                    case C_CALL -> cw.writeCall(parser.arg1(), parser.arg2());
                }
            }
        }
        return sw.toString();
    }

    private static short[] runOfficial(String path, int... init) throws IOException {
        File vm = new File("test/validation/" + path);
        short[] ram = new short[4096];
        for (int i = 0; i < init.length; i++) ram[i] = (short) init[i];
        execute(translate(Files.readString(vm.toPath()), vm.getName().replace(".vm", "")), ram);
        return ram;
    }

    // Mini-emulador Hack: suporta apenas as instruções que o CodeWriter emite.
    private static void execute(String asm, short[] ram) {
        List<String> prog = new ArrayList<>();
        Map<String, Integer> symbols = new HashMap<>(Map.of("SP", 0, "LCL", 1, "ARG", 2, "THIS", 3, "THAT", 4));
        for (int i = 0; i < 16; i++) symbols.put("R" + i, i);
        for (String l : asm.split("\n")) {
            if (l.startsWith("(")) symbols.put(l.substring(1, l.length() - 1), prog.size());
            else prog.add(l);
        }
        int nextVar = 16;
        short a = 0, d = 0;
        for (int pc = 0, steps = 0; pc < prog.size(); pc++) {
            if (++steps > 1_000_000) throw new IllegalStateException("possível loop infinito no Assembly gerado");
            String ins = prog.get(pc);
            if (ins.startsWith("@")) {
                String s = ins.substring(1);
                if (s.matches("\\d+")) a = Short.parseShort(s);
                else {
                    if (!symbols.containsKey(s)) symbols.put(s, nextVar++);
                    a = (short) (int) symbols.get(s);
                }
                continue;
            }
            String dest = "", comp = ins, jump = "";
            if (comp.contains(";")) { jump = comp.split(";")[1]; comp = comp.split(";")[0]; }
            if (comp.contains("=")) { dest = comp.split("=")[0]; comp = comp.split("=")[1]; }
            short m = a >= 0 && a < ram.length ? ram[a] : 0; // A pode conter uma constante, não um endereço
            short v = (short) switch (comp) {
                case "M" -> m;
                case "D" -> d;
                case "A" -> a;
                case "M+1" -> m + 1;
                case "M-1" -> m - 1;
                case "A-1" -> a - 1;
                case "D+M" -> d + m;
                case "M-D" -> m - d;
                case "D&M" -> d & m;
                case "D-A" -> d - a;
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
                case "JNE" -> v != 0;
                case "JMP" -> true;
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
    void pushEPopEmCadaSegmento() throws IOException {
        String asm = translate("""
            push constant 7
            pop local 2
            push constant 8
            pop argument 1
            push constant 3000
            pop pointer 0
            push constant 9
            pop this 4
            push constant 10
            pop temp 7
            push constant 11
            pop static 5
            push local 2
            push argument 1
            push this 4
            push temp 7
            push static 5
            push pointer 0
            """, "Foo");
        // R13 só nos pops com endereço calculado (local, argument, this): 3 pops x 2 usos.
        assertEquals(6, asm.lines().filter(l -> l.equals("@R13")).count());
        short[] ram = new short[4096];
        ram[0] = 256; ram[1] = 300; ram[2] = 400;
        execute(asm, ram);
        assertEquals(7, ram[302]);
        assertEquals(8, ram[401]);
        assertEquals(3000, ram[3]);
        assertEquals(9, ram[3004]);
        assertEquals(10, ram[12]);
        assertEquals(11, ram[16]); // primeira variável Foo.5
        assertTrue(asm.contains("@Foo.5"));
        assertEquals(262, ram[0]);
        assertArrayEquals(new short[] { 7, 8, 9, 10, 11, 3000 }, java.util.Arrays.copyOfRange(ram, 256, 262));
    }

    @Test
    void segmentosInvalidosLancamExcecao() {
        CodeWriter cw = new CodeWriter(new StringWriter());
        assertThrows(IllegalArgumentException.class, () -> cw.writePop("constant", 0));
        assertThrows(IllegalArgumentException.class, () -> cw.writePush("temp", 8));
        assertThrows(IllegalArgumentException.class, () -> cw.writePush("pointer", 2));
        assertThrows(IllegalArgumentException.class, () -> cw.writePush("heap", 0));
        assertThrows(IllegalStateException.class, () -> cw.writePush("static", 0));
    }

    @Test
    void naoInicializaPonteiros() throws IOException {
        String asm = translate("push constant 1\npop local 0\n", "X");
        assertFalse(asm.startsWith("@256"), "SP não deve ser inicializado nesta etapa");
        // SP volta ao valor inicial e LCL não é tocado: nada é inicializado pelo tradutor.
        short[] ram = new short[4096];
        ram[0] = 256; ram[1] = 300;
        execute(asm, ram);
        assertEquals(256, ram[0]);
        assertEquals(300, ram[1]);
    }

    // Valores esperados copiados dos .cmp; RAM inicial copiada dos .tst.
    @Test
    void validacaoOficialSimpleAdd() throws IOException {
        short[] ram = runOfficial("project7/StackArithmetic/SimpleAdd/SimpleAdd.vm", 256);
        assertEquals(257, ram[0]);
        assertEquals(15, ram[256]);
    }

    @Test
    void validacaoOficialStackTest() throws IOException {
        short[] ram = runOfficial("project7/StackArithmetic/StackTest/StackTest.vm", 256);
        assertEquals(266, ram[0]);
        assertArrayEquals(new short[] { -1, 0, 0, 0, -1, 0, -1, 0, 0, -91 },
            java.util.Arrays.copyOfRange(ram, 256, 266));
    }

    @Test
    void validacaoOficialBasicTest() throws IOException {
        short[] ram = runOfficial("project7/MemoryAccess/BasicTest/BasicTest.vm", 256, 300, 400, 3000, 3010);
        assertEquals(472, ram[256]);
        assertEquals(10, ram[300]);
        assertEquals(21, ram[401]);
        assertEquals(22, ram[402]);
        assertEquals(36, ram[3006]);
        assertEquals(42, ram[3012]);
        assertEquals(45, ram[3015]);
        assertEquals(510, ram[11]);
    }

    @Test
    void validacaoOficialPointerTest() throws IOException {
        short[] ram = runOfficial("project7/MemoryAccess/PointerTest/PointerTest.vm", 256);
        assertEquals(6084, ram[256]);
        assertEquals(3030, ram[3]);
        assertEquals(3040, ram[4]);
        assertEquals(32, ram[3032]);
        assertEquals(46, ram[3046]);
    }

    @Test
    void validacaoOficialStaticTest() throws IOException {
        short[] ram = runOfficial("project7/MemoryAccess/StaticTest/StaticTest.vm", 256);
        assertEquals(1110, ram[256]);
    }

    @Test
    void comandoInvalidoLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> new CodeWriter(new StringWriter()).writeArithmetic("mul"));
    }

    // --- Controle de fluxo (Project 08) ---

    @Test
    void rotulosForaDeFuncaoUsamPrefixoFixo() throws IOException {
        StringWriter sw = new StringWriter();
        try (CodeWriter cw = new CodeWriter(sw)) {
            cw.writeLabel("LOOP");
            cw.writeGoto("LOOP");
        }
        assertEquals("(Global$LOOP)\n@Global$LOOP\n0;JMP\n", sw.toString());
    }

    @Test
    void rotulosDeFuncoesDiferentesNaoColidem() throws IOException {
        StringWriter sw = new StringWriter();
        try (CodeWriter cw = new CodeWriter(sw)) {
            cw.setFunctionName("Main.fibonacci");
            cw.writeLabel("LOOP");
            cw.setFunctionName("Sys.init");
            cw.writeLabel("LOOP");
        }
        assertEquals("(Main.fibonacci$LOOP)\n(Sys.init$LOOP)\n", sw.toString());
    }

    @Test
    void ifGotoDesempilhaESaltaSomenteSeDiferenteDeZero() throws IOException {
        // Se saltar, pula o "push constant 99"; a pilha termina com SP = 256 + o que sobrou.
        String vm = """
            push constant %d
            if-goto SKIP
            push constant 99
            label SKIP
            """;

        short[] ram = new short[512];
        ram[0] = 256;
        execute(translate(vm.formatted(-1), "T"), ram); // verdadeiro (-1): salta
        assertEquals(256, ram[0], "condição foi desempilhada e o push 99 foi pulado");

        ram = new short[512];
        ram[0] = 256;
        execute(translate(vm.formatted(7), "T"), ram); // qualquer valor != 0 também salta
        assertEquals(256, ram[0]);

        ram = new short[512];
        ram[0] = 256;
        execute(translate(vm.formatted(0), "T"), ram); // falso (0): não salta
        assertEquals(257, ram[0]);
        assertEquals(99, ram[256]);
    }

    @Test
    void validacaoOficialBasicLoop() throws IOException {
        short[] ram = new short[4096];
        ram[0] = 256; ram[1] = 300; ram[2] = 400; ram[400] = 3;
        execute(translate(Files.readString(new File("test/validation/project8/BasicLoop/BasicLoop.vm").toPath()), "BasicLoop"), ram);
        assertEquals(257, ram[0]);
        assertEquals(6, ram[256]);
    }

    @Test
    void validacaoOficialFibonacciSeries() throws IOException {
        short[] ram = new short[4096];
        ram[0] = 256; ram[1] = 300; ram[2] = 400; ram[400] = 6; ram[401] = 3000;
        execute(translate(Files.readString(new File("test/validation/project8/FibonacciSeries/FibonacciSeries.vm").toPath()), "FibonacciSeries"), ram);
        assertArrayEquals(new short[] { 0, 1, 1, 2, 3, 5 }, java.util.Arrays.copyOfRange(ram, 3000, 3006));
    }

    @Test
    void functionComZeroVariaveisGeraApenasRotuloENaoAlteraPilha() throws IOException {
        String asm = translate("function Sys.init 0\n", "Sys");
        assertEquals("(Sys.init)\n", asm);

        short[] ram = new short[512];
        ram[0] = 256; // SP
        ram[1] = 256; // LCL
        ram[256] = 99; // lixo na memória para garantir que não será sobrescrito
        execute(asm, ram);

        assertEquals(256, ram[0], "SP não deve avançar quando nVars == 0");
        assertEquals(99, ram[256], "Nenhuma posição deve ser zerada quando nVars == 0");
    }

    @Test
    void functionInicializaVariaveisLocaisComZero() throws IOException {
        String asm = translate("function Main.soma 3\n", "Main");
        assertTrue(asm.startsWith("(Main.soma)\n"));

        short[] ram = new short[512];
        ram[0] = 300; // SP inicial (apontando para a base do novo frame)
        ram[1] = 300; // LCL já posicionado pelo call antes de entrar na função
        // Preenche com lixo para provar que o writeFunction realmente zera os 3 espaços
        ram[300] = 77;
        ram[301] = 88;
        ram[302] = 99;
        ram[303] = 55; // posição fora do escopo (não deve ser tocada)

        execute(asm, ram);

        assertEquals(303, ram[0], "SP deve avançar nVars posições");
        assertArrayEquals(new short[] { 0, 0, 0 }, java.util.Arrays.copyOfRange(ram, 300, 303));
        assertEquals(55, ram[303], "Não deve zerar além de nVars");
    }

    @Test
    void functionAtualizaEscopoDeRotulosInternos() throws IOException {
        String asm = translate("""
            function ClassA.foo 1
            label LOOP
            goto LOOP
            function ClassB.bar 0
            label LOOP
            """, "Test");

        assertTrue(asm.contains("(ClassA.foo)\n"));
        assertTrue(asm.contains("(ClassA.foo$LOOP)\n@ClassA.foo$LOOP\n0;JMP\n"));
        assertTrue(asm.contains("(ClassB.bar)\n(ClassB.bar$LOOP)\n"));
    }
    
    @Test
    void callSalvaFrameReposicionaPonteirosESaltaParaFuncao() throws IOException {
        // Simula 2 argumentos já empilhados em 256 e 257 (SP = 258).
        // A função chamada escreve o resultado da soma dos 2 argumentos em ARG[0] e volta manualmente para o return-address.
        String vm = """
            call Math.soma 2
            push constant 99
            goto FIM
            function Math.soma 0
            push argument 0
            push argument 1
            add
            pop argument 0
            label FIM
            """;

        short[] ram = new short[512];
        ram[0] = 258;  // SP (após 2 argumentos empilhados)
        ram[1] = 300;  // LCL da chamadora
        ram[2] = 400;  // ARG da chamadora
        ram[3] = 3000; // THIS da chamadora
        ram[4] = 4000; // THAT da chamadora
        ram[256] = 10; // arg 0
        ram[257] = 25; // arg 1

        String asm = translate(vm, "Main");
        execute(asm, ram);

        // Verifica o frame salvo na pilha (endereços 258 a 262):
        // ram[258] contém o endereço de retorno na ROM (> 0)
        assertTrue(ram[258] > 0, "Endereço de retorno deve ter sido empilhado");
        assertEquals(300, ram[259], "LCL antigo deve ser salvo");
        assertEquals(400, ram[260], "ARG antigo deve ser salvo");
        assertEquals(3000, ram[261], "THIS antigo deve ser salvo");
        assertEquals(4000, ram[262], "THAT antigo deve ser salvo");

        // Verifica os novos ponteiros da função chamada:
        assertEquals(256, ram[2], "ARG deve apontar para o primeiro argumento (263 - 5 - 2 = 256)");
        assertEquals(263, ram[1], "LCL deve apontar para o topo da pilha logo após o frame (263)");

        // Verifica que o salto para Math.soma realmente ocorreu e leu ARG[0] + ARG[1] = 35
        assertEquals(35, ram[256], "A função chamada deve ter acessado os argumentos via novo ARG");
    }

    @Test
    void callSemArgumentosReposicionaARGParaMesmoEnderecoDeLCL() throws IOException {
        String vm = """
            call Sys.init 0
            goto FIM
            function Sys.init 0
            label FIM
            """;

        short[] ram = new short[512];
        ram[0] = 256; // SP inicial (0 argumentos)
        ram[1] = 300;
        ram[2] = 400;
        ram[3] = 3000;
        ram[4] = 4000;

        execute(translate(vm, "Sys"), ram);

        // Com 0 argumentos, frame ocupa 256..260, então SP = 261, LCL = 261 e ARG = 261 - 5 - 0 = 256
        assertEquals(261, ram[0]);
        assertEquals(261, ram[1], "LCL deve ser 261");
        assertEquals(256, ram[2], "ARG deve ser 256 (base do frame)");
    }

    @Test
    void multiplasChamadasGeramEnderecosDeRetornoExclusivos() throws IOException {
        StringWriter sw = new StringWriter();
        try (CodeWriter cw = new CodeWriter(sw)) {
            cw.setFunctionName("Main.main");
            cw.writeCall("Math.multiply", 2);
            cw.writeCall("Math.multiply", 2);
        }
        String asm = sw.toString();

        List<String> returnLabels = asm.lines()
            .filter(l -> l.startsWith("(Main.main$ret."))
            .toList();

        assertEquals(2, returnLabels.size(), "Deve gerar 2 rótulos de retorno");
        assertNotEquals(returnLabels.get(0), returnLabels.get(1), "Os rótulos de retorno devem ser únicos");
    }
}
