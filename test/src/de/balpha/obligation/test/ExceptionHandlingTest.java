package de.balpha.obligation.test;

import android.os.Handler;
import de.balpha.obligation.*;

import java.util.ArrayList;

public class ExceptionHandlingTest extends BaseTest {

    public class ExceptionObligation extends Obligation
    {
        public int result7 = 0;
        public int result11 = 0;
        public int result13 = 0;
        public int result9 = 0;

        public int onExceptionCallCount = 0;

        @Provides(7)
        public int failing() {
            throw new RuntimeException();
        }

        @Provides(11)
        @Async
        public int failingAsync() {
            throw new RuntimeException();
        }


        boolean throw13 = true;
        @Provides(13)
        public int failingWillSuspend() {
            if (throw13) {
                throw13 = false;
                throw new RuntimeException();
            }
           return 100;
        }

        boolean throw9 = true;
        @Provides(9)
        @Async
        public int failingAsyncWillSuspend() {
            if (throw9) {
                throw9 = false;
                throw new RuntimeException();
            }
            return -1;
        }

        @Goal
        @Needs({7, 11, 13, 9})
        public void done(int r7, int r11, int r13, int r9) {
            result7 = r7;
            result11 = r11;
            result13 = r13;
            result9 = r9;
        }

        ArrayList<Integer> I = new ArrayList<Integer>();
        ArrayList<ExceptionWrapper> E = new ArrayList<ExceptionWrapper>();

        @Override
        public void onException(final ExceptionWrapper problem, int resultId) {
            I.add(resultId);
            E.add(problem);
            onExceptionCallCount++;
            switch (resultId) {
                case 7: problem.useResult(42); break;
                case 11: problem.useResult(666); break;
                case 13:
                case 9: {
                    problem.expectRetry();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            problem.retry();
                        }
                    }, 100);
                    break;
                }
                default: assertFalse("bad result id ", true);
            }

        }
    }

    public class ExceptionObligation2 extends Obligation {

        private final boolean mResumeOthers;

        public ExceptionObligation2(boolean resumeOthers) {
            mResumeOthers = resumeOthers;
        }

        boolean throw1 = true;
        @Provides(1)
        @Async
        public int get1() {
            if (throw1) {
                throw1 = false;
                throw new RuntimeException();
            }
            return 11;
        }

        @Provides(2)
        @Async
        public int get2() {
            sleep(100);
            return 12;
        }
        boolean threeCalled = false;
        @Provides(3)
        @Needs(2)
        public int get3(int r2) {
            threeCalled = true;
            return 13;
        }
        boolean complete = false;
        @Goal
        @Needs({1, 3})
        public void done() {
            complete = true;
        }

        @Override
        public void onException(final ExceptionWrapper problem, int id) {
            problem.expectRetry(mResumeOthers);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    problem.retry();
                }
            }, 200);
        }
    }

    Handler handler;
    public void testExceptions() throws Throwable{
        final ExceptionObligation ob = new ExceptionObligation();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                handler = new Handler();
                ob.fulfill();
            }
        });
        Thread.sleep(200);
        assertEquals(42, ob.result7);
        assertEquals(666, ob.result11);
        assertEquals(100, ob.result13);
        assertEquals(-1, ob.result9);
        assertEquals(4, ob.onExceptionCallCount);
    }

    public void testResumeOthers() throws Throwable {
        final ExceptionObligation2 oTrue = new ExceptionObligation2(true);
        final ExceptionObligation2 oFalse = new ExceptionObligation2(false);

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                handler = new Handler();
                oTrue.fulfill();
                oFalse.fulfill();
            }
        });

        sleep(150);
        assertTrue(oTrue.threeCalled);
        assertFalse(oFalse.threeCalled);
        assertFalse(oTrue.complete);
        assertFalse(oFalse.complete);
        sleep(100);
        assertTrue(oTrue.threeCalled);
        assertTrue(oFalse.threeCalled);
        assertTrue(oTrue.complete);
        assertTrue(oFalse.complete);
    }
}
