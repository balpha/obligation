package de.balpha.obligation;

import android.os.AsyncTask;
import android.os.Looper;

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

    boolean[] mInstructionSuspended;

    HashSet<AsyncTask> mRunningAsync = new HashSet<AsyncTask>();

    Queue<Instruction> mReadyToRun;
    Queue<Instruction> mNeedToRun;

    LinkedList<ExceptionWrapper> mBlockingExceptions = new LinkedList<ExceptionWrapper>();

    private boolean isReady(Instruction inst) {
        for (int dep : inst.needed) {
            if (!mHaveResults[dep])
                return false;
        }
        return true;
    }

    Job(InstructionSet instructionSet, Obligation obligation) {
        mInstructionSet = instructionSet;
        mObligation = obligation;

        mResults = new Object[instructionSet.providers.length];
        mHaveResults = new boolean[instructionSet.providers.length];
        mInstructionSuspended = new boolean[instructionSet.providers.length];
    }

    private boolean isJobSuspended() {
        return mBlockingExceptions.size() > 0;
    }



    void prepare() {

        InstructionSet instructionSet = mInstructionSet;

        mReadyToRun = new LinkedList<Instruction>();
        boolean[] isDependedOn = new boolean[instructionSet.providers.length];
        mNeedToRun = new LinkedList<Instruction>();

        for (Instruction inst : instructionSet.goals) {
            for (int dep : inst.needed)
                if (!mHaveResults[dep])
                    isDependedOn[dep] = true;
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < instructionSet.providers.length; i++) {
                if (isDependedOn[i]) {
                    Instruction provider = instructionSet.providers[i];
                    for (int dep : provider.needed) {
                        if (mHaveResults[dep])
                            continue;
                        if (isDependedOn[dep])
                            continue;
                        changed = true;
                        isDependedOn[dep] = true;
                    }
                }
            }
        }

        for (int i = 0; i < instructionSet.providers.length; i++) {
            if (isDependedOn[i])
                mNeedToRun.add(instructionSet.providers[i]);
        }
        for (Instruction inst : instructionSet.goals) {
            if (inst.result <= 0 || !isDependedOn[inst.result])
                mNeedToRun.add(inst);
        }

        checkReady();

    }

    private Object executeInstruction(Instruction inst, boolean forceSync) throws InvocationTargetException {
        Object[] args = new Object[inst.parameterCount];
        for (int j = 0; j < inst.parameterCount; j++) {
            args[j] = mResults[inst.needed[j]];
        }
        if (!forceSync && inst.async) {
            AsyncRun task = new AsyncRun(inst);
            mRunningAsync.add(task);
            task.executeOnExecutor(sThreadPool);
            return null;
        } else {
            Object result;
            try {
                result = inst.method.invoke(mObligation, args);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            return result;
        }
    }

    private static RuntimeException asRuntimeException(Throwable ex) {
        if (ex instanceof RuntimeException)
            return (RuntimeException)ex;
        else
            return new RuntimeException(ex);
    }

    private Object onException(Instruction inst, Throwable exception) {
        if (!inst.isProvider())
            throw asRuntimeException(exception);
        boolean causedSuspension = !isJobSuspended();
        ExceptionWrapper wrapper = new ExceptionWrapper(exception, causedSuspension, this, inst);
        mBlockingExceptions.add(wrapper);
        mObligation.onException(wrapper, mInstructionSet.idMapReverse.get(inst.result));
        if (!wrapper.mHandled) {
            throw asRuntimeException(exception);
        }
        if (wrapper.resultProvided)
            return wrapper.result;
        else
            return null;
    }

    private void checkReady() {
        Iterator<Instruction> it = mNeedToRun.iterator();
        while (it.hasNext()) {
            Instruction dep = it.next();
            if (isInstructionSuspended(dep))
                continue;
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

    public void suspendInstruction(Instruction inst) {
        mInstructionSuspended[inst.result] = true;
    }

    public void resumeInstruction(Instruction inst) {
        mInstructionSuspended[inst.result] = false;
        checkReady();
        go();
    }

    private boolean isInstructionSuspended(Instruction inst) {
        return inst.isProvider() && mInstructionSuspended[inst.result];
    }

    void resumeFromAll() {
        for (int i = 0; i < mInstructionSuspended.length; i++)
            mInstructionSuspended[i] = false;

        while (!mBlockingExceptions.isEmpty()) {
            ExceptionWrapper e = mBlockingExceptions.pop();
            e.retry(false);
        }
        checkReady();
        if (!mIsGoing)
            go();
    }

    void resumeFrom(ExceptionWrapper wrapper, boolean goAgain) {
        mBlockingExceptions.remove(wrapper);
        if (goAgain && !mIsGoing)
            go();
    }

    private boolean mIsGoing = false;

    void go() {
        mIsGoing = true;
        while (mReadyToRun.size() > 0 && !isJobSuspended()) {
            Instruction inst = mReadyToRun.remove();
            Object result = null;
            try {
                result = executeInstruction(inst, false);
            } catch (InvocationTargetException e) {
                result = onException(inst, e.getCause());
            }
            if (isInstructionSuspended(inst)) {
                mNeedToRun.add(inst);
            } else if (!inst.async && inst.result >= 0) {
                setResult(inst.result, result);
                checkReady();
            }
        }

        // note that if the job is suspended at this point, then so is some instruction, and thus mNeedToRun is not empty
        if (mNeedToRun.isEmpty() && mRunningAsync.isEmpty())
            mObligation.onComplete();
        mIsGoing = false;
    }

    private static Map<Class, Class> primitiveMap = new HashMap<Class, Class>(8);
    static {
        primitiveMap.put(Boolean.TYPE, Boolean.class);
        primitiveMap.put(Byte.TYPE, Byte.class);
        primitiveMap.put(Character.TYPE, Character.class);
        primitiveMap.put(Short.TYPE, Short.class);
        primitiveMap.put(Integer.TYPE, Integer.class);
        primitiveMap.put(Long.TYPE, Long.class);
        primitiveMap.put(Float.TYPE, Float.class);
        primitiveMap.put(Double.TYPE, Double.class);
    }

    boolean checkType(int id, Object result) {
        Class<?> expected = mInstructionSet.providers[id].method.getReturnType();
        Class<?> actual = result.getClass();
        if (expected.isPrimitive()) {
            expected = primitiveMap.get(expected);
        }
        return expected.isAssignableFrom(actual);
    }

    void setResultExternal(int extId, Object result) {
        Integer id = mInstructionSet.idMap.get(extId);
        if (!checkType(id, result))
            throw new RuntimeException("setResult given wrong type; expected " + mInstructionSet.providers[id].method.getReturnType().getName() + " but got " +result.getClass().getName());
        setResult(id, result);
    }

    void setResult(int index, Object result) {
        if (mHaveResults[index])
            throw new RuntimeException("Obligation result set multiple times");
        mResults[index] = result;
        mHaveResults[index] = true;
    }

    public void cancel() {
        for (AsyncTask task : mRunningAsync) {
            task.cancel(false);
            mRunningAsync = null;
        }
    }

    private class AsyncRun extends AsyncTask<Void, Void, Object> {
        private Instruction mInst;
        private InvocationTargetException mException;

        public AsyncRun(Instruction inst) {
            mInst = inst;
        }

        @Override
        protected Object doInBackground(Void... params) {
            try {
                return executeInstruction(mInst, true);
            } catch (InvocationTargetException e) {
                mException = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(Object result) {
            if (isCancelled())
                return;
            mRunningAsync.remove(this);
            if (mException != null) {
                result = onException(mInst, mException.getCause());
            }
            if (isInstructionSuspended(mInst)) {
                mNeedToRun.add(mInst);
                return;
            }
            if (mInst.result >= 0) {
                setResult(mInst.result, result);
                checkReady();
            }
            go();
        }
    }
}
