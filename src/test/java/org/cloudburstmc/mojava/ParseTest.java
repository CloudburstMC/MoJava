package org.cloudburstmc.mojava;

import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@DisplayName("Parse Tests")
public class ParseTest {

    @Test
    @DisplayName("Parse File 1")
    public void parse1() throws IOException {
        Assertions.assertDoesNotThrow(() -> MoJava.parse(getClass().getClassLoader().getResourceAsStream("expr1.txt")));
    }

    @Test
    @DisplayName("Parse File ")
    public void parse2() throws IOException {
        Assertions.assertDoesNotThrow(() -> MoJava.parse(getClass().getClassLoader().getResourceAsStream("expr2.txt")));
    }

    @Test
    @DisplayName("Parse File 3")
    public void parse3() throws IOException {
        Assertions.assertDoesNotThrow(() -> MoJava.parse(getClass().getClassLoader().getResourceAsStream("expr3.txt")));
    }

    @Test
    @DisplayName("Parse File 4")
    public void parse4() throws IOException {
        Assertions.assertDoesNotThrow(() -> MoJava.parse(getClass().getClassLoader().getResourceAsStream("expr4.txt")));
    }

    @Test
    public void parse5() {
        MoRuntime runtime = new MoRuntime();
        runtime.execute(MoJava.parse("temp.vari[0] = 1"));
    }
}
