package de.balpha.obligation.test;

import de.balpha.obligation.*;

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
        @Goal
        private void c() {
            assertFalse("should never have been called", true);
        }

        @Needs(1)
        @Goal
        private void d() {
            s = 1;
        }
    }

    public void testCancel() throws Throwable {
        final CancelObligation o = new CancelObligation();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                o.fulfill();
            }
        });
        sleep(300);
        o.cancel();
        sleep(400);
        assertEquals(o.s, 1);
    }
}
