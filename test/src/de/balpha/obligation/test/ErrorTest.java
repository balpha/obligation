package de.balpha.obligation.test;

import android.test.ActivityInstrumentationTestCase2;
import de.balpha.obligation.Needs;
import de.balpha.obligation.Obligation;
import de.balpha.obligation.Provides;
import de.balpha.obligation.testapp.TestActivity;

public class ErrorTest extends ActivityInstrumentationTestCase2<TestActivity> {

    private static class NotProvided extends Obligation {
        @Needs(1)
        private void foo(String bar) { }
    }

    private static class Dupe extends Obligation {
        @Provides(1)
        private String foo() { return ""; }

        @Provides(1)
        private int bar() { return 1; }
    }

    private static class TypeMismatch extends Obligation {
        @Needs(1)
        private void foo(int bar) { }

        @Provides(1)
        private String foo() { return ""; }
    }

    private static class ParamCountMismatch extends Obligation {
        @Needs({1, 2})
        private void foo(String s) { }

        @Provides(1)
        private String foo() { return ""; }

        @Provides(2)
        private String bar() { return ""; }
    }

    private static class Circular1 extends Obligation {
        @Needs(1)
        @Provides(2)
        private int foo(int x) { return x; }

        @Needs(2)
        @Provides(1)
        private int bar(int x) { return x; }
    }

    private static class Circular2 extends Obligation {
        @Needs(1)
        @Provides(1)
        private int foo(int x) { return x; }
    }

    public ErrorTest() {
        super(TestActivity.class);
    }

    public void testErrors() {
        assertErrorContains(NotProvided.class, "isn't provided");
        assertErrorContains(Dupe.class, "multiple Obligation methods provide");
        assertErrorContains(TypeMismatch.class, "but needs object id 1 which is");
        assertErrorContains(ParamCountMismatch.class, "parameter count doesn't match @Needs() arguments");
        assertErrorContains(Circular1.class, "circular dependencies");
        assertErrorContains(Circular2.class, "circular dependencies");
    }

    private static void assertErrorContains(Class<? extends Obligation> cls, String error) {
        String e = Obligation.checkObligation(cls);
        assertNotNull("expected " + cls.getName() + " to not verify", e);
        assertTrue("expected " + cls.getName() + " verification error to contain \"" + error + "\" but got \"" + e + "\"", e.contains(error));
    }
}
