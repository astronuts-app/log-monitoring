package io.astronuts.monitoring.logback;

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.EchoEncoder;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.util.InterruptUtil;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@SuppressWarnings("resultOfMethodCallIgnored")
public class AsyncAstronutsAppenderBase<E> extends UnsynchronizedAppenderBase<E> {


    /**
     * The default buffer size.
     */
    public static final int DEFAULT_QUEUE_SIZE = 256;
    /**
     * The default maximum queue flush time allowed during appender stop. If the
     * worker takes longer than this time it will exit, discarding any remaining
     * items in the queue
     */
    public static final int DEFAULT_MAX_FLUSH_TIME = 1000;
    static final int UNDEFINED = -1;
    protected Encoder<E> encoder = new EchoEncoder<>();
    BlockingQueue<E> blockingQueue;
    int queueSize = DEFAULT_QUEUE_SIZE;
    int discardingThreshold = UNDEFINED;
    boolean neverBlock = false;
    Worker worker = new Worker();
    int maxFlushTime = DEFAULT_MAX_FLUSH_TIME;

    /**
     * Pre-process the event prior to queueing. The base class does no
     * pre-processing but subclasses can override this behavior.
     *
     * @param eventObject - the event to pre-process
     */
    protected void preprocess(E eventObject) {
    }

    protected boolean isDiscardable(E eventObject) {
        return false;
    }

    @Override
    public void start() {
        if (isStarted())
            return;

        if (queueSize < 1) {
            addError("Invalid queue size [" + queueSize + "]");
            return;
        }
        blockingQueue = new ArrayBlockingQueue<E>(queueSize);

        if (discardingThreshold == UNDEFINED)
            discardingThreshold = queueSize / 5;
        addInfo("Setting discardingThreshold to " + discardingThreshold);
        worker.setDaemon(true);
        worker.setName("AsyncAppender-Worker-" + getName());
        // make sure this instance is marked as "started" before staring the worker
        // Thread
        super.start();
        worker.start();
    }

    @Override
    public void stop() {
        if (!isStarted())
            return;

        // mark this appender as stopped so that Worker can also processPriorToRemoval
        // if it is invoking
        // aii.appendLoopOnAppenders
        // and sub-appenders consume the interruption
        super.stop();

        // interrupt the worker thread so that it can terminate. Note that the
        // interruption can be consumed by sub-appenders
        worker.interrupt();

        InterruptUtil interruptUtil = new InterruptUtil(context);

        try {
            interruptUtil.maskInterruptFlag();

            worker.join(maxFlushTime);

            // check to see if the thread ended and if not add a warning message
            if (worker.isAlive()) {
                addWarn("Max queue flush timeout (" + maxFlushTime + " ms) exceeded. Approximately "
                        + blockingQueue.size() + " queued events were possibly discarded.");
            } else {
                addInfo("Queue flush finished successfully within timeout.");
            }

        } catch (InterruptedException e) {
            int remaining = blockingQueue.size();
            addError("Failed to join worker thread. " + remaining + " queued events may be discarded.", e);
        } finally {
            interruptUtil.unmaskInterruptFlag();
        }
    }

    @Override
    protected void append(E eventObject) {
        if (isQueueBelowDiscardingThreshold()) {
            return;
        }
        preprocess(eventObject);
        put(eventObject);
    }

    private boolean isQueueBelowDiscardingThreshold() {
        return (blockingQueue.remainingCapacity() < discardingThreshold);
    }


    private void put(E eventObject) {
        if (neverBlock) {
            blockingQueue.offer(eventObject);
        } else {
            putUninterruptibly(eventObject);
        }
    }

    private void putUninterruptibly(E eventObject) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    blockingQueue.put(eventObject);
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getDiscardingThreshold() {
        return discardingThreshold;
    }

    public void setDiscardingThreshold(int discardingThreshold) {
        this.discardingThreshold = discardingThreshold;
    }

    public int getMaxFlushTime() {
        return maxFlushTime;
    }

    public void setMaxFlushTime(int maxFlushTime) {
        this.maxFlushTime = maxFlushTime;
    }

    /**
     * Returns the number of elements currently in the blocking queue.
     *
     * @return number of elements currently in the queue.
     */
    public int getNumberOfElementsInQueue() {
        return blockingQueue.size();
    }

    public boolean isNeverBlock() {
        return neverBlock;
    }

    public void setNeverBlock(boolean neverBlock) {
        this.neverBlock = neverBlock;
    }

    /**
     * The remaining capacity available in the blocking queue.
     * <p>
     * See also {@link java.util.concurrent.BlockingQueue#remainingCapacity()
     * BlockingQueue#remainingCapacity()}
     *
     * @return the remaining capacity
     */
    public int getRemainingCapacity() {
        return blockingQueue.remainingCapacity();
    }


    static class Worker extends Thread {


        public void run() {
//            AsyncAstronutsAppenderBase<E> parent = AsyncAstronutsAppenderBase.this;
//            AppenderAttachableImpl<E> aai = parent.aai;
//
//            // loop while the parent is started
//            while (parent.isStarted()) {
//                try {
//                    List<E> elements = new ArrayList<E>();
//                    E e0 = parent.blockingQueue.take();
//                    elements.add(e0);
//                    parent.blockingQueue.drainTo(elements);
//                    for (E e : elements) {
//                        aai.appendLoopOnAppenders(e);
//                    }
//                } catch (InterruptedException e1) {
//                    // exit if interrupted
//                    break;
//                }
//            }
//
//            addInfo("Worker thread will flush remaining events before exiting. ");
//
//            for (E e : parent.blockingQueue) {
//                aai.appendLoopOnAppenders(e);
//                parent.blockingQueue.remove(e);
//            }
//
//            aai.detachAndStopAllAppenders();
        }
    }
}
