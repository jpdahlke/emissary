package emissary.core.sentinel.protocols.rules;

import emissary.core.sentinel.protocols.Protocol;
import emissary.pool.AgentPool;
import emissary.test.core.junit5.UnitTest;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnyMaxTimeTest extends UnitTest {

    Collection<Protocol.PlaceAgentStats> placeAgentStats;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Protocol.PlaceAgentStats stats = new Protocol.PlaceAgentStats("TestPlace");
        placeAgentStats = List.of(stats);
        for (int i = 1; i < 6; ++i) {
            stats.update(i);
        }
    }

    @Test
    void constructorBlankPlace() {
        assertThrows(IllegalArgumentException.class, () -> new AnyMaxTime("rule1", "", "3L", "1.0"));
    }

    @Test
    void constructorInvalidTimeLimit() {
        assertThrows(NumberFormatException.class, () -> new AnyMaxTime("rule1", "TestPlace", "L", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> new AnyMaxTime("rule1", "TestPlace", "0L", "1.0"));
    }

    @Test
    void constructorInvalidThreshold() {
        assertThrows(NumberFormatException.class, () -> new AnyMaxTime("rule1", "TestPlace", "3L", "."));
        assertThrows(IllegalArgumentException.class, () -> new AnyMaxTime("rule1", "TestPlace", "3L", "0.0"));
    }

    @Test
    void overTimeLimit() {
        Rule rule = new AnyMaxTime("rule1", "TestPlace", 5, 0.75);
        assertTrue(rule.overTimeLimit(placeAgentStats));
    }

    @Test
    void notOverTimeLimit() {
        Rule rule = new AnyMaxTime("rule1", "TestPlace", 6, 0.75);
        assertFalse(rule.overTimeLimit(placeAgentStats));
    }

    @Nested
    class ConditionTest extends UnitTest {

        List<Protocol.PlaceAgentStats> placeStats = new ArrayList<>();
        AgentPool pool = mock(AgentPool.class);

        @Test
        void condition() {
            Protocol.PlaceAgentStats lowerStats = new Protocol.PlaceAgentStats("ToLowerPlace");
            lowerStats.update(1);
            lowerStats.update(3);
            lowerStats.update(4);

            Protocol.PlaceAgentStats upperStats = new Protocol.PlaceAgentStats("ToUpperPlace");
            upperStats.update(2);
            upperStats.update(5);

            CollectionUtils.addAll(placeStats, lowerStats);
            CollectionUtils.addAll(placeStats, upperStats);

            try (MockedStatic<AgentPool> agentPool = Mockito.mockStatic(AgentPool.class)) {
                agentPool.when(AgentPool::lookup).thenReturn(pool);

                assertTrue(checkCondition("To(?:Lower|Upper)Place", 5, 1.0, 5), "One agent should be over max time");
                assertFalse(checkCondition("To(?:Lower|Upper)Place", 5, 1.0, 6), "Not all agents can be over max time; pool too large");

                assertFalse(checkCondition("To(?:Lower|Upper)Place", 6, 1.0, 5), "No agents are over max time");
                assertFalse(checkCondition("To(?:Lower|Upper)Place", 6, 1.0, 6), "No agents are over max time; pool too large");

                assertTrue(checkCondition("ToLowerPlace", 4, .5, 5));

                assertFalse(checkCondition("ToLowerPlace", 5, .5, 5));

                assertFalse(checkCondition("ToLowerPlace", 4, 0.75, 5));
            }
        }

        private boolean checkCondition(String pattern, long timeLimit, double threshold, int poolSize) {
            Rule rule = new AnyMaxTime("TestRule", pattern, timeLimit, threshold);
            when(pool.getCurrentPoolSize()).thenReturn(poolSize);
            return rule.condition(placeStats);
        }
    }
}
