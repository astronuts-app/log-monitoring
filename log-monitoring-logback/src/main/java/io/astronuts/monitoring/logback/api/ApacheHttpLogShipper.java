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

package io.astronuts.monitoring.logback.api;

import ch.qos.logback.core.spi.LifeCycle;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * ApacheHttpLogShipper is a LogShipper implementation that sends log messages to the Astronuts using the Apache HTTP
 * client.
 *
 * @author Arun Patra
 * @since 1.0.0
 */
public class ApacheHttpLogShipper implements LogShipper{

    private final String version;
    private final String secretKey;
    private final String endpointUrl;
    private final LifeCycle lifeCycle;
    private final EventTransformer eventTransformer;

    /**
     * Constructor.
     *
     * @param secretKey   The secret key to use
     * @param endpointUrl The endpoint URL
     * @param lifeCycle   The lifecycle object
     */
    public ApacheHttpLogShipper(String version, String secretKey, String endpointUrl,
                                LifeCycle lifeCycle) {
        this.version = version;
        this.secretKey = secretKey;
        this.endpointUrl = endpointUrl;
        this.eventTransformer = new DefaultEventTransformer();
        this.lifeCycle = lifeCycle;
    }
    private boolean noticeIssued = false;


    @Override
    public void sendLogAsync(String logMessage) {

        String JSON = eventTransformer.transformEventToJson(secretKey, logMessage);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(endpointUrl);
            httpPost.setEntity(new StringEntity(JSON));
            httpPost.setHeader("Content-Type", "application/json");

            httpClient.execute(httpPost, response -> {
                int status = response.getCode();

                switch (status) {
                    case 401:
                    case 403: {
                        System.err.printf("\nError: Unauthorized. For help with Astronuts log monitoring (v%s),\n" +
                                "please see https://www.astronuts.io/docs/log-monitoring. After fixing the issue,\n" +
                                "restart your application.\n", version);
                        lifeCycle.stop();
                        break;
                    }
                    case 500:
                        if (!noticeIssued) {
                            System.err.printf("\nWarning: Astronuts log monitoring (v%s) is currently unavailable.\n" +
                                    "Please check https://status.astronuts.io for service status. Log monitoring\n" +
                                    "will be disabled until the service is restored.\n", version);
                            noticeIssued = true;
                        }
                        break;
                    case 200:
                    case 201:
                    case 202:
                        return EntityUtils.toString(response.getEntity());
                    default: {
                        lifeCycle.stop();
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            // NOOP
        }
    }
}
