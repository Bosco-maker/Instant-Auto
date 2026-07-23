package com.example.instantauto.integration;

import com.example.instantauto.actions.Action;
import com.example.instantauto.actions.AutoParser;
import com.example.instantauto.actions.UserActionRegistry;
import com.example.instantauto.actions.MiniAction;
import com.example.instantauto.configs.MetaFieldRegistry;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class IntegrationTest {

    private final List<String> executionLogs = new ArrayList<>();

    @Before
    public void setup() {
        MetaFieldRegistry.clear();
        UserActionRegistry.clear();
        executionLogs.clear();
        MetaFieldRegistry.registerField("maxPower", Double.class, 0.0);
        MetaFieldRegistry.registerField("is_blue", Boolean.class, false);
        MetaFieldRegistry.registerField("Starting", String.class, "");
        MetaFieldRegistry.registerField("Title", String.class, "");

        UserActionRegistry.register(new MiniAction("PRINT", obj -> () -> {
            executionLogs.add(String.valueOf(obj));
            return false;
        }));
    }

    @Test
    public void testFullParsingAndExecution() {
        String resourcesPath = "src/test/resources/textfiles/";
        String generalSettings = resourcesPath + "GeneralRobotSettings";
        String metaActionSettings = resourcesPath + "MetaActionSettings";
        String autoFilePath = resourcesPath + "[ACTIVE]IntegrationTestAuto";

        AutoParser parser = new AutoParser(generalSettings, metaActionSettings);
        parser.parse(new File(autoFilePath));

        List<Action> actions = parser.getActions();
        assertFalse("Should have parsed actions", actions.isEmpty());

        for (Action action : actions) {
            action.run();
        }

        // Verify logs
        assertTrue("Log should contain RED BRANCH 2. Logs: " + executionLogs, executionLogs.stream().anyMatch(s -> s.contains("RED BRANCH 2")));
    }
}
