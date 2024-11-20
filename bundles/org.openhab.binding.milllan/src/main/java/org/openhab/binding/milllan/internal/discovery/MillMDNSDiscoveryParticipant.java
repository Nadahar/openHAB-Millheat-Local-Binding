package org.openhab.binding.milllan.internal.discovery;

import static org.openhab.binding.milllan.internal.MillBindingConstants.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Dictionary;
import java.util.Locale;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(configurationPid = "discovery.milllan")
@NonNullByDefault
public class MillMDNSDiscoveryParticipant implements MDNSDiscoveryParticipant { //TODO: (Nad) Header + JavaDocs

    private static final String SERVICE_TYPE = "_mill._tcp.local.";

    private final Logger logger = LoggerFactory.getLogger(MillMDNSDiscoveryParticipant.class);

    private boolean isAutoDiscoveryEnabled = true;

    @Activate
    public MillMDNSDiscoveryParticipant() {
    }

    @Activate
    protected void activate(ComponentContext componentContext) {
        activateOrModifyService(componentContext);
    }

    @Modified
    protected void modified(ComponentContext componentContext) {
        activateOrModifyService(componentContext);
    }

    private void activateOrModifyService(ComponentContext componentContext) {
        Dictionary<String, @Nullable Object> properties = componentContext.getProperties();
        String autoDiscoveryPropertyValue = (String) properties.get(
            DiscoveryService.CONFIG_PROPERTY_BACKGROUND_DISCOVERY
        );
        if (autoDiscoveryPropertyValue != null && !autoDiscoveryPropertyValue.isBlank()) {
            isAutoDiscoveryEnabled = Boolean.valueOf(autoDiscoveryPropertyValue);
        }
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Set.of(THING_TYPE_PANEL_HEATER); //TODO: (Nad) Add types
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    @Override
    public @Nullable DiscoveryResult createResult(ServiceInfo service) {
        if (isAutoDiscoveryEnabled) {
            ThingUID uid = getThingUID(service);
            if (uid != null) {
                DiscoveryResultBuilder builder = DiscoveryResultBuilder.create(uid);
                builder.withLabel("Mill " + service.getPropertyString(MDNS_PROPERTY_NAME))
                    .withProperty(CONFIG_PARAM_HOSTNAME, resolveIPAddress(service.getInetAddresses()))
                    .withProperty(Thing.PROPERTY_MAC_ADDRESS, formatMACAddress(uid.getId()))
                    .withRepresentationProperty(Thing.PROPERTY_MAC_ADDRESS);
                return builder.build();
            }
        }
        return null;
    }

    @Override
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
        if (service.getInetAddresses().length == 0) {
            return null;
        }

        /*
         * There is no documentation regarding what properties devices will broadcast, so this
         * logic must be adapted as devices are tested. So far, these properties have been seen:
         *
         * id = "mill_modular_<MAC ADDRESS WITHOUT SEPARATORS> - name = "Panel Heater Modular"
         */
        String id = service.getPropertyString(MDNS_PROPERTY_ID);
        if (id != null && id.startsWith("mill_modular_")) {
            String name = service.getPropertyString(MDNS_PROPERTY_NAME);
            if (name != null && name.toLowerCase(Locale.ROOT).contains("panel heater")) {
                return new ThingUID(THING_TYPE_PANEL_HEATER, id.substring(13));
            } else {
                logger.warn(
                    "Mill LAN discovered an unrecognized Mill device. The details should be reported to the binding " +
                    "developer so that it can be recognized in the future. id=\"{}\", name=\"{}\"",
                    id,
                    name
                );
            }
        }
        return null;
    }

    protected String resolveIPAddress(InetAddress[] addresses) {
        for (InetAddress address : addresses) {
            try {
                if (address.isReachable(1000)) {
                    return address.getHostAddress();
                }
            } catch (IOException e) {
                // Move on
            }
        }
        return addresses.length > 0 ? addresses[0].getHostAddress() : "";
    }

    protected String formatMACAddress(String unformatted) {
        char[] c = unformatted.toUpperCase(Locale.ROOT).toCharArray();
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < c.length - 1; i += 2) {
            sb.append(c, i, 2);
            if (i < c.length - 2) {
                sb.append(':');
            }
        }
        return sb.toString();
    }
}
