package org.dreamhorizon.pulsealertscron;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic test for MainApplication
 */
class MainApplicationTest {

    @Test
    void testMainApplicationInstantiation() {
        MainApplication app = new MainApplication();
        assertNotNull(app, "MainApplication should be instantiated");
    }
}

