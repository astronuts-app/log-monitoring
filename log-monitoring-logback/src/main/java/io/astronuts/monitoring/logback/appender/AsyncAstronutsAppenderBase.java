/*
 * Copyright 2024 Astronuts, Inc., the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.astronuts.monitoring.logback.appender;

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.EchoEncoder;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.util.InterruptUtil;
import io.astronuts.monitoring.logback.api.ApacheHttpLogShipper;
import io.astronuts.monitoring.logback.api.DefaultEventTransformer;
import io.astronuts.monitoring.logback.api.EventTransformer;
import io.astronuts.monitoring.logback.api.LogShipper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * An asynchronous appender base class for sending log events to the Astronuts API.
 *
 * @param <E> The type of log event to append
 */

@SuppressWarnings("resultOfMethodCallIgnored")
public class AsyncAstronutsAppenderBase<E> extends UnsynchronizedAppenderBase<E> {

    /**
     * Constructor
     */
    public AsyncAstronutsAppenderBase() {
    }

    /**
     * The default endpoint URL.
     */
    private static final String DEFAULT_ENDPOINT_URL = "https://api.astronuts.io/helios/api/foxbat/log-event";

    /**
     *  The blocking queue holding the events to log.
     */
    BlockingQueue<E> blockingQueue;

    /**
     * The encoder which is ultimately responsible for writing the eventObject to a byte array.
     */
    Encoder<E> encoder = new EchoEncoder<>();

    /**
     * The log shipper which is responsible for sending the log events to the Astronuts API.
     */
    LogShipper logShipper;

    /**
     * The event transformer which is responsible for transforming the log events to a JSON format.
     */
    EventTransformer eventTransformer;

    /**
     * The secret key used to authenticate the log events.
     */
    private String secretKey;

    /**
     * The endpoint URL to which the log events are sent.
     */
    private String endpointUrl;

    /**
     * Set the secret key.
     * @param secretKey The secret key.
     */
    @SuppressWarnings("unused")
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Set the endpoint URL.
     * @param endpointUrl The endpoint URL.
     */
    @SuppressWarnings("unused")
    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    /**
     * The default buffer size.
     */
    public static final int DEFAULT_QUEUE_SIZE = 256;
    int queueSize = DEFAULT_QUEUE_SIZE;

    static final int UNDEFINED = -1;
    int discardingThreshold = UNDEFINED;
    boolean neverBlock = false;

    Worker worker = new Worker();

    /**
     * The default maximum queue flush time allowed during appender stop. If the
     * worker takes longer than this time it will exit, discarding any remaining
     * items in the queue
     */
    public static final int DEFAULT_MAX_FLUSH_TIME = 1000;
    int maxFlushTime = DEFAULT_MAX_FLUSH_TIME;


    /**
     * Is the eventObject passed as parameter discardable? The base class's
     * implementation of this method always returns 'false' but sub-classes may (and
     * do) override this method.
     * <p>
     * Note that only if the buffer is nearly full are events discarded. Otherwise,
     * when the buffer is "not full" all events are logged.
     *
     * @param eventObject - the event to check
     * @return - true if the event can be discarded, false otherwise
     */
    protected boolean isDiscardable(E eventObject) {
        return false;
    }

    /**
     * Pre-process the event prior to queueing. The base class does no
     * pre-processing but subclasses can override this behavior.
     *
     * @param eventObject - the event to pre-process
     */
    protected void preprocess(E eventObject) {
    }


    @Override
    public void start() {
        if (isStarted())
            return;

        // Initialize the secretKey with the correct precedence
        if (secretKey == null || secretKey.trim().isEmpty()) {
            secretKey = System.getenv("ASTRONUTS_LOGFILE_SECRET");
            if (secretKey == null || secretKey.trim().isEmpty()) {
                secretKey = System.getProperty("astronuts.logfile.secret");
            }
        }

        // Initialize the secretKey with the correct precedence
        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            endpointUrl = System.getenv("ASTRONUTS_ENDPOINT_URL");
            if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
                endpointUrl = System.getProperty("astronuts.endpoint.url");
            }
            if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
                endpointUrl = DEFAULT_ENDPOINT_URL;
            }
        }

        if (secretKey == null || secretKey.trim().isEmpty()) {
            System.out.println("Warn: Astronuts log monitoring library was found in classpath, but the Astronuts " +
                    "'File Secret Key' was not provided. To solve the issue, or disable Astronuts log monitoring see " +
                    "https://www.astronuts.io/docs/log-monitoring. After fixing the issue, restart your application.");
            return;
        }

        if (queueSize < 1) {
            addError("Invalid queue size [" + queueSize + "]");
            return;
        }

        blockingQueue = new ArrayBlockingQueue<E>(queueSize);

        eventTransformer = new DefaultEventTransformer();
        logShipper = new ApacheHttpLogShipper(secretKey, endpointUrl, this);

        if (discardingThreshold == UNDEFINED)
            discardingThreshold = queueSize / 5;

        addInfo("Setting discardingThreshold to " + discardingThreshold);

        worker.setDaemon(true);

        worker.setName("AsyncAstronutsAppender-Worker-" + getName());
        // make sure this instance is marked as "started" before staring the worker
        // Thread
        super.start();
        worker.start();
        System.out.printf("Info: You have enabled Astronuts log monitoring v%s through the Logback " +
                "appender. To customize the configuration, or for more details, please visit " +
                "https://www.astronuts.io/docs/log-monitoring.%n", getVersion());
    }

    @Override
    public void stop() {
        if (!isStarted())
            return;

        // mark this appender as stopped so that Worker can also processPriorToRemoval
        // if it is working
        super.stop();

        // interrupt the worker thread so that it can terminate.
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
        if (isQueueBelowDiscardingThreshold() || isDiscardable(eventObject)) {
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

    /**
     * Get the number of elements currently in the blocking queue.
     *
     * @return number of elements currently in the queue.
     */
    @SuppressWarnings("unused")
    public int getQueueSize() {
        return queueSize;
    }

    /**
     * Set the queue size.
     *
     * @param queueSize The queue size.
     */
    @SuppressWarnings("unused")
    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    /**
     * Get the discarding threshold.
     *
     * @return The discarding threshold.
     */
    @SuppressWarnings("unused")
    public int getDiscardingThreshold() {
        return discardingThreshold;
    }

    /**
     * Set the discarding threshold.
     *
     * @param discardingThreshold The discarding threshold.
     */
    @SuppressWarnings("unused")
    public void setDiscardingThreshold(int discardingThreshold) {
        this.discardingThreshold = discardingThreshold;
    }

    /**
     * Get the maximum queue flush time allowed during appender stop. If the worker
     * takes longer than this time it will exit, discarding any remaining items in
     * the queue.
     *
     * @return The maximum queue flush time.
     */
    @SuppressWarnings("unused")
    public int getMaxFlushTime() {
        return maxFlushTime;
    }

    /**
     * Set the maximum queue flush time allowed during appender stop. If the worker
     * takes longer than this time it will exit, discarding any remaining items in
     * the queue.
     *
     * @param maxFlushTime The maximum queue flush time.
     */
    @SuppressWarnings("unused")
    public void setMaxFlushTime(int maxFlushTime) {
        this.maxFlushTime = maxFlushTime;
    }

    /**
     * Returns the number of elements currently in the blocking queue.
     *
     * @return number of elements currently in the queue.
     */
    @SuppressWarnings("unused")
    public int getNumberOfElementsInQueue() {
        return blockingQueue.size();
    }

    /**
     * Returns true if blocking is disabled.
     * @return  true if blocking is disabled.
     */
    @SuppressWarnings("unused")
    public boolean isNeverBlock() {
        return neverBlock;
    }

    /**
     * If true, the appender will not wait for space to become available in the
     * queue. This can be useful if you want to discard events rather than block
     * the producer thread.
     *
     * @param neverBlock true if the appender should not block
     */
    @SuppressWarnings("unused")
    public void setNeverBlock(boolean neverBlock) {
        this.neverBlock = neverBlock;
    }

    /**
     * The remaining capacity available in the blocking queue.
     * <p>
     * See also {@link BlockingQueue#remainingCapacity()
     * BlockingQueue#remainingCapacity()}
     *
     * @return the remaining capacity
     */
    @SuppressWarnings("unused")
    public int getRemainingCapacity() {
        return blockingQueue.remainingCapacity();
    }

    /**
     * Get the version of the library.
     * @return The version of the library.
     */
    private String getVersion() {
        String version = "0.0.0";
        Properties props = new Properties();
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("app.properties")) {
            if (is != null) {
                props.load(is);
                version = props.getProperty("version");
            }
        } catch (Throwable e) {
            // NOOP
        }
        return version;
    }

    class Worker extends Thread {

        public void run() {
            AsyncAstronutsAppenderBase<E> parent = AsyncAstronutsAppenderBase.this;

            // loop while the parent is started
            while (parent.isStarted()) {
                try {
                    List<E> elements = new ArrayList<E>();
                    E e0 = parent.blockingQueue.take();
                    elements.add(e0);
                    parent.blockingQueue.drainTo(elements);
                    for (E e : elements) {
                        logShipper.sendLogAsync(new String(encoder.encode(e)));
                    }
                } catch (InterruptedException e1) {
                    // exit if interrupted
                    break;
                }
            }

            addInfo("Worker thread will flush remaining events before exiting. ");

            for (E e : parent.blockingQueue) {
                logShipper.sendLogAsync(new String(encoder.encode(e)));
                parent.blockingQueue.remove(e);
            }
        }
    }
}
