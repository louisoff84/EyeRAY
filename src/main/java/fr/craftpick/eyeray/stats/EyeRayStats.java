package fr.craftpick.eyeray.stats;

import java.util.concurrent.atomic.LongAdder;

public final class EyeRayStats {
    private final LongAdder blockChangesSent = new LongAdder();
    private final LongAdder oresHidden = new LongAdder();
    private final LongAdder decoysSent = new LongAdder();
    private final LongAdder chunksScanned = new LongAdder();
    private final LongAdder blocksRevealed = new LongAdder();

    public void blockChange() { blockChangesSent.increment(); }
    public void oreHidden() { oresHidden.increment(); }
    public void decoySent() { decoysSent.increment(); }
    public void chunkScanned() { chunksScanned.increment(); }
    public void blockRevealed() { blocksRevealed.increment(); }

    public long blockChangesSent() { return blockChangesSent.sum(); }
    public long oresHidden() { return oresHidden.sum(); }
    public long decoysSent() { return decoysSent.sum(); }
    public long chunksScanned() { return chunksScanned.sum(); }
    public long blocksRevealed() { return blocksRevealed.sum(); }
}
