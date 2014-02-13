package de.balpha.obligation;

import java.lang.reflect.Method;

/* package */ class Instruction {
    int result;
    int[] needed;
    int parameterCount; // the first this much of needed are actually parameters of the method
    Method method;
    boolean async;
    boolean goal;

    public boolean isProvider() {
        return result >= 0;
    }
}
