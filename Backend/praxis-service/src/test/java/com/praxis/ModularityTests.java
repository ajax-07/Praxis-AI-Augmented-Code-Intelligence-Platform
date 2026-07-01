package com.praxis;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

public class ModularityTests {
    @Test
    void modulesAreClean() {
        ApplicationModules
                .of(PraxisServiceApplication.class).verify();
    }
}
