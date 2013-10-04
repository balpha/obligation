package de.balpha.obligation.test;

import android.test.ActivityInstrumentationTestCase2;
import de.balpha.obligation.Async;
import de.balpha.obligation.Needs;
import de.balpha.obligation.Obligation;
import de.balpha.obligation.Provides;
import de.balpha.obligation.testapp.TestActivity;

public class AsyncTest extends ActivityInstrumentationTestCase2<TestActivity> {
    public AsyncTest() {
        super(TestActivity.class);
    }
    private Object waiter = new Object();
    public class AsyncObligation extends Obligation {
        private static final int FOO = 1;
        public boolean isDone = false;
        public int test = 0;
        private boolean onCompleteCalled = false;

        @Needs(FOO)
        private void done(String s) {
            assertFalse(onCompleteCalled);
            assertEquals(s, "Hello");
            isDone = true;
        }

        @Provides(FOO)
        @Async
        public String getstring() {
            assertEquals(test, 0);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertEquals(test, 1);
            test = 2;
            return "Hello";
        }

        @Override
        protected void onComplete() {
            assertTrue(isDone);
            onCompleteCalled = true;
        }
    }

    public void testAsync() throws Throwable {
        final AsyncObligation ao = new AsyncObligation();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                ao.fulfill();
            }
        });
        Thread.sleep(100);
        ao.test = 1;
        Thread.sleep(200);
        assertEquals(ao.test, 2);
        Thread.sleep(200);
        assertTrue(ao.isDone);
        assertTrue(ao.onCompleteCalled);
    }
}
