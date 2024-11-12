package org.openhab.binding.milllan.internal.api;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.MillUtil;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public enum PredictiveHeatingType { //TODO: (Nad) HEader + JavaDocs

    @SerializedName("Off")
    OFF("No predictive heating. The temperature will be change exactly with the weekly program timer."),

    @SerializedName("Simple")
    SIMPLE(
        "Simple predictive heating. The temperature will be change before the timer, with a fixed time " +
        "for each Celsius degree."
    ),

    @SerializedName("Advanced")
    ADVANCED(
        "Advanced predictive heating. The temperature will be change before the timer, with a time based " +
        "on the current room model. Model is estimated while running.d."
    );

    private final String description;

    private PredictiveHeatingType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Nullable
    public static PredictiveHeatingType typeOf(@Nullable String value) {
        if (value == null || MillUtil.isBlank(value)) {
            return null;
        }

        String mValue = value.trim().toUpperCase(Locale.ROOT);
        for (PredictiveHeatingType entry : values()) {
            if (mValue.equals(entry.name())) {
                return entry;
            }
        }
        return null;
    }
}
