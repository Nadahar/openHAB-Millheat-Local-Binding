package org.openhab.binding.milllan.internal.api.response;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.api.ResponseStatus;

import com.google.gson.annotations.SerializedName;


/**
 * This class is used for deserializing JSON response objects from the "commercial-lock-customization" API call.
 *
 * @author Nadahar
 */
@NonNullByDefault
public class CommercialLockCustomizationResponse implements Response { // TODO: (Nad) Header + JavaDocs

    @Nullable
    private Boolean enabled;

    @Nullable
    @SerializedName("min_allowed_temp_in_commercial_lock")
    private Double min;

    @Nullable
    @SerializedName("max_allowed_temp_in_commercial_lock")
    private Double max;

    @Nullable
    private ResponseStatus status;

    @Nullable
    public Boolean getEnabled() {
        return enabled;
    }

    @Nullable
    public Double getMinimum() {
        return min;
    }

    @Nullable
    public Double getMaximum() {
        return max;
    }

    @Nullable
    @Override
    public ResponseStatus getStatus() {
        return status;
    }

    public boolean isComplete() {
        return enabled != null && min != null && max != null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, max, min, status);
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
        CommercialLockCustomizationResponse other = (CommercialLockCustomizationResponse) obj;
        return Objects.equals(enabled, other.enabled) && Objects.equals(max, other.max)
                && Objects.equals(min, other.min) && status == other.status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CommercialLockCustomizationResponse [");
        if (enabled != null) {
            builder.append("enabled=").append(enabled).append(", ");
        }
        if (min != null) {
            builder.append("min=").append(min).append(", ");
        }
        if (max != null) {
            builder.append("max=").append(max).append(", ");
        }
        if (status != null) {
            builder.append("status=").append(status);
        }
        builder.append("]");
        return builder.toString();
    }
}
