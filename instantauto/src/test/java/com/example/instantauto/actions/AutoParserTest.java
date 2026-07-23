package com.example.instantauto.actions;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutoParserTest {

    @Test
    public void testStripComments() {
        assertEquals("GO.TO.POSE2D(0,0,0)", AutoParser.stripComments("GO.TO.POSE2D(0,0,0)// This is a comment").trim());
        assertEquals("GO.TO.POSE2D(0,0,0)", AutoParser.stripComments("GO.TO.POSE2D(0,0,0)# This is a hash comment").trim());
        assertEquals("", AutoParser.stripComments("// Full line comment").trim());
        assertEquals("key=value", AutoParser.stripComments("key=value // comment").trim());
    }
}
