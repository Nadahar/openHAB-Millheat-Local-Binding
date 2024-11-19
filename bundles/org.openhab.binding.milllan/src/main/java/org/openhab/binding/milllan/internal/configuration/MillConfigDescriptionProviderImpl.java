package org.openhab.binding.milllan.internal.configuration;

import static org.openhab.binding.milllan.internal.MillBindingConstants.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.MillUtil;
import org.openhab.core.config.core.ConfigDescription;
import org.openhab.core.config.core.ConfigDescriptionBuilder;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.ConfigDescriptionParameter.Type;
import org.openhab.core.config.core.ConfigDescriptionParameterBuilder;
import org.openhab.core.config.core.ConfigDescriptionProvider;
import org.openhab.core.config.core.ConfigOptionProvider;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
@Component(service = {ConfigDescriptionProvider.class, ConfigOptionProvider.class, MillConfigDescriptionProvider.class})
public class MillConfigDescriptionProviderImpl implements ConfigDescriptionProvider, ConfigOptionProvider, MillConfigDescriptionProvider {

    private final Logger logger = LoggerFactory.getLogger(MillConfigDescriptionProviderImpl.class);

    private final String uriPrefix = "thing:";
    private final String uriFilter = uriPrefix + BINDING_ID;

    // Doc must be synchronized by this
    protected final Map<URI, Map<String, ConfigDescriptionParameter>> parameters = new HashMap<>();

    @Activate
    public MillConfigDescriptionProviderImpl() {
    }

    @Override
    public @Nullable Collection<ParameterOption> getParameterOptions(
        URI uri,
        String param,
        @Nullable String context,
        @Nullable Locale locale
    ) {
        // TODO: (Nad) Implement or remove interface
//        logger.debug("getParameterOptions(), uri=\"{}\", param=\"{}\", context={}", uri, param, context);
        return null;
    }

    @Override
    public Collection<ConfigDescription> getConfigDescriptions(@Nullable Locale locale) {
        // TODO: (Nad) Figure out Locale
        List<ConfigDescription> result = new ArrayList<>();
        synchronized (this) {
            for (Entry<URI, Map<String, ConfigDescriptionParameter>> entry : parameters.entrySet()) {
                result.add(
                    ConfigDescriptionBuilder.create(entry.getKey())
                    .withParameters(new ArrayList<>(entry.getValue().values()))
                    .build()
                );
            }
        }
        return result;
    }

    @Override
    public @Nullable ConfigDescription getConfigDescription(URI uri, @Nullable Locale locale) { //TODO: (Nad) Figure out Locale handling
        if (!uriFilter.equals(getURIParts(uri, 2))) {
            return null;
        }

        Map<String, ConfigDescriptionParameter> thingMap;
        synchronized (this) {
            thingMap = parameters.get(uri);
            if (thingMap == null) {
                return null;
            }
            return ConfigDescriptionBuilder.create(uri)
                .withParameters(new ArrayList<>(thingMap.values()))
                .build();
        }
    }

    @Override
    public boolean enableDescriptions(@Nullable ThingUID uid, @Nullable String... configParameterNames) {
        return uid == null ?
            false :
            enableDescriptions(URI.create(uriPrefix + uid.getAsString()), configParameterNames);
    }

    @SuppressWarnings("null")
    @Override
    public boolean enableDescriptions(@Nullable URI uri, @Nullable String... configParameterNames) {
        if (uri == null || configParameterNames == null || configParameterNames.length == 0) {
            return false;
        }

        synchronized (this) {
            Map<String, ConfigDescriptionParameter> thingMap = parameters.get(uri);
            if (thingMap == null) {
                thingMap = new LinkedHashMap<>();
                parameters.put(uri, thingMap);
            }

            boolean result = false;
            for (String configParameterName : configParameterNames) {
                if (
                    configParameterName == null ||
                    MillUtil.isBlank(configParameterName) ||
                    thingMap.containsKey(configParameterName)
                ) {
                    continue;
                }
                switch (configParameterName) {
                    case CONFIG_PARAM_TIMEZONE_OFFSET:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.INTEGER)
                            .withUnit("min").withMinimum(BigDecimal.valueOf(-840L))
                            .withMaximum(BigDecimal.valueOf(720L)).withStepSize(BigDecimal.valueOf(15L))
                            .withLabel("Time Zone Offset").withDescription("The time zone offset from UTC in minutes.")
                            .withGroupName("general").withAdvanced(true).withDefault("0").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_PID_KP:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withStepSize(BigDecimal.valueOf(1L)).withLabel("Kp")
                            .withDescription("The PID controller's proportional gain factor K<sub>p</sub>.")
                            .withGroupName("pid").withAdvanced(true).withDefault("70").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_PID_KI:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withStepSize(BigDecimal.valueOf(0.01)).withLabel("Ki")
                            .withDescription("The PID controller's integral gain factor K<sub>i</sub>.")
                            .withGroupName("pid").withAdvanced(true).withDefault("0.02").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_PID_KD:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withStepSize(BigDecimal.valueOf(1L)).withLabel("Kd")
                            .withDescription("The PID controller's derivative gain factor K<sub>d</sub>.")
                            .withGroupName("pid").withAdvanced(true).withDefault("4500").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_PID_KD_FILTER_N:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withStepSize(BigDecimal.valueOf(1L)).withLabel("Kd filter")
                            .withDescription("The PID controller's derivative (K<sub>d</sub>) filter time coefficient.")
                            .withGroupName("pid").withAdvanced(true).withDefault("60").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_PID_WINDUP_LIMIT_PCT:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(100L))
                            .withStepSize(BigDecimal.valueOf(1L)).withLabel("Ki Wind-up Limit").withUnit("%")
                            .withDescription("The PID controller's wind-up limit for integral part (K<sub>i</sub>) in percent (0 to 100).")
                            .withGroupName("pid").withAdvanced(true).withDefault("95").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_CLOUD_COMMUNICATION:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.BOOLEAN)
                            .withLabel("Enable Cloud Communication")
                            .withDescription("Whether cloud communication is enabled in the device. Changing this will reboot the device.")
                            .withGroupName("general").withAdvanced(true).withDefault("false").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_HYSTERESIS_UPPER:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(5L))
                            .withStepSize(BigDecimal.valueOf(0.25)).withLabel("Hysteresis Upper")
                            .withDescription("The upper limit: Set temperature + upper limit = stop heating.")
                            .withGroupName("hysteresis").withAdvanced(true).withDefault("1").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_HYSTERESIS_LOWER:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(5L))
                            .withStepSize(BigDecimal.valueOf(0.25)).withLabel("Hysteresis Lower")
                            .withDescription("The lower limit: Set temperature - lower limit = start heating.")
                            .withGroupName("hysteresis").withAdvanced(true).withDefault("0.5").build()
                        );
                        result |= true;
                        break;
                    default:
                        logger.warn(
                            "{} was asked to describe an unimlemented configuration parameter {}",
                            getClass().getSimpleName(),
                            configParameterName
                        );
                        break;
                }
            }
            return result;
        }
    }

    @Override
    public boolean disableDescriptions(@Nullable ThingUID uid, @Nullable String... configParameterNames) {
        return uid == null ?
            false :
            disableDescriptions(URI.create(uriPrefix + uid.getAsString()), configParameterNames);
    }

    @SuppressWarnings("null")
    @Override
    public boolean disableDescriptions(@Nullable URI uri, @Nullable String... configParameterNames) {
        if (uri == null || configParameterNames == null || configParameterNames.length == 0) {
            return false;
        }

        synchronized (this) {
            Map<String, ConfigDescriptionParameter> thingMap = parameters.get(uri);
            if (thingMap == null) {
                return false;
            }
            boolean result = false;
            for (String configParameterName : configParameterNames) {
                if (configParameterName == null || MillUtil.isBlank(configParameterName)) {
                    continue;
                }
                if (thingMap.remove(configParameterName) != null) {
                    result |= true;
                }
            }
            if (thingMap.isEmpty()) {
                parameters.remove(uri);
            }
            return result;
        }
    }

    @Override
    public boolean disableDescriptions(@Nullable ThingUID uid) {
        return uid == null ? false : disableDescriptions(URI.create(uriPrefix + uid.getAsString()));
    }

    @Override
    public synchronized boolean disableDescriptions(@Nullable URI uri) {
        return uri == null ? false : parameters.remove(uri) != null;
    }

    protected String getURIParts(URI uri, int parts) {
        if (parts == 0) {
            return "";
        }
        int remaining = parts;
        char[] fullURI = uri.toString().toCharArray();
        for (int i = 0; i < fullURI.length - 1; i++) {
            if (fullURI[i] == ':') {
                remaining--;
                if (remaining == 0) {
                    return String.copyValueOf(fullURI, 0, i);
                }
            }
        }
        return "";
    }
}
