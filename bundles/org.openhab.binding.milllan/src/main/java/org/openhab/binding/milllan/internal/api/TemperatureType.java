package org.openhab.binding.milllan.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public enum TemperatureType { //TODO: (Nad) HEader + JavaDocs

    @SerializedName("Off")
    OFF("The set-temperature is fixed at zero."),

    @SerializedName("Normal")
    NORMAL("The set-temperature used for \"Independent device\" and timers."),

    @SerializedName("Comfort")
    COMFORT("The set-temperature used in \"Comfort\" mode."),

    @SerializedName("Sleep")
    SLEEP("The set-temperature used in \"Sleep\" mode."),

    @SerializedName("Away")
    AWAY("The set-temperature used in \"Away\" mode."),

    @SerializedName("AlwaysHeating")
    ALWAYS_HEATING("The device always heats.");

    private final String description;

    private TemperatureType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
