package org.openhab.binding.milllan.internal;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.milllan.internal.exception.MillException;

@NonNullByDefault
public interface MillHTTPClientProvider { //TODO: (Nad) Header + JavaDocs

    ContentResponse send(
        URI uri,
        HttpMethod method,
        @Nullable Map<String, String> headers,
        @Nullable InputStream content,
        @Nullable String contentType, //Doc: Ignored in content is null
        long timeout,
        @Nullable TimeUnit timeUnit
    ) throws MillException;
}
