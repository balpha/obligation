package de.balpha.obligation;

import android.content.Context;
import dalvik.system.DexFile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

public abstract class Obligation {
    private static Map<Class, InstructionSet> cache = new HashMap<Class, InstructionSet>();

    private boolean mStarted = false;

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
            //noinspection unchecked
            buildInstructionSet((Class<? extends Obligation>)cls);
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

    private static boolean isFreeOfCircularDependencies(Instruction[] instructions) {
        LinkedList<Instruction> queue = new LinkedList<Instruction>();
        HashSet<Integer> fulfilled = new HashSet<Integer>();
        LinkedList<Instruction> unfulfilled = new LinkedList<Instruction>();
        for (Instruction inst: instructions) {
            if (inst.parameters.length == 0) {
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
                for (int param : inst2.parameters) {
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
            providerTypes.add(method.getReturnType());
            nextId++;
        }
        ArrayList<Instruction> providers = new ArrayList<Instruction>();
        ArrayList<Instruction> needers = new ArrayList<Instruction>();
        ArrayList<Instruction> all = new ArrayList<Instruction>();
        for (Method method : methods) {
            Needs needs = method.getAnnotation(Needs.class);
            Provides provides = method.getAnnotation(Provides.class);
            if (needs == null && provides == null)
                continue;
            Instruction inst = new Instruction();
            inst.method = method;
            inst.async = method.isAnnotationPresent(Async.class);
            if (provides != null)
                inst.result = idMap.get(provides.value());
            else
                inst.result = -1;

            Class<?>[] params = method.getParameterTypes();
            if (needs == null && params.length > 0) {
                throw new RuntimeException("Obligation method " + method.getName() + " has formal parameters but not @Needs()");
            }
            if (needs == null) {
                inst.parameters = new int[0];
            } else {
                int[] paramIds = needs.value();
                if (params.length != paramIds.length)
                    throw new RuntimeException("Obligation method " + method.getName() + " parameter count doesn't match @Needs() arguments");
                inst.parameters = new int[params.length];
                for (int i = 0; i < params.length; i++) {
                    Integer needsId = idMap.get(paramIds[i]);
                    if (needsId == null)
                        throw new RuntimeException("Obligation method " + method.getName() + " needs object id " + paramIds[i] + " which isn't provided");
                    if (!params[i].isAssignableFrom(providerTypes.get(needsId)))
                        throw new RuntimeException("Obligation method " + method.getName() + " parameter " + i + " has type " + params[i].getName() + " but needs object id " + paramIds[i] + " which is " + providerTypes.get(needsId));
                    inst.parameters[i] = needsId;
                }
            }
            if (inst.result >= 0)
                providers.add(inst);
            if (inst.parameters.length > 0)
                needers.add(inst);
            all.add(inst);
            inst.method.setAccessible(true);
        }

        InstructionSet result = new InstructionSet();
        result.providers = new Instruction[providers.size()];
        providers.toArray(result.providers);
        result.needers = new Instruction[needers.size()];
        needers.toArray(result.needers);
        result.all = new Instruction[all.size()];
        all.toArray(result.all);

        if (!isFreeOfCircularDependencies(result.all)) {
            throw new RuntimeException("Obligation has circular dependencies");
        }

        return result;
    }

    public void fulfill() {
        if (mStarted)
            throw new RuntimeException("Obligation can only be fulfilled once");
        mStarted = true;
        Class<? extends Obligation> cls = this.getClass();
        InstructionSet is = getInstructionSet(cls);
        Job job = is.createJob(this);
        job.go();
    }

    protected void onComplete() { }
}
