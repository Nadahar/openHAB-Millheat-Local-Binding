package org.openhab.binding.milllan.internal.api.response;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.api.ResponseStatus;

@NonNullByDefault
public interface Response { //TODO: (Nad) Header + JavaDocs

    @Nullable
    public ResponseStatus getStatus();
}
