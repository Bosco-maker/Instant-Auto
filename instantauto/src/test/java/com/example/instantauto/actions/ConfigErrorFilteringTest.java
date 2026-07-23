package com.example.instantauto.actions;

import com.example.instantauto.configs.MetaFieldRegistry;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class ConfigErrorFilteringTest {

    @Before
    public void setup() {
        MetaFieldRegistry.clear();
        UserActionRegistry.clear();
        MetaFieldRegistry.registerField("Starting", String.class, "");
        MetaFieldRegistry.registerField("auto_var", String.class, "");
    }

    @Test
    public void testUnknownFormatErrorsInConfigLogs() throws IOException {
        File tempAuto = File.createTempFile("[ACTIVE]TestAuto", ".txt");
        try (FileWriter writer = new FileWriter(tempAuto)) {
            writer.write("Starting=TEST\n");
            writer.write("auto_var=VAL\n");
            writer.write("ACTION1()\n");
            writer.write("invalid line\n");
        }

        File tempGeneral = File.createTempFile("General", ".txt");
        File tempMeta = File.createTempFile("Meta", ".txt");

        AutoParser parser = new AutoParser(tempGeneral.getAbsolutePath(), tempMeta.getAbsolutePath());
        parser.parse(tempAuto);

        List<String> configLogs = parser.getConfigLogs();
        System.out.println("Config Logs: " + configLogs);

        // Should NOT contain "Unknown format" errors for action lines
        assertFalse("Should NOT have error for ACTION1()", configLogs.stream().anyMatch(log -> log.contains("Unknown format") && log.contains("ACTION1()")));
        assertFalse("Should NOT have error for invalid line", configLogs.stream().anyMatch(log -> log.contains("Unknown format") && log.contains("invalid line")));

        // Verify configs were still read
        assertEquals("TEST", MetaFieldRegistry.getEntry("Starting").value);
        assertEquals("VAL", MetaFieldRegistry.getEntry("auto_var").value);

        // Verify action errors still capture non-config garbage
        List<String> actionErrors = parser.getActionErrors();
        System.out.println("Action Errors: " + actionErrors);
        assertTrue("Should have action error for invalid line", actionErrors.stream().anyMatch(e -> e.contains("Unknown Action -> invalid line")));
        
        tempAuto.delete();
        tempGeneral.delete();
        tempMeta.delete();
    }
}
