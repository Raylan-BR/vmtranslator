import codewrite.CodeWriter;
import parser.Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Uso: Main <arquivo.vm | diretório>");
            System.exit(1);
        }
        File input = new File(args[0]);
        if (!input.exists()) {
            System.err.println("Caminho não encontrado: " + input.getPath());
            System.exit(1);
        }

        File[] vmFiles;
        File asmFile;
        if (input.isDirectory()) {
            vmFiles = input.listFiles((dir, name) -> name.endsWith(".vm"));
            if (vmFiles == null || vmFiles.length == 0) {
                System.err.println("Nenhum arquivo .vm em: " + input.getPath());
                System.exit(1);
            }
            Arrays.sort(vmFiles); // ordem determinística, independente do sistema de arquivos
            // Convenção do nand2tetris: Dir/ -> Dir/Dir.asm
            asmFile = new File(input, input.getAbsoluteFile().getName() + ".asm");
        } else {
            if (!input.getName().endsWith(".vm")) {
                System.err.println("O arquivo precisa ter extensão .vm: " + input.getPath());
                System.exit(1);
            }
            vmFiles = new File[]{input};
            asmFile = new File(input.getAbsoluteFile().getParentFile(), baseName(input) + ".asm");
        }

        // Sys.vm presente => programa completo, precisa do bootstrap (writeInit, ainda não implementado).
        boolean needsBootstrap = Arrays.stream(vmFiles).anyMatch(f -> f.getName().equals("Sys.vm"));

        try (CodeWriter writer = new CodeWriter(new FileWriter(asmFile, StandardCharsets.UTF_8))) {
            for (File vmFile : vmFiles) {
                writer.setFileName(baseName(vmFile)); // static vira <Arquivo>.<i>, separado por arquivo
                translate(vmFile, writer);
            }
        }
        System.out.println("Gerado: " + asmFile.getPath() + (needsBootstrap ? " (Sys.vm encontrado: requer bootstrap)" : ""));
    }

    private static void translate(File vmFile, CodeWriter writer) throws IOException {
        try (Parser parser = new Parser(vmFile)) {
            while (parser.hasMoreLines()) {
                parser.advance();
                switch (parser.commandType()) {
                    case C_ARITHMETIC -> writer.writeArithmetic(parser.arg1());
                    case C_PUSH -> writer.writePush(parser.arg1(), parser.arg2());
                    case C_POP -> writer.writePop(parser.arg1(), parser.arg2());
                    case C_LABEL -> writer.writeLabel(parser.arg1());
                    case C_GOTO -> writer.writeGoto(parser.arg1());
                    case C_IF -> writer.writeIf(parser.arg1());
                }
            }
        }
    }

    private static String baseName(File vmFile) {
        return vmFile.getName().replaceAll("\\.vm$", "");
    }
}
