import codewrite.CodeWriter;
import parser.Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 1 || !args[0].endsWith(".vm")) {
            System.err.println("Uso: Main <arquivo.vm>");
            System.exit(1);
        }
        File vmFile = new File(args[0]);
        String baseName = vmFile.getName().replaceAll("\\.vm$", "");
        File asmFile = new File(vmFile.getParentFile(), baseName + ".asm");

        try (Parser parser = new Parser(vmFile);
             CodeWriter writer = new CodeWriter(new FileWriter(asmFile, StandardCharsets.UTF_8))) {
            writer.setFileName(baseName);
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
        System.out.println("Gerado: " + asmFile.getPath());
    }
}
