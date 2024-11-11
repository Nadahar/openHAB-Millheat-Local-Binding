package org.openhab.binding.milllan.internal.api;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.MillUtil;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public enum DisplayUnit { //TODO: (Nad) HEader + JavaDocs

    @SerializedName("Celsius")
    CELSIUS("Celsius"),

    /** Fahrenheit (note that there's a spelling error in the API) */
    @SerializedName("Farenheit")
    FAHRENHEIT("Fahrenheit");

    private final String description;

    private DisplayUnit(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Nullable
    public static DisplayUnit typeOf(@Nullable String value) {
        if (value == null || MillUtil.isBlank(value)) {
            return null;
        }

        String mValue = value.toUpperCase(Locale.ROOT);
        for (DisplayUnit entry : values()) {
            if (mValue.equals(entry.name())) {
                return entry;
            }
        }
        return null;
    }
}
