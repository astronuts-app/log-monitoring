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

package io.astronuts.monitoring.logback.util;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.astronuts.monitoring.logback.util.DurationUtil.convertDurationToHumanReadable;

public class StatisticsFlusher {

    private final boolean flushStatistics;
    private final Duration flushDuration;
    private final AtomicLong processedCounter;
    private final AtomicLong discardedCounter;
    private Instant lastFlushTimestamp;
    private boolean isRunning = false;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public StatisticsFlusher(boolean flushStatistics, String flushDurationString, AtomicLong processedCounter, AtomicLong discardedCounter) {

        this.flushStatistics = flushStatistics;
        this.flushDuration = Duration.parse(flushDurationString);
        this.processedCounter = processedCounter;
        this.discardedCounter = discardedCounter;
        this.lastFlushTimestamp = Instant.now();

    }

    public void scheduleStatsPrinting() {
        if (!flushStatistics) {
            return;
        }
        if (isRunning) {
            return;
        }
        // Schedule the task to run every 1 minute
        scheduler.scheduleAtFixedRate(statsPrinter(), 60, flushDuration.getSeconds(), TimeUnit.SECONDS);
        this.isRunning = true;
    }

    public void stopPrintingStats() {
        if (!flushStatistics) {
            return;
        }

        if (!isRunning) {
            return;
        }
        try {
            scheduler.shutdownNow();
        } catch (Throwable t) {
            // Ignore
        } finally {
            this.isRunning = false;
        }
    }

    private Runnable statsPrinter() {
        return () -> {
            Instant now = Instant.now();
            long processedSinceLast = processedCounter.getAndSet(0);
            long discardedSinceLast = discardedCounter.getAndSet(0);
            Duration durationSinceLast = Duration.between(lastFlushTimestamp, now);
            lastFlushTimestamp = now;

            String sb = "Astronuts log monitoring stats reporter: [Time since last stats flush = " +
                    convertDurationToHumanReadable(durationSinceLast) + ", " +
                    "Processed = " + processedSinceLast + ", " +
                    "Suppressed = " + discardedSinceLast + "]";
            System.out.println(sb);
        };
    }

}
