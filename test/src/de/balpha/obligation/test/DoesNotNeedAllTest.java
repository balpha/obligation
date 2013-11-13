package de.balpha.obligation.test;


import de.balpha.obligation.Goal;
import de.balpha.obligation.Needs;
import de.balpha.obligation.Obligation;
import de.balpha.obligation.Provides;

public class DoesNotNeedAllTest extends BaseTest {

    public class DoesNotNeedAllObligation extends Obligation {
        public String mS;
        @Provides(1)
        private int a() {
            return 42;
        }

        @Provides(2)
        private String b() {
            return "Hello";
        }

        @Needs({2, 1})
        @Goal
        private void c(String s) {
            mS = s;
        }
    }

    public void testDoesNotNeedAll() {
        DoesNotNeedAllObligation o = new DoesNotNeedAllObligation();
        o.fulfill();
        assertEquals(o.mS, "Hello");
    }
}
