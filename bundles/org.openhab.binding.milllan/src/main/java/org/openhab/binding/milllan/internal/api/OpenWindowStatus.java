package org.openhab.binding.milllan.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public enum OpenWindowStatus { //TODO: (Nad) HEader + JavaDocs

    @SerializedName("Disabled not active now")
    DISABLED("Open Window functionality is disabled."),

    @SerializedName("Enabled active now")
    ENABLED_ACTIVE(
        "Open Window functionality is active, and the window is detected as open, so the heater is not heating."
    ),

    @SerializedName("Enabled not active now")
    ENABLED_INACTIVE(
        "Open Window functionality is active, but the window is not detected as open, so the heater operates normally."
    );

    private final String description;

    private OpenWindowStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
