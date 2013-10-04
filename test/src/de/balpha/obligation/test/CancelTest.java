package de.balpha.obligation.test;

import de.balpha.obligation.Async;
import de.balpha.obligation.Needs;
import de.balpha.obligation.Obligation;
import de.balpha.obligation.Provides;

public class CancelTest extends BaseTest {
    public class CancelObligation extends Obligation {
        int s = 0;

        @Provides(1)
        @Async
        private int a() {
            sleep(200);
            return 42;
        }

        @Provides(2)
        @Async
        private int b() {
            sleep(400);
            return 66;
        }

        @Needs({1, 2})
        private void c() {
            assertFalse("should never have been called", true);
        }

        @Needs(1)
        private void d() {
            s = 1;
        }
    }

    public void testCancel() {
        CancelObligation o = new CancelObligation();
        o.fulfill();
        sleep(300);
        o.cancel();
        sleep(400);
        assertEquals(o.s, 1);
    }
}
