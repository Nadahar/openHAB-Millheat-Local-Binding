package org.openhab.binding.milllan.internal.api.response;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.api.PredictiveHeatingType;
import org.openhab.binding.milllan.internal.api.ResponseStatus;

import com.google.gson.annotations.SerializedName;


/**
 * This class is used for deserializing JSON response objects from the "predictive-heating-type" API call.
 *
 * @author Nadahar
 */
@NonNullByDefault
public class PredictiveHeatingTypeResponse implements Response { // TODO: (Nad) Header + JavaDocs

    @SerializedName("predictive_heating_type")
    @Nullable
    private PredictiveHeatingType predictiveHeatingType;

    @Nullable
    private ResponseStatus status;

    @Nullable
    public PredictiveHeatingType getPredictiveHeatingType() {
        return predictiveHeatingType;
    }

    @Override
    @Nullable
    public ResponseStatus getStatus() {
        return status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(predictiveHeatingType, status);
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
        PredictiveHeatingTypeResponse other = (PredictiveHeatingTypeResponse) obj;
        return predictiveHeatingType == other.predictiveHeatingType && status == other.status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PredictiveHeatingTypeResponse [");
        if (predictiveHeatingType != null) {
            builder.append("predictiveHeatingType=").append(predictiveHeatingType).append(", ");
        }
        if (status != null) {
            builder.append("status=").append(status);
        }
        builder.append("]");
        return builder.toString();
    }
}
