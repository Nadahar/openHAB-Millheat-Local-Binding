package org.openhab.binding.milllan.internal.configuration;

import java.net.URI;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ThingUID;

@NonNullByDefault
public interface MillConfigDescriptionProvider { //TODO: (Nad) HEader + JavaDocs

    public boolean enableDescriptions(@Nullable ThingUID uid, @Nullable String... configParameterNames);

    public boolean enableDescriptions(@Nullable URI uri, @Nullable String... configParameterNames);

    public boolean disableDescriptions(@Nullable ThingUID uid, @Nullable String... configParameterNames);

    public boolean disableDescriptions(@Nullable URI uri, @Nullable String... configParameterNames);

    public boolean disableDescriptions(@Nullable ThingUID uid);

    public boolean disableDescriptions(@Nullable URI uri);
}
