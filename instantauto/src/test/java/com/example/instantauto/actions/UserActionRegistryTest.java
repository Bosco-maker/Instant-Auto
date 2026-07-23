package com.example.instantauto.actions;

import com.example.instantauto.configs.MetaFieldRegistry;
import org.junit.Before;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class UserActionRegistryTest {

    @Before
    public void setup() {
        UserActionRegistry.clear();
        MetaFieldRegistry.clear();
        UserActionRegistry.register(new MiniAction("PRINT", obj -> new Action() {
            @Override
            public boolean run() {
                System.out.println(obj);
                return false;
            }
        }));
    }

    @Test
    public void testSplitByTopLevelCommas() {
        String input = "ACTION1, ACTION2(param1, param2), ACTION3{SUB1, SUB2}";
        List<String> result = UserActionRegistry.splitByTopLevelCommas(input);
        assertEquals(3, result.size());
        assertEquals("ACTION1", result.get(0).trim());
        assertEquals("ACTION2(param1, param2)", result.get(1).trim());
        assertEquals("ACTION3{SUB1, SUB2}", result.get(2).trim());
    }

    @Test
    public void testEvaluateCondition() {
        MetaFieldRegistry.registerField("isBlue", Boolean.class, true);
        MetaFieldRegistry.registerField("isRed", Boolean.class, false);

        assertTrue(UserActionRegistry.evaluateCondition("isBlue"));
        assertFalse(UserActionRegistry.evaluateCondition("isRed"));
        assertFalse(UserActionRegistry.evaluateCondition("unknownVar"));
    }

    @Test
    public void testCreateActionWithVariable() {
        MetaFieldRegistry.registerField("myVar", String.class, "Hello");
        Action action = UserActionRegistry.createAction("PRINT(myVar)");
        assertNotNull(action);
        // We can't easily verify the print output without redirecting System.out, 
        // but we verify it's created successfully.
    }
}
