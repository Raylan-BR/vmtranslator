package unit;
import parser.Parser;
import parser.CommandType;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    @Test
    void deveIgnorarArquivoVazioOuApenasComentarios() throws IOException {
        String vmCode = """
            // Comentário no topo
            
               // Comentário indentado
            \t
            """;

        try (Parser parser = new Parser(new StringReader(vmCode))) {
            assertFalse(parser.hasMoreLines());
        }
    }

    @Test
    void deveLimparComentariosInlineEEspacosIrregulares() throws IOException {
        String vmCode = "   push\t\tconstant    17   // empilha 17\n";

        try (Parser parser = new Parser(new StringReader(vmCode))) {
            assertTrue(parser.hasMoreLines());
            parser.advance();

            assertEquals(CommandType.C_PUSH, parser.commandType());
            assertEquals("constant", parser.arg1());
            assertEquals(17, parser.arg2());
            assertFalse(parser.hasMoreLines());
        }
    }

    @Test
    void deveReconhecerTodosOsComandosAritmeticosELogicos() throws IOException {
        List<String> commands = List.of("add", "sub", "neg", "eq", "gt", "lt", "and", "or", "not");
        String vmCode = String.join("\n", commands);

        try (Parser parser = new Parser(new StringReader(vmCode))) {
            for (String expected : commands) {
                assertTrue(parser.hasMoreLines());
                parser.advance();
                assertEquals(CommandType.C_ARITHMETIC, parser.commandType());
                assertEquals(expected, parser.arg1());
                assertThrows(IllegalStateException.class, parser::arg2);
            }
            assertFalse(parser.hasMoreLines());
        }
    }

    @Test
    void deveProcessarSequenciaCompletaComLixoNoFinal() throws IOException {
        String vmCode = """
            // Inicia teste
            push local 0
            pop argument 2 // salva no arg 2
            
            add
            // Linhas vazias e comentários após a última instrução válida
            
            // Fim
            """;

        try (Parser parser = new Parser(new StringReader(vmCode))) {
            // Instrução 1: push local 0
            assertTrue(parser.hasMoreLines());
            parser.advance();
            assertEquals(CommandType.C_PUSH, parser.commandType());
            assertEquals("local", parser.arg1());
            assertEquals(0, parser.arg2());

            // Instrução 2: pop argument 2
            assertTrue(parser.hasMoreLines());
            parser.advance();
            assertEquals(CommandType.C_POP, parser.commandType());
            assertEquals("argument", parser.arg1());
            assertEquals(2, parser.arg2());

            // Instrução 3: add
            assertTrue(parser.hasMoreLines());
            parser.advance();
            assertEquals(CommandType.C_ARITHMETIC, parser.commandType());
            assertEquals("add", parser.arg1());

            // Não deve tentar ler os comentários finais como próxima instrução
            assertFalse(parser.hasMoreLines());
        }
    }

    @Test
    void deveReconhecerComandosDeFluxoDePrograma() throws IOException {
        String vmCode = """
            label LOOP_START
            goto END   // pula
            if-goto LOOP_START
            """;

        try (Parser parser = new Parser(new StringReader(vmCode))) {
            parser.advance();
            assertEquals(CommandType.C_LABEL, parser.commandType());
            assertEquals("LOOP_START", parser.arg1());
            assertThrows(IllegalStateException.class, parser::arg2);

            parser.advance();
            assertEquals(CommandType.C_GOTO, parser.commandType());
            assertEquals("END", parser.arg1());

            parser.advance();
            assertEquals(CommandType.C_IF, parser.commandType());
            assertEquals("LOOP_START", parser.arg1());
            assertFalse(parser.hasMoreLines());
        }
    }

    @Test
    void deveReconhecerComandosDeFuncao() throws IOException {
        String vmCode = """
            function SimpleFunction.test 2
            call Math.multiply 2
            return
            """;

        try (Parser parser = new Parser(new StringReader(vmCode))) {
            parser.advance();
            assertEquals(CommandType.C_FUNCTION, parser.commandType());
            assertEquals("SimpleFunction.test", parser.arg1());
            assertEquals(2, parser.arg2());

            parser.advance();
            assertEquals(CommandType.C_CALL, parser.commandType());
            assertEquals("Math.multiply", parser.arg1());
            assertEquals(2, parser.arg2());

            parser.advance();
            assertEquals(CommandType.C_RETURN, parser.commandType());
            assertThrows(IllegalStateException.class, parser::arg1);
            assertThrows(IllegalStateException.class, parser::arg2);
            assertFalse(parser.hasMoreLines());
        }
    }

    @Test
    void deveRejeitarComandoDesconhecido() throws IOException {
        try (Parser parser = new Parser(new StringReader("jump X"))) {
            parser.advance();
            assertThrows(IllegalArgumentException.class, parser::commandType);
        }
    }

    // --- Validação com os arquivos oficiais do Project 08 ---

    private static final Path PROJECT8 = Path.of("test/validation/project8");

    // Lê o arquivo inteiro e descreve cada comando como "TIPO arg1 arg2" (só os args que o tipo possui)
    private static List<String> parseAll(Path vm) throws IOException {
        List<String> out = new ArrayList<>();
        try (Parser parser = new Parser(vm.toFile())) {
            while (parser.hasMoreLines()) {
                parser.advance();
                CommandType type = parser.commandType();
                String line = type.name();
                if (type != CommandType.C_RETURN) line += " " + parser.arg1();
                if (Set.of(CommandType.C_PUSH, CommandType.C_POP, CommandType.C_FUNCTION, CommandType.C_CALL).contains(type)) {
                    line += " " + parser.arg2();
                }
                out.add(line);
            }
        }
        return out;
    }

    @Test
    void deveLerTodosOsArquivosOficiaisDoProject08() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(PROJECT8)) {
            files = walk.filter(f -> f.toString().endsWith(".vm")).toList();
        }
        assertEquals(9, files.size(), "arquivos .vm esperados em test/validation/project8");

        Set<CommandType> seen = EnumSet.noneOf(CommandType.class);
        for (Path vm : files) {
            for (String cmd : parseAll(vm)) { // falha aqui se algum comando não for reconhecido
                seen.add(CommandType.valueOf(cmd.split(" ")[0]));
            }
        }
        assertEquals(EnumSet.allOf(CommandType.class), seen, "todos os tipos de comando devem aparecer");
    }

    @Test
    void deveExtrairArgumentosDoSimpleFunction() throws IOException {
        assertEquals(List.of(
            "C_FUNCTION SimpleFunction.test 2",
            "C_PUSH local 0",
            "C_PUSH local 1",
            "C_ARITHMETIC add",
            "C_ARITHMETIC not",
            "C_PUSH argument 0",
            "C_ARITHMETIC add",
            "C_PUSH argument 1",
            "C_ARITHMETIC sub",
            "C_RETURN"
        ), parseAll(PROJECT8.resolve("SimpleFunction/SimpleFunction.vm")));
    }

    @Test
    void deveExtrairArgumentosDoBasicLoop() throws IOException {
        List<String> cmds = parseAll(PROJECT8.resolve("BasicLoop/BasicLoop.vm"));
        assertTrue(cmds.contains("C_LABEL LOOP"));
        assertTrue(cmds.contains("C_IF LOOP"));
    }

    @Test
    void deveExtrairArgumentosDeFuncoesECallsDoStaticsTest() throws IOException {
        List<String> cmds = parseAll(PROJECT8.resolve("StaticsTest/Sys.vm"));
        assertEquals("C_FUNCTION Sys.init 0", cmds.get(0));
        assertTrue(cmds.contains("C_CALL Class1.set 2"));
        assertTrue(cmds.contains("C_CALL Class2.get 0"));
        assertTrue(cmds.contains("C_GOTO END"));
    }
}
