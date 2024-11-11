package org.openhab.binding.milllan.internal.api;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.MillUtil;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public enum ControllerType { //TODO: (Nad) HEader + JavaDocs

    @SerializedName("pid")
    PID("PID"),

    @SerializedName("hysteresis_or_slow_pid")
    SLOW_PID("Slow PID"),

    @SerializedName("unknown")
    UNKNOWN("Unknown");

    private final String description;

    private ControllerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Nullable
    public static ControllerType typeOf(@Nullable String value) {
        if (value == null || MillUtil.isBlank(value)) {
            return null;
        }

        String mValue = value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+|-", "_");
        for (ControllerType entry : values()) {
            if (mValue.equals(entry.name())) {
                return entry;
            }
        }
        return null;
    }
}
