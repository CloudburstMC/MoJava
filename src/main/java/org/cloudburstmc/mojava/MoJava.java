package org.cloudburstmc.mojava;

import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.tokenizer.TokenIterator;
import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.cloudburstmc.mojava.utils.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MoJava {

    public static List<Expression> parse(String code) {
        return (createParser(code)).parse();
    }

    public static List<Expression> parse(Path path) throws IOException {
        return (createParser(path)).parse();
    }

    public static List<Expression> parse(InputStream stream) throws IOException {
        return (createParser(stream)).parse();
    }

    public static MoParser createParser(String code) {
        return new MoParser(new TokenIterator(code), code);
    }

    public static MoParser createParser(Path path) throws IOException {
        byte[] fileBytes = Files.readAllBytes(path);

        String code = new String(fileBytes, StandardCharsets.UTF_8);

        return createParser(code);
    }

    public static MoParser createParser(InputStream stream) throws IOException {
        String code = FileUtils.readFile(stream);
        return createParser(code);
    }

    public static MoRuntime createRuntime() {
        return new MoRuntime();
    }
}
