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

import java.io.IOException;

@SuppressWarnings("unused")
public class AstronutsAppender<E> extends AsyncAstronutsAppenderBase<E> {

    private static final String DEFAULT_ENDPOINT_URL = "http://localhost:9245/api/foxbat/log-event";
    private final EventTransformer eventTransformer = new DefaultEventTransformer();
    private String secretKey;
    private String endpointUrl;

    @SuppressWarnings("unused")
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @SuppressWarnings("unused")
    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    protected boolean isDiscardable(E eventObject) {
        if (eventObject instanceof ILoggingEvent) {
            ILoggingEvent event = (ILoggingEvent) eventObject;
            Level level = event.getLevel();
            return level.toInt() < Level.ERROR_INT;
        }
        return false;
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
            System.err.println("Error: Astronuts 'File Secret Key' must be provided. Appender will not be started.");
            return;
        }
        super.start();
    }

    @Override
    protected void append(E eventObject) {

        if (!isStarted()) {
            return; // Do not append if the appender hasn't started properly, or the event is discardable
        }

        if (isDiscardable(eventObject)) {
            return;
        }
        String logMessage = eventTransformer.transformEventToJson(secretKey, new String(encoder.encode(eventObject)));

        sendLogAsync(logMessage);
    }

    private void sendLogAsync(String logMessage) {
        new Thread(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(endpointUrl);
                httpPost.setEntity(new StringEntity(logMessage));
                httpPost.setHeader("Content-Type", "application/json");

                httpClient.execute(httpPost, response -> {
                    int status = response.getCode();
                    if (status >= 200 && status < 300) {
                        return EntityUtils.toString(response.getEntity());
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                });
            } catch (IOException e) {
                // Handle exceptions appropriately
                //System.out.println("Failed to send log message to Astronuts: " + e.getMessage()) ;
            }
        }).start();
    }
}
