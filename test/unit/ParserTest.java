package unit;
import parser.Parser;
import parser.CommandType;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

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
}