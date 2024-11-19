package org.openhab.binding.milllan.internal.api.response;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.api.ResponseStatus;

import com.google.gson.annotations.SerializedName;


/**
 * This class is used for deserializing JSON response objects from the "cloud-communication" API call.
 *
 * @author Nadahar
 */
@NonNullByDefault
public class CloudCommunicationResponse implements Response { // TODO: (Nad) Header + JavaDocs

    /** Whether cloud communication is enabled */
    @Nullable
    @SerializedName("value")
    private Boolean enabled;

    @Nullable
    private ResponseStatus status;

    @Nullable
    public Boolean isEnabled() {
        return enabled;
    }

    @Override
    @Nullable
    public ResponseStatus getStatus() {
        return status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, status);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CloudCommunicationResponse other = (CloudCommunicationResponse) obj;
        return Objects.equals(enabled, other.enabled) && status == other.status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CloudCommunicationResponse [");
        if (enabled != null) {
            builder.append("enabled=").append(enabled).append(", ");
        }
        if (status != null) {
            builder.append("status=").append(status);
        }
        builder.append("]");
        return builder.toString();
    }
}
