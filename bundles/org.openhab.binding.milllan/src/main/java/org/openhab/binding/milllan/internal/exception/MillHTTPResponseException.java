package org.openhab.binding.milllan.internal.exception;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.core.thing.ThingStatusDetail;

@NonNullByDefault
public class MillHTTPResponseException extends MillException { //TODO: (Nad) Header + JavaDocs

    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    public MillHTTPResponseException(int httpStatus) {
        super(httpStatus + " - " + HttpStatus.getMessage(httpStatus));
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(@Nullable String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(int httpStatus, @Nullable Throwable cause) {
        super(httpStatus + " - " + HttpStatus.getMessage(httpStatus), cause);
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(@Nullable String message, int httpStatus, @Nullable Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(int httpStatus, @Nullable ThingStatusDetail thingStatusDetail) {
        super(httpStatus + " - " + HttpStatus.getMessage(httpStatus), thingStatusDetail);
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(
        @Nullable String message,
        int httpStatus,
        @Nullable ThingStatusDetail thingStatusDetail
    ) {
        super(message, thingStatusDetail);
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(
        int httpStatus,
        @Nullable ThingStatusDetail thingStatusDetail,
        @Nullable Throwable cause
    ) {
        super(httpStatus + " - " + HttpStatus.getMessage(httpStatus), thingStatusDetail, cause);
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(
        @Nullable String message,
        int httpStatus,
        @Nullable ThingStatusDetail thingStatusDetail,
        @Nullable Throwable cause
    ) {
        super(message, thingStatusDetail, cause);
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(
        int httpStatus,
        @Nullable ThingStatusDetail thingStatusDetail,
        @Nullable String thingStatusDescription
    ) {
        super(httpStatus + " - " + HttpStatus.getMessage(httpStatus), thingStatusDetail, thingStatusDescription);
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(
        @Nullable String message,
        int httpStatus,
        @Nullable ThingStatusDetail thingStatusDetail,
        @Nullable String thingStatusDescription
    ) {
        super(message, thingStatusDetail, thingStatusDescription);
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(
        int httpStatus,
        @Nullable ThingStatusDetail thingStatusDetail,
        @Nullable String thingStatusDescription,
        @Nullable Throwable cause
    ) {
        super(
            httpStatus + " - " + HttpStatus.getMessage(httpStatus),
            thingStatusDetail,
            thingStatusDescription,
            cause
        );
        this.httpStatus = httpStatus;
    }

    public MillHTTPResponseException(
        @Nullable String message,
        int httpStatus,
        @Nullable ThingStatusDetail thingStatusDetail,
        @Nullable String thingStatusDescription,
        @Nullable Throwable cause
    ) {
        super(message, thingStatusDetail, thingStatusDescription, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
