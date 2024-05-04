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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback appender for sending log events to Astronuts.
 */
@SuppressWarnings("unused")
public class AstronutsAppender extends AsyncAstronutsAppenderBase<ILoggingEvent> {

    /**
     * Constructor
     */
    public AstronutsAppender() {
    }

    /**
     * Events of level TRACE, DEBUG, INFO and WARN are deemed to be discardable.
     *
     * @param event the event to evaluate
     * @return true if the event is of level TRACE, DEBUG, INFO or WARN false otherwise.
     */
    protected boolean isDiscardable(ILoggingEvent event) {
        Level level = event.getLevel();
        boolean exclude = getExcludedLoggersSet().stream().anyMatch(loggerName -> event.getLoggerName().startsWith(loggerName));
        boolean discard = level.toInt() < Level.ERROR_INT;
        if (exclude && !discard) {
            discardedCounter.incrementAndGet();
        }
        return discard || exclude;
    }

    protected void preprocess(ILoggingEvent eventObject) {
        eventObject.prepareForDeferredProcessing();
    }

}
