package org.openhab.binding.milllan.internal.api.response;

import java.math.BigDecimal;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.api.ResponseStatus;


/**
 * This class is used for deserializing JSON response objects from the "set-temperature" API call.
 *
 * @author Nadahar
 */
@NonNullByDefault
public class SetTemperatureResponse implements Response { // TODO: (Nad) Header + JavaDocs

    /**
     * The set-temperature unit.
     */
    @Nullable
    private BigDecimal value;

    @Nullable
    private ResponseStatus status;

    @Nullable
    public BigDecimal getSetTemperature() {
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
        SetTemperatureResponse other = (SetTemperatureResponse) obj;
        return status == other.status && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SetTemperatureResponse [");
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
