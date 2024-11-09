package org.openhab.binding.milllan.internal.exception;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ThingStatusDetail;

@NonNullByDefault
public class MillException extends Exception { //TODO: (Nad) Header + JavaDocs + name?

    private static final long serialVersionUID = 1L;

    @Nullable
    protected ThingStatusDetail thingStatusDetail;
    @Nullable
    protected String thingStatusDescription;

    public MillException(@Nullable String message) {
        super(message);
    }

    public MillException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public MillException(@Nullable String message, @Nullable ThingStatusDetail thingStatusDetail) {
        super(message);
        this.thingStatusDetail = thingStatusDetail;
        if (thingStatusDetail != null && thingStatusDetail != ThingStatusDetail.NONE) {
            this.thingStatusDescription = message;
        }
    }

    public MillException(
            @Nullable String message,
            @Nullable ThingStatusDetail thingStatusDetail,
            @Nullable Throwable cause
    ) {
        super(message, cause);
        this.thingStatusDetail = thingStatusDetail;
        if (thingStatusDetail != null && thingStatusDetail != ThingStatusDetail.NONE) {
            this.thingStatusDescription = message;
        }
    }

    public MillException(
            @Nullable String message,
            @Nullable ThingStatusDetail thingStatusDetail,
            @Nullable String thingStatusDescription
    ) {
        super(message);
        this.thingStatusDetail = thingStatusDetail;
        this.thingStatusDescription = thingStatusDescription;
    }

    public MillException(
            @Nullable String message,
            @Nullable ThingStatusDetail thingStatusDetail,
            @Nullable String thingStatusDescription,
            @Nullable Throwable cause
    ) {
        super(message, cause);
        this.thingStatusDetail = thingStatusDetail;
        this.thingStatusDescription = thingStatusDescription;
    }

    @Nullable
    public ThingStatusDetail getThingStatusDetail() {
        return thingStatusDetail;
    }

    @Nullable
    public String getThingStatusDescription() {
        return thingStatusDescription;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getName());
        ThingStatusDetail detail = thingStatusDetail;
        String description = thingStatusDescription;
        boolean hasDetail = detail != null && detail != ThingStatusDetail.NONE;
        if (hasDetail || (description != null && !description.isBlank())) {
            sb.append(" (");
            if (hasDetail) {
                sb.append("statusDetail: ").append(detail);
            }
            if (description != null && !description.isBlank()) {
                if (hasDetail) {
                    sb.append(", ");
                }
                sb.append("statusDescription: ").append(description);
            }
            sb.append(')');
        }
        String message = getLocalizedMessage();
        if (message != null) {
            sb.append(": ").append(message);
        }
        return sb.toString();
    }
}
