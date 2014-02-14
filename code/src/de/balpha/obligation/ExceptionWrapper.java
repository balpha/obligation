package de.balpha.obligation;

import android.os.Looper;

public class ExceptionWrapper {

    public final boolean causedSuspension;
    public final Throwable exception;

    private final Job mJob;
    private final Instruction mInstruction;
    /* package */ boolean mHandled = false;
    private boolean mExpectingRetry = false;

    boolean resultProvided = false;
    Object result = null;

    /* package */ ExceptionWrapper(Throwable exception, boolean causedSuspension, Job job, Instruction instruction) {
        mJob = job;
        mInstruction = instruction;
        this.exception = exception;
        this.causedSuspension = causedSuspension;
    }

    private void handled() {
        if (mHandled)
            throw new RuntimeException("Exception handled more than once");
        mHandled = true;
    }

    public void expectRetry() {
        expectRetry(false);
    }

    public void expectRetry(boolean resumeOthers) {
        handled();
        mExpectingRetry = true;
        mJob.suspendInstruction(mInstruction);
        if (resumeOthers)
            mJob.resumeFrom(this, false);
    }

    public void retryAll() {
        mJob.resumeFromAll();
    }

    public void retry() {
        retry(true);
    }
    void retry(boolean callResume) {
        if (!mExpectingRetry)
            throw new RuntimeException("unexpected call to retry()");
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new RuntimeException("retry must be called from the UI thread");
        mExpectingRetry = false;
        if (callResume) {
            mJob.resumeInstruction(mInstruction);
            mJob.resumeFrom(this, true);
        }
    }

    public void useResult(Object data) {
        handled();
        if (!mJob.checkType(mInstruction.result, data))
            throw new RuntimeException("data provided to useResult is of wrong type; expected " + mInstruction.method.getReturnType().getName() + " but got " + data.getClass().getName());
        resultProvided = true;
        result = data;
        mJob.resumeFrom(this, false);
    }

}
