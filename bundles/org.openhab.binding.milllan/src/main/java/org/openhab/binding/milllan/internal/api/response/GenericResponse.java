package org.openhab.binding.milllan.internal.api.response;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.api.ResponseStatus;


/**
 * This class is used for deserializing JSON response objects that only contains the "status" field.
 *
 * @author Nadahar
 */
@NonNullByDefault
public class GenericResponse implements Response { // TODO: (Nad) Header + JavaDocs

    @Nullable
    private ResponseStatus status;

    @Override
    @Nullable
    public ResponseStatus getStatus() {
        return status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status);
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
        GenericResponse other = (GenericResponse) obj;
        return status == other.status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("GenericResponse [status=").append(status).append("]");
        return builder.toString();
    }
}
