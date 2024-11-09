package org.openhab.binding.milllan.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public enum ResponseStatus { //TODO: (Nad) HEader + JavaDocs
    @SerializedName("ok")
    OK("The request was successful"),

    @SerializedName("Failed to parse message body")
    PARSE_FAILED("The request body is incorrect or the parameters are invalid"),

    @SerializedName("Failed to execute the request")
    REQUEST_FAILED("There was a problem with the processing request"),

    @SerializedName("Length of request body too long")
    TOO_LONG("The length of the request body is too long"),

    @SerializedName("Failed to create response body")
    RESPONSE_FAILED("there was a problem with the processing respone");

    private final String description;

    private ResponseStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
