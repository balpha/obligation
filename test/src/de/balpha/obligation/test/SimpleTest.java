package de.balpha.obligation.test;

import android.test.ActivityInstrumentationTestCase2;
import de.balpha.obligation.Goal;
import de.balpha.obligation.Needs;
import de.balpha.obligation.Obligation;
import de.balpha.obligation.Provides;
import de.balpha.obligation.testapp.TestActivity;

public class SimpleTest extends ActivityInstrumentationTestCase2<TestActivity> {
    public SimpleTest() {
        super(TestActivity.class);
    }
    private static class SimpleObligation extends Obligation {
        private static final int START = 1;
        private static final int TWICE = 2;
        private static final int AND_THEN_SOME = 3;

        private final int mStart;
        public int result;

        private SimpleObligation(int start) {
            mStart = start;
        }

        @Needs(AND_THEN_SOME)
        @Goal
        private void done(int input) {
            result = input;
        }

        @Needs(START)
        @Provides(TWICE)
        private int makeDouble(int input) {
            return 2 * input;
        }

        @Needs(TWICE)
        @Provides(AND_THEN_SOME)
        private int add(int input) {
            return input + 4;
        }

        @Provides(START)
        private int startValue() {
            return mStart;
        }
    }

    @android.test.UiThreadTest
    public void testSimple() {
        SimpleObligation so = new SimpleObligation(3);
        so.fulfill();
        assertEquals(so.result, 10);

    }
}
