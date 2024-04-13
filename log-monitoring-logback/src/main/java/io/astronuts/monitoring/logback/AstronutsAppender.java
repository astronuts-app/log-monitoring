package io.astronuts.monitoring.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.astronuts.monitoring.api.DefaultEventTransformer;
import io.astronuts.monitoring.api.EventTransformer;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

@SuppressWarnings("unused")
public class AstronutsAppender<E> extends AsyncAstronutsAppenderBase<E> {

    private static final String DEFAULT_ENDPOINT_URL = "https://api.astronuts.io/helios/api/foxbat/log-event";
    private final EventTransformer eventTransformer = new DefaultEventTransformer();
    private String secretKey;
    private String endpointUrl;
    private boolean noticeIssued = false;
    @SuppressWarnings("unused")
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @SuppressWarnings("unused")
    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    /**
     * Check if the event is discardable. By default, events with a level lower than ERROR are discardable.
     * @param eventObject The event object
     * @return  true if the event is discardable, false otherwise
     */
    protected boolean isDiscardable(E eventObject) {
        if (eventObject instanceof ILoggingEvent) {
            ILoggingEvent event = (ILoggingEvent) eventObject;
            Level level = event.getLevel();
            return level.toInt() < Level.ERROR_INT;
        } else {
            return false;
        }
    }

    @Override
    public void start() {

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
            System.err.println("Error: Astronuts 'File Secret Key' must be provided. For more information see " +
                    "https://www.astronuts.io/docs/log-monitoring. After fixing the issue, restart your application.");
            return;
        }
        super.start();
        System.out.println("Info: You have enabled Astronuts log monitoring through the Logback appender. " +
                "To customize the configuration, or for more details, please visit " +
                "https://www.astronuts.io/docs/log-monitoring.");
    }

    @Override
    protected void append(E eventObject) {

        if (!isStarted()) {
            return; // Do not append if the appender hasn't started properly, or the event is discardable
        }

        if (isDiscardable(eventObject)) {
            return;
        }

        sendLogAsync(eventTransformer.transformEventToJson(secretKey, new String(encoder.encode(eventObject))));
    }

    private void sendLogAsync(String logMessage) {
        new Thread(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(endpointUrl);
                httpPost.setEntity(new StringEntity(logMessage));
                httpPost.setHeader("Content-Type", "application/json");

                httpClient.execute(httpPost, response -> {
                    int status = response.getCode();

                    switch (status) {
                        case 401:
                        case 403: {
                            System.err.println("Error: Unauthorized. For help, please see " +
                                    "https://www.astronuts.io/docs/log-monitoring. After fixing the issue, " +
                                    "restart your application.");
                            super.stop();
                            break;
                        }
                        case 500:
                            if (!noticeIssued) {
                                System.err.println("Warning: Astronuts log monitoring is currently unavailable. " +
                                        "Please check https://status.astronuts.io for service status. Log monitoring" +
                                        " will be disabled until the service is restored.");
                                noticeIssued = true;
                            }
                            break;
                        case 200:
                        case 201:
                        case 202:
                            return EntityUtils.toString(response.getEntity());
                        default: {
                            super.stop();
                            throw new ClientProtocolException("Unexpected response status: " + status);
                        }
                    }
                    return null;
                });
            } catch (Exception e) {
                // NOOP
            }
        }).start();
    }
}
