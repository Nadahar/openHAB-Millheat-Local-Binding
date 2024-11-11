package org.openhab.binding.milllan.internal.api.response;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.api.ResponseStatus;


/**
 * This class is used for deserializing JSON response objects from the "temperature-calibration-offset" API call.
 *
 * @author Nadahar
 */
@NonNullByDefault
public class TemperatureCalibrationOffsetResponse implements Response { // TODO: (Nad) Header + JavaDocs

    /**
     * The temperature calibration offset.
     */
    @Nullable
    private Double value;

    @Nullable
    private ResponseStatus status;

    @Nullable
    public Double getValue() {
        return value;
    }

    @Override
    @Nullable
    public ResponseStatus getStatus() {
        return status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, value);
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
        TemperatureCalibrationOffsetResponse other = (TemperatureCalibrationOffsetResponse) obj;
        return status == other.status && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("TemperatureCalibrationOffsetResponse [");
        if (value != null) {
            builder.append("value=").append(value).append(", ");
        }
        if (status != null) {
            builder.append("status=").append(status);
        }
        builder.append("]");
        return builder.toString();
    }
}
