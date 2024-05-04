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
import io.astronuts.monitoring.logback.util.StatisticsFlusher;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static io.astronuts.monitoring.logback.util.DurationUtil.convertDurationToHumanReadable;

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
     * The processed counter.
     */
    final AtomicLong processedCounter = new AtomicLong(0);

    /**
     * The discarded counter.
     */
    final AtomicLong discardedCounter = new AtomicLong(0);

    /**
     * The default endpoint URL.
     */
    private static final String DEFAULT_ENDPOINT_URL = "https://api.astronuts.io/helios/api/foxbat/log-event";

    /**
     * The default flush duration in ISO-8601 duration format, such as "PT1M" for 1 minute, "PT1H" for 1 hour, etc.
     */
    private static final String DEFAULT_FLUSH_DURATION = "PT10M";

    /**
     * The blocking queue holding the events to log.
     */
    BlockingQueue<E> blockingQueue;

    StatisticsFlusher statisticsFlusher;

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
     * The list of excluded loggers names as a comma seperated list.
     */
    private String excludedLoggerNames = "";

    /**
     * The list of logger names to exclude as an internal list.
     */
    private final Set<String> excludedLoggersSet = new HashSet<>();

    /**
     * Is log monitoring disabled?
     */
    private boolean disableLogMonitoring = false;

    /**
     * Flush statistics flag.
     */
    private boolean flushStatistics = false;

    /**
     * The flush duration in ISO-8601 duration format, such as "PT15M" for 15 minutes, "PT1H" for 1 hour, etc.
     */
    private String flushDuration;

    /**
     * The flush duration in ISO-8601 duration format, such as "PT15M" for 15 minutes, "PT1H" for 1 hour, etc.
     */
    @SuppressWarnings("unused")
    public String getFlushDuration() {
        return flushDuration;
    }

    /**
     * Set the flush duration in ISO-8601 duration format, such as "PT15M" for 15 minutes, "PT1H" for 1 hour, etc.
     */
    @SuppressWarnings("unused")
    public void setFlushDuration(String flushDuration) {
        this.flushDuration = flushDuration;
    }

    /**
     * Is flush statistics enabled?
     *
     * @return true if flush statistics is enabled.
     */
    @SuppressWarnings("unused")
    public boolean isFlushStatistics() {
        return flushStatistics;
    }

    /**
     * Set flush statistics to enabled.
     *
     * @param flushStatistics true if flush statistics is enabled.
     */
    @SuppressWarnings("unused")
    public void setFlushStatistics(boolean flushStatistics) {
        this.flushStatistics = flushStatistics;
    }

    /**
     * Is log monitoring disabled?
     *
     * @return true if log monitoring is disabled.
     */
    @SuppressWarnings("unused")
    public boolean isDisableLogMonitoring() {
        return disableLogMonitoring;
    }

    /**
     * Set log monitoring to disabled.
     *
     * @param disableLogMonitoring true if log monitoring is disabled.
     */
    @SuppressWarnings("unused")
    public void setDisableLogMonitoring(boolean disableLogMonitoring) {
        this.disableLogMonitoring = disableLogMonitoring;
    }

    /**
     * Set the secret key.
     *
     * @param secretKey The secret key.
     */
    @SuppressWarnings("unused")
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Set the endpoint URL.
     *
     * @param endpointUrl The endpoint URL.
     */
    @SuppressWarnings("unused")
    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    /**
     * Get the excluded logger names.
     *
     * @return The comma seperated list of excluded logger names.
     */
    @SuppressWarnings("unused")
    public String getExcludedLoggerNames() {
        return excludedLoggerNames;
    }

    /**
     * Set the excluded logger names.
     *
     * @param excludedLoggerNames The comma seperated list of excluded logger names.
     */
    @SuppressWarnings("unused")
    public void setExcludedLoggerNames(String excludedLoggerNames) {
        this.excludedLoggerNames = excludedLoggerNames;
    }

    /**
     * Get the set of excluded loggers.
     *
     * @return The set of excluded loggers.
     */
    public Set<String> getExcludedLoggersSet() {
        return excludedLoggersSet;
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
     * worker takes longer than this time, it will exit, discarding any remaining
     * items in the queue
     */
    public static final int DEFAULT_MAX_FLUSH_TIME = 1000;
    int maxFlushTime = DEFAULT_MAX_FLUSH_TIME;

    /**
     * Is the eventObject passed as parameter discardable? The base class's
     * implementation of this method always returns 'false' but subclasses may (and
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
     * pre-processing, but subclasses can override this behavior.
     *
     * @param eventObject - the event to pre-process
     */
    protected void preprocess(E eventObject) {
    }

    @Override
    public void start() {
        if (isStarted())
            return;

        // Initialize the disableLogMonitoring field with the correct precedence
        if (!disableLogMonitoring) {
            disableLogMonitoring = Boolean.parseBoolean(System.getenv("ASTRONUTS_DISABLE_LOG_MONITORING"));
            if (!disableLogMonitoring) {
                disableLogMonitoring = Boolean.parseBoolean(System.getProperty("astronuts.disable.log.monitoring"));
            }
        }

        if (disableLogMonitoring) {
            System.out.printf("\nInfo: You have disabled Astronuts log monitoring (v%s). To read more and manage log\n" +
                    "monitoring configurations see https://www.astronuts.io/docs/log-monitoring.\n", getVersion());
            return;
        }

        // Initialize the flushStatistics field with the correct precedence
        if (!flushStatistics) {
            flushStatistics = Boolean.parseBoolean(System.getenv("ASTRONUTS_FLUSH_STATISTICS"));
            if (!flushStatistics) {
                flushStatistics = Boolean.parseBoolean(System.getProperty("astronuts.flush.statistics"));
            }
        }

        // Initialize the flushDuration field with the correct precedence
        if (flushDuration == null || flushDuration.trim().isEmpty()) {
            flushDuration = System.getenv("ASTRONUTS_FLUSH_STATISTICS_DURATION");
            if (flushDuration == null || flushDuration.trim().isEmpty()) {
                flushDuration = System.getProperty("astronuts.flush.statistics.duration");
            }
            if (flushDuration == null || flushDuration.trim().isEmpty()) {
                flushDuration = DEFAULT_FLUSH_DURATION;
            }
        }

        // Initialize the secretKey with the correct precedence
        if (secretKey == null || secretKey.trim().isEmpty()) {
            secretKey = System.getenv("ASTRONUTS_LOGFILE_SECRET");
            if (secretKey == null || secretKey.trim().isEmpty()) {
                secretKey = System.getProperty("astronuts.logfile.secret");
            }
        }

        // Initialize the endpoint URL with the correct precedence
        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            endpointUrl = System.getenv("ASTRONUTS_ENDPOINT_URL");
            if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
                endpointUrl = System.getProperty("astronuts.endpoint.url");
            }
            if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
                endpointUrl = DEFAULT_ENDPOINT_URL;
            }
        }

        // Initialize the list of excluded loggers with the correct precedence
        if (excludedLoggerNames == null || excludedLoggerNames.trim().isEmpty()) {
            excludedLoggerNames = System.getenv("ASTRONUTS_EXCLUDED_LOGGER_NAMES");
            if (excludedLoggerNames == null || excludedLoggerNames.trim().isEmpty()) {
                excludedLoggerNames = System.getProperty("astronuts.excluded.logger.names");
            }
            if (excludedLoggerNames != null && !excludedLoggerNames.trim().isEmpty()) {
                Arrays.stream(excludedLoggerNames.split(","))
                        .map(String::trim)
                        .forEach(excludedLoggersSet::add);
            }
        }

        if (secretKey == null || secretKey.trim().isEmpty()) {
            System.out.printf("\nWarn: Astronuts log monitoring (v%s) library was found in classpath, but the\n" +
                            "Astronuts 'File Secret Key' was not provided. To solve the issue, or disable\n" +
                            "Astronuts log monitoring see https://www.astronuts.io/docs/log-monitoring. After\n" +
                            "fixing the issue, restart your application.\n", getVersion());
            return;
        }

        if (queueSize < 1) {
            addError("Invalid queue size [" + queueSize + "]");
            return;
        }

        // Check for the flushDuration and set default if needed
        try {
            Duration d = Duration.parse(flushDuration);
            // Set default flush duration if the user supplied duration is less than 1 minute
            if (d.getSeconds() < 60) {
                flushDuration = DEFAULT_FLUSH_DURATION;
            }
        } catch (Exception e) {
            flushDuration = DEFAULT_FLUSH_DURATION;
        }

        statisticsFlusher = new StatisticsFlusher(flushStatistics, flushDuration, processedCounter, discardedCounter);

        blockingQueue = new ArrayBlockingQueue<>(queueSize);

        eventTransformer = new DefaultEventTransformer();

        logShipper = new ApacheHttpLogShipper(getVersion(), secretKey, endpointUrl, this);

        if (discardingThreshold == UNDEFINED)
            discardingThreshold = queueSize / 5;

        addInfo("Setting discardingThreshold to " + discardingThreshold);

        worker.setDaemon(true);

        worker.setName("AsyncAstronutsAppender-Worker-" + getName());
        // make sure this instance is marked as "started" before staring the worker
        // Thread
        super.start();
        statisticsFlusher.scheduleStatsPrinting();
        worker.start();

        String s = getKickoffString();
        System.out.println(s);
    }

    @Override
    protected void append(E eventObject) {
        if (isQueueBelowDiscardingThreshold() || isDiscardable(eventObject)) {
            return;
        }
        preprocess(eventObject);
        put(eventObject);
    }

    @Override
    public void stop() {
        if (!isStarted())
            return;

        // mark this appender as stopped so that Worker can also processPriorToRemoval
        // if it is working
        super.stop();
        statisticsFlusher.stopPrintingStats();

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
     *
     * @return true if blocking is disabled.
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
     *
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

    private boolean isQueueBelowDiscardingThreshold() {
        return (blockingQueue.remainingCapacity() < discardingThreshold);
    }

    private String getKickoffString() {
        String s = String.format(
                "\nInfo: Astronuts log monitoring (v%s) is active through the Astronuts Logback appender\n" +
                        "To learn more and customize the configuration, please visit " +
                        "https://www.astronuts.io/docs/log-monitoring.\n",
                getVersion());

        if (excludedLoggerNames != null && !excludedLoggerNames.trim().isEmpty()) {
            s += String.format("Excluded logger names: [%s] \n", excludedLoggerNames);
        }

        if (flushStatistics) {
            s += String.format("Statistics will be flushed every %s.\n",
                    convertDurationToHumanReadable(Duration.parse(flushDuration)));
        }
        return s;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
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

    class Worker extends Thread {

        @SuppressWarnings("ResultOfMethodCallIgnored")
        public void run() {
            AsyncAstronutsAppenderBase<E> parent = AsyncAstronutsAppenderBase.this;

            // loop while the parent is started
            while (parent.isStarted()) {
                try {
                    List<E> elements = new ArrayList<>();
                    E e0 = parent.blockingQueue.take();
                    elements.add(e0);
                    parent.blockingQueue.drainTo(elements);
                    for (E e : elements) {
                        processedCounter.incrementAndGet();
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
