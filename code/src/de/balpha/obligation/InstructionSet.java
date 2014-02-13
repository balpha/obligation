package de.balpha.obligation;

import java.util.HashMap;

/* package */ class InstructionSet {
    Instruction[] providers; // keyed on the result id
    Instruction[] goals; // not keyed on anything particular
    HashMap<Integer, Integer> idMap = new HashMap<Integer, Integer>(); // keys are code-provided, values are the internal ones
    HashMap<Integer, Integer> idMapReverse = new HashMap<Integer, Integer>(); // keys are the internal ones, values are code-provided
    Job createJob(Obligation obligation) {
        return new Job(this, obligation);
    }

}
