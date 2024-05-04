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
import java.util.concurrent.TimeUnit;

public class DurationUtil {

    private DurationUtil() {
    }

    public static String convertDurationToHumanReadable(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() - TimeUnit.DAYS.toHours(days);
        long minutes = duration.toMinutes() - TimeUnit.HOURS.toMinutes(duration.toHours());
        long seconds = duration.getSeconds() - TimeUnit.MINUTES.toSeconds(duration.toMinutes());

        StringBuilder humanReadable = new StringBuilder();
        if (days > 0) {
            humanReadable.append(days).append(days > 1 ? " days " : " day ");
        }
        if (hours > 0) {
            humanReadable.append(hours).append(hours > 1 ? " hours " : " hour ");
        }
        if (minutes > 0) {
            humanReadable.append(minutes).append(minutes > 1 ? " minutes " : " minute ");
        }
        if (seconds > 0) {
            humanReadable.append(seconds).append(seconds > 1 ? " seconds " : " second ");
        }

        return humanReadable.toString().trim();
    }
}
