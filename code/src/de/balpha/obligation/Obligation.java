package de.balpha.obligation;

import android.content.Context;
import android.os.Looper;
import dalvik.system.DexFile;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

public abstract class Obligation {
    private static Map<Class, InstructionSet> cache = new HashMap<Class, InstructionSet>();

    private boolean mStarted = false;
    private Job mJob;


    private static synchronized InstructionSet getInstructionSet(Class<? extends Obligation> cls) {
        InstructionSet is = cache.get(cls);
        if (is != null)
            return is;
        is = buildInstructionSet(cls);
        cache.put(cls, is);
        return is;
    }

    public static String checkAllObligationsInPackage(Context context) {
        try {
            return checkAllObligationsInPackageImpl(context);
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    public static String checkObligation(Class<? extends Obligation> cls) {
        try {
            buildInstructionSet(cls);
        } catch (Exception e) {
            return e.getMessage();
        }
        return null;

    }

    private static String checkAllObligationsInPackageImpl(Context context) throws IOException {
        Enumeration<String> allClasses = new DexFile(context.getPackageCodePath()).entries();
        while (allClasses.hasMoreElements()) {
            String classname = allClasses.nextElement();
            Class<?> cls;
            try {
                cls = Class.forName(classname);
            } catch (ClassNotFoundException e) {
                continue;
            }
            if (Obligation.class.isAssignableFrom(cls) && cls != Obligation.class) {
                //noinspection unchecked
                String e = checkObligation((Class<? extends Obligation>) cls);
                if (e != null)
                    return classname + ": " + e;
            }
        }
        return null;
    }

    private static boolean isFreeOfCircularDependencies(List<Instruction> instructions) {
        LinkedList<Instruction> queue = new LinkedList<Instruction>();
        HashSet<Integer> fulfilled = new HashSet<Integer>();
        LinkedList<Instruction> unfulfilled = new LinkedList<Instruction>();
        for (Instruction inst: instructions) {
            if (inst.needed.length == 0) {
                queue.add(inst);
            } else {
                unfulfilled.add(inst);
            }
        }

        while (queue.size() > 0) {
            Instruction inst = queue.pop();
            if (inst.result >= 0)
                fulfilled.add(inst.result);
            else
                continue;

            Iterator<Instruction> it = unfulfilled.iterator();
            while (it.hasNext()) {
                Instruction inst2 = it.next();
                boolean ok = true;
                for (int param : inst2.needed) {
                    if (!fulfilled.contains(param)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    queue.add(inst2);
                    it.remove();
                }
            }
        }
        return unfulfilled.size() == 0;
    }

    // FIXME: handle inheritance?
    private static InstructionSet buildInstructionSet(Class<? extends Obligation> cls) {
        HashMap<Integer, Integer> idMap = new HashMap<Integer, Integer>(); // keys are code-provided, values are the internal ones
        HashMap<Integer, Integer> idMapReverse = new HashMap<Integer, Integer>(); // keys are the internal ones, values are code-provided
        int nextId = 0;
        ArrayList<Class<?>> providerTypes = new ArrayList<Class<?>>(); // FIXME: handle generics
        Method[] methods = cls.getDeclaredMethods();
        for (Method method : methods) {
            Provides provides = method.getAnnotation(Provides.class);
            if (provides == null)
                continue;
            int val = provides.value();
            if (val <= 0)
                throw new RuntimeException("Obligation object ids must be positive; found " + val);
            if (idMap.containsKey(val))
                throw new RuntimeException("multiple Obligation methods provide object id " + val);
            idMap.put(val, nextId);
            idMapReverse.put(nextId, val);
            providerTypes.add(method.getReturnType());
            nextId++;
        }
        ArrayList<Instruction> providers = new ArrayList<Instruction>();
        ArrayList<Instruction> goals = new ArrayList<Instruction>();
        ArrayList<Instruction> all = new ArrayList<Instruction>();
        for (Method method : methods) {
            Needs needs = method.getAnnotation(Needs.class);
            Provides provides = method.getAnnotation(Provides.class);
            boolean isGoal = method.isAnnotationPresent(Goal.class);
            boolean typeCheckOnly = false;
            if (provides == null && !isGoal) {
                if (needs != null)
                    typeCheckOnly = true; // a method that provides nothing and is not a goal method will never be called by th obligation mechanism
                else
                    continue; // not an obligation method
            }
            Instruction inst = new Instruction();
            inst.method = method;
            inst.async = method.isAnnotationPresent(Async.class);
            inst.goal = isGoal;
            if (provides != null)
                inst.result = idMap.get(provides.value());
            else
                inst.result = -1;

            Class<?>[] params = method.getParameterTypes();
            if (needs == null && params.length > 0) {
                throw new RuntimeException("Obligation method " + method.getName() + " has formal parameters but not @Needs()");
            }
            if (needs == null) {
                inst.needed = new int[0];
            } else {
                int[] neededIds = needs.value();
                if (params.length > neededIds.length)
                    throw new RuntimeException("Obligation method " + method.getName() + " has more parameters than @Needs() arguments");
                if (typeCheckOnly && params.length < neededIds.length)
                    throw new RuntimeException("Obligation method " + method.getName() + " has fewer parameters than @Needs() arguments, and is neither a goal nor a provider. This is very likely a mistake.");
                inst.needed = new int[neededIds.length];
                inst.parameterCount = params.length;
                for (int i = 0; i < neededIds.length; i++) {
                    Integer needsId = idMap.get(neededIds[i]);
                    if (needsId == null)
                        throw new RuntimeException("Obligation method " + method.getName() + " needs object id " + neededIds[i] + " which isn't provided");
                    inst.needed[i] = needsId;
                    if (i < params.length) {
                        if (!params[i].isAssignableFrom(providerTypes.get(needsId)))
                            throw new RuntimeException("Obligation method " + method.getName() + " parameter " + i + " has type " + params[i].getName() + " but needs object id " + neededIds[i] + " which is " + providerTypes.get(needsId));
                    }
                }
            }

            if (typeCheckOnly)
                continue;

            if (inst.result >= 0)
                providers.add(inst);
            if (inst.goal)
                goals.add(inst);
            all.add(inst);
            inst.method.setAccessible(true);
        }

        InstructionSet result = new InstructionSet();
        result.providers = new Instruction[providers.size()];
        for (Instruction p : providers) {
            result.providers[p.result] = p;
        }
        result.goals = new Instruction[goals.size()];
        goals.toArray(result.goals);

        if (!isFreeOfCircularDependencies(all)) {
            throw new RuntimeException("Obligation has circular dependencies");
        }
        result.idMap = idMap;
        result.idMapReverse = idMapReverse;

        return result;
    }

    private void ensureJob() {
        if (mJob == null) {
            Class<? extends Obligation> cls = this.getClass();
            InstructionSet is = getInstructionSet(cls);
            mJob = is.createJob(this);
        }
    }

    protected void setResult(int id, Object data) {
        if (mStarted)
            throw new RuntimeException("Obligation cannot be given data externally after fulfillment has started");
        ensureJob();
        mJob.setResultExternal(id, data);
    }

    public void fulfill() {
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new RuntimeException("Obligation.fulfill() must be called from the UI thread");
        if (mStarted)
            throw new RuntimeException("Obligation can only be fulfilled once");
        mStarted = true;
        ensureJob();
        mJob.prepare();
        mJob.go();
    }

    public void cancel() {
        mJob.cancel();
    }

    protected void onComplete() { }

    public void onException(ExceptionWrapper problem, int id) {
        // nothing
    }
}
