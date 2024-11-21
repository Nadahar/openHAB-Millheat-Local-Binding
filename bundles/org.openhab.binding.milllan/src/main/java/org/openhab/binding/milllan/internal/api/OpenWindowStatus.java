package org.openhab.binding.milllan.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public enum OpenWindowStatus { //TODO: (Nad) HEader + JavaDocs

    @SerializedName("Disabled not active now")
    DISABLED("Open Window functionality is disabled.", false),

    @SerializedName("Enabled active now")
    ENABLED_ACTIVE(
        "Open Window functionality is active, and the window is detected as open, so the heater is not heating.",
        true
    ),

    @SerializedName("Enabled not active now")
    ENABLED_INACTIVE(
        "Open Window functionality is active, but the window is not detected as open, so the heater operates normally.",
        true
    );

    private final String description;

    private final boolean enabled;

    private OpenWindowStatus(String description, boolean enabled) {
        this.description = description;
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
