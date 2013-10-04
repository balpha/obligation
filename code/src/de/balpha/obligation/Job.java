package de.balpha.obligation;

import android.os.AsyncTask;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* package */ class Job {

    private static ExecutorService sThreadPool = Executors.newFixedThreadPool(4);

    InstructionSet mInstructionSet;
    Obligation mObligation;
    Object[] mResults;
    boolean[] mHaveResults;
    int mRunningAsyncCount = 0;

    Queue<Instruction> mReadyToRun;
    LinkedList<Instruction> mDependent;

    Job(InstructionSet instructionSet, Obligation obligation) {
        mInstructionSet = instructionSet;
        mObligation = obligation;

        mResults = new Object[instructionSet.providers.length];
        mHaveResults = new boolean[instructionSet.providers.length];

        mDependent = new LinkedList<Instruction>(Arrays.asList(mInstructionSet.needers));
        mReadyToRun = new LinkedList<Instruction>();
        for (Instruction inst : mInstructionSet.providers)
            if (inst.needed.length == 0)
                mReadyToRun.add(inst);
    }

    private Object executeInstruction(Instruction inst, boolean forceSync) {
        Object[] args = new Object[inst.parameterCount];
        for (int j = 0; j < inst.parameterCount; j++) {
            args[j] = mResults[inst.needed[j]];
        }
        if (!forceSync && inst.async) {
            mRunningAsyncCount++;
            new AsyncRun(inst).executeOnExecutor(sThreadPool);
            return null;
        } else {
            Object result;
            try {
                result = inst.method.invoke(mObligation, args);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException)
                    throw (RuntimeException)cause;
                else
                    throw new RuntimeException(cause);
            }
            return result;
        }
    }

    void go() {
        while (mReadyToRun.size() > 0) {
            Instruction inst = mReadyToRun.remove();
            Object result = executeInstruction(inst, false);
            if (!inst.async && inst.result >= 0)
                setResult(inst.result, result);
        }
        if (mDependent.isEmpty() && mRunningAsyncCount == 0)
            mObligation.onComplete();
    }

    void setResult(int index, Object result) {
        if (mHaveResults[index])
            throw new RuntimeException("Obligation result set multiple times");
        mResults[index] = result;
        mHaveResults[index] = true;

        Iterator<Instruction> it = mDependent.iterator();
        while (it.hasNext()) {
            Instruction dep = it.next();
            boolean satisfied = true;
            for (int para : dep.needed) {
                if (!mHaveResults[para]) {
                    satisfied = false;
                    break;
                }
            }
            if (!satisfied)
                continue;
            it.remove();
            mReadyToRun.add(dep);
        }
    }

    private class AsyncRun extends AsyncTask<Void, Void, Object> {
        private Instruction mInst;

        public AsyncRun(Instruction inst) {
            mInst = inst;
        }

        @Override
        protected Object doInBackground(Void... params) {
            return executeInstruction(mInst, true);
        }

        @Override
        protected void onPostExecute(Object result) {
            mRunningAsyncCount--;
            if (mInst.result >= 0)
                setResult(mInst.result, result);
            go();
        }
    }
}
