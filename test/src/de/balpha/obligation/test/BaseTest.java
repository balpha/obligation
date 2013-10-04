package de.balpha.obligation.test;

import android.test.ActivityInstrumentationTestCase2;
import de.balpha.obligation.testapp.TestActivity;

public abstract class BaseTest extends ActivityInstrumentationTestCase2<TestActivity> {
    public BaseTest() {
        super(TestActivity.class);
    }
    public void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
