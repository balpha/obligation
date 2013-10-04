package de.balpha.obligation;

/* package */ class InstructionSet {
    Instruction[] providers;
    Instruction[] needers;
    Instruction[] all;

    Job createJob(Obligation obligation) {
        return new Job(this, obligation);
    }

}
