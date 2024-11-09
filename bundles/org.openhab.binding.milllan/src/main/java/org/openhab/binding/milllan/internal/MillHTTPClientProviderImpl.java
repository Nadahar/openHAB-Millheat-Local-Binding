package org.openhab.binding.milllan.internal;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.openhab.binding.milllan.internal.exception.MillException;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.ThingStatusDetail;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
@Component(service = {MillHTTPClientProvider.class})
public class MillHTTPClientProviderImpl implements MillHTTPClientProvider {

    private final Logger logger = LoggerFactory.getLogger(MillHTTPClientProviderImpl.class);

    @SuppressWarnings("unused") //TODO: (Nad) Figure out
    @Nullable
    private final HttpClientFactory httpClientFactory;

    private final HttpClient httpClient;

    @Activate
    public MillHTTPClientProviderImpl(@Reference HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
        this.httpClient = httpClientFactory.createHttpClient("mill-lan-binding", new Client.Client(true));
        try {
            httpClient.start();
        } catch (Exception e) {
            logger.warn("Failed to start http client: {}", e.getMessage());
            throw new IllegalStateException("Could not create HttpClient", e);
        }
    }

    @Deactivate
    public void deactivate() {
        try {
            httpClient.stop();
        } catch (Exception e) {
            logger.warn("Failed to stop Mill LAN http client: {}", e.getMessage());
        }
    }

    @Override
    public ContentResponse send(
        URI uri,
        HttpMethod method,
        @Nullable Map<String, String> headers,
        @Nullable InputStream content,
        @Nullable String contentType, //Doc: Ignored in content is null
        long timeout,
        @Nullable TimeUnit timeUnit
    ) throws MillException {
        Request request = httpClient.newRequest(uri).method(method).timeout(
            timeout,
            timeUnit == null ? TimeUnit.MILLISECONDS : timeUnit
        );

        if (headers != null) {
            for (Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equals(HttpHeader.USER_AGENT.asString())) {
                    request.agent(entry.getValue());
                } else {
                    request.header(entry.getKey(), entry.getValue());
                }
            }
        }

        if (content != null) {
            try (InputStreamContentProvider inputStreamContentProvider = new InputStreamContentProvider(content)) {
                request.content(inputStreamContentProvider, contentType);
            }
        }

        try {
            logger.trace("Sending HTTP request: {}", request);
            return request.send();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new MillException(
                "Failed to send request",
                ThingStatusDetail.COMMUNICATION_ERROR,
                "Failed to send request: " + (cause == null ? "" : cause.getMessage()),
                cause
           );
        } catch (InterruptedException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new MillException("Interrupted while sending request", cause);
        } catch (TimeoutException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new MillException(
                "Timed out while trying to communicate",
                ThingStatusDetail.COMMUNICATION_ERROR,
                "Communication timeout",
                cause
           );
        }
    }
}
