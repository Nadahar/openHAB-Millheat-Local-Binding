package org.openhab.binding.milllan.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.MillUtil;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public enum OperationMode { //TODO: (Nad) HEader + JavaDocs

    @SerializedName("Off")
    OFF(
        "The device is in off mode, it is not possible to send any comands to the device. " +
        "The device doesn't follow neither weekly program nor it is in independent mode, " +
        "nor in control individually."
    ),

    @SerializedName("Weekly program")
    WEEKLY_PROGRAM(
        "The device follows the weekly program, changing temperature by display buttons changes " +
        "the temperature of the current temperature mode."
    ),

    @SerializedName("Independent device")
    INDEPENDENT_DEVICE("The device follows the single set value, with timers enabled."),

    @SerializedName("Control individually")
    CONTROL_INDIVIDUALLY("The device follows the single set value, without any timers or weekly program."),

    @SerializedName("Invalid")
    INVALID("Invalid mode");

    private final String description;

    private OperationMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Nullable
    public static OperationMode typeOf(@Nullable String value) {
        if (value == null || MillUtil.isBlank(value)) {
            return null;
        }

        for (OperationMode entry : values()) {
            if (value.equals(entry.name())) {
                return entry;
            }
        }
        return null;
    }
}
