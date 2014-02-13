package de.balpha.obligation.test;

import de.balpha.obligation.Goal;
import de.balpha.obligation.Needs;
import de.balpha.obligation.Obligation;
import de.balpha.obligation.Provides;

public class ExternalDataText extends BaseTest {
    public class ExternalDataObligation extends Obligation {
        int r17;
        int r111;

        @Needs(17)
        @Goal
        public void foo(int i) {
            r17 = i;
        }

        @Needs(111)
        @Goal
        public void bar(int i) {
            r111 = i;
        }

        @Provides(17)
        public int getFoo() {
            assertFalse("should never have been called", true);
            return 0;
        }

        @Provides(111)
        public int getBar() {
            return 42;
        }

        public void setFoo(Object val) {
            setResult(17, val);
        }
    }

    private static class ShouldHaveThrownException extends RuntimeException {}

    @android.test.UiThreadTest
    public void testExternalData() {
        ExternalDataObligation o = new ExternalDataObligation();
        try {
            o.setFoo("Hello");
            throw new ShouldHaveThrownException();
        } catch (RuntimeException e) {
            if (e instanceof ShouldHaveThrownException || !e.getMessage().contains("setResult given wrong type"))
                throw e;
        }
        o.setFoo(123);
        o.fulfill();
        assertEquals(o.r17, 123);
        assertEquals(o.r111, 42);
        try {
            o.setFoo(999);
            throw new ShouldHaveThrownException();
        } catch (RuntimeException e) {
            if (e instanceof ShouldHaveThrownException || !e.getMessage().contains("after fulfillment has started"))
                throw e;
        }
    }
}
