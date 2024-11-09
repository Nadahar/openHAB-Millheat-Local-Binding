package org.openhab.binding.milllan.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public enum LockStatus { //TODO: (Nad) HEader + JavaDocs
    @SerializedName("No lock")
    NO_LOCK("All buttons are active."),

    @SerializedName("Child lock")
    CHILD_LOCK("The buttons on the device are not active."),

    @SerializedName("Commercial lock")
    COMMERCIAL_LOCK("The buttons on the device have limited functionality.");

    private final String description;

    private LockStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
