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
import org.openhab.binding.milllan.internal.MillBindingConstants;
import org.openhab.binding.milllan.internal.MillUtil;
import org.openhab.core.config.core.ConfigDescription;
import org.openhab.core.config.core.ConfigDescriptionBuilder;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.ConfigDescriptionParameter.Type;
import org.openhab.core.config.core.ConfigDescriptionParameterBuilder;
import org.openhab.core.config.core.ConfigDescriptionProvider;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.thing.ThingUID;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
@Component(service = {ConfigDescriptionProvider.class, MillConfigDescriptionProvider.class})
public class MillConfigDescriptionProviderImpl implements ConfigDescriptionProvider, MillConfigDescriptionProvider {

    private final Logger logger = LoggerFactory.getLogger(MillConfigDescriptionProviderImpl.class);

    private final String uriPrefix = "thing:";
    private final String uriFilter = uriPrefix + BINDING_ID;

    // Doc must be synchronized by this
    protected final Map<URI, Map<String, ConfigDescriptionParameter>> parameters = new HashMap<>();

    @Activate
    public MillConfigDescriptionProviderImpl() {
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
                    case CONFIG_PARAM_COMMERCIAL_LOCK_MIN:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(99L))
                            .withStepSize(BigDecimal.valueOf(0.5)).withLabel("Commercial Lock Minimum Temperature")
                            .withDescription("The minimum temperature that can be set while the commercial lock is active.")
                            .withGroupName("commercialLock").withAdvanced(true).withDefault("21").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_COMMERCIAL_LOCK_MAX:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(99L))
                            .withStepSize(BigDecimal.valueOf(0.5)).withLabel("Commercial Lock Maximum Temperature")
                            .withDescription("The maximum temperature that can be set while the commercial lock is active.")
                            .withGroupName("commercialLock").withAdvanced(true).withDefault("23").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(99L)).withUnit("Cel")
                            .withStepSize(BigDecimal.valueOf(0.25)).withLabel("Drop Temperature Threshold")
                            .withDescription("The temperature drop required to trigger (activate) the open window function.")
                            .withGroupName("openWindowFunction").withAdvanced(true).withDefault("5").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.INTEGER)
                            .withMinimum(BigDecimal.valueOf(0L)).withUnit("s")
                            .withStepSize(BigDecimal.valueOf(1)).withLabel("Drop Time Range")
                            .withDescription("The time range for which a drop in temperature will be evaluated.")
                            .withGroupName("openWindowFunction").withAdvanced(true).withDefault("900").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.DECIMAL)
                            .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(99L)).withUnit("Cel")
                            .withStepSize(BigDecimal.valueOf(0.25)).withLabel("Increase Temperature Threshold")
                            .withDescription("The temperature increase required to deactivate the open window function.")
                            .withGroupName("openWindowFunction").withAdvanced(true).withDefault("3").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.INTEGER)
                            .withMinimum(BigDecimal.valueOf(0L)).withUnit("s")
                            .withStepSize(BigDecimal.valueOf(1)).withLabel("Increase Time Range")
                            .withDescription("The time range for which a increase in temperature will be evaluated.")
                            .withGroupName("openWindowFunction").withAdvanced(true).withDefault("900").build()
                        );
                        result |= true;
                        break;
                    case CONFIG_PARAM_OPEN_WINDOW_MAX_TIME:
                        thingMap.put(
                            configParameterName,
                            ConfigDescriptionParameterBuilder.create(configParameterName, Type.INTEGER)
                            .withMinimum(BigDecimal.valueOf(0L)).withUnit("s")
                            .withStepSize(BigDecimal.valueOf(1)).withLabel("Maximum Time")
                            .withDescription("The maximum time the open window function will remain active.")
                            .withGroupName("openWindowFunction").withAdvanced(true).withDefault("3600").build()
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

    public static enum DescriptionParameter {
        CONFIG_PARAM_TIMEZONE_OFFSET(
            MillBindingConstants.CONFIG_PARAM_TIMEZONE_OFFSET,
            "thing-general.config.milllan.timezone-offset.label",
            "Time Zone Offset",
            "thing-general.config.milllan.timezone-offset.description",
            "The time zone offset from UTC in minutes."
        ),
        CONFIG_PARAM_PID_KP(
            MillBindingConstants.CONFIG_PARAM_PID_KP,
            "",
            "Kp",
            "thing-general.config.milllan.pid-kp.description",
            "The PID controller's proportional gain factor K<sub>p</sub>."
        ),
        CONFIG_PARAM_PID_KI(
            MillBindingConstants.CONFIG_PARAM_PID_KI,
            "",
            "Ki",
            "thing-general.config.milllan.pid-ki.description",
            "The PID controller's integral gain factor K<sub>i</sub>."
        ),
        CONFIG_PARAM_PID_KD(
            MillBindingConstants.CONFIG_PARAM_PID_KD,
            "",
            "Kd",
            "thing-general.config.milllan.pid-kd.description",
            "The PID controller's derivative gain factor K<sub>d</sub>."
        ),
        CONFIG_PARAM_PID_KD_FILTER_N(
            MillBindingConstants.CONFIG_PARAM_PID_KD_FILTER_N,
            "thing-general.config.milllan.pid-kd-filter.label",
            "Kd Filter",
            "thing-general.config.milllan.pid-kd-filter.description",
            "The PID controller's derivative (K<sub>d</sub>) filter time coefficient."
        ),
        CONFIG_PARAM_PID_WINDUP_LIMIT_PCT(
            MillBindingConstants.CONFIG_PARAM_PID_WINDUP_LIMIT_PCT,
            "thing-general.config.milllan.pid-kd-wind-up.label",
            "Ki Wind-up Limit",
            "thing-general.config.milllan.pid-kd-wind-up.description",
            "The PID controller's wind-up limit for integral part (K<sub>i</sub>) in percent (0 to 100)."
        ),
        CONFIG_PARAM_CLOUD_COMMUNICATION(
            MillBindingConstants.CONFIG_PARAM_CLOUD_COMMUNICATION,
            "thing-general.config.milllan.cloud-communication.label",
            "Enable Cloud Communication",
            "thing-general.config.milllan.cloud-communication.description",
            "Whether cloud communication is enabled in the device. Changing this will reboot the device."
        ),
        CONFIG_PARAM_HYSTERESIS_UPPER(
            MillBindingConstants.CONFIG_PARAM_HYSTERESIS_UPPER,
            "thing-general.config.milllan.hysteresis-upper.label",
            "Hysteresis Upper",
            "thing-general.config.milllan.hysteresis-upper.description",
            "The upper limit: Set temperature + upper limit = stop heating."
        ),
        CONFIG_PARAM_HYSTERESIS_LOWER(
            MillBindingConstants.CONFIG_PARAM_HYSTERESIS_LOWER,
            "thing-general.config.milllan.hysteresis-lower.label",
            "Hysteresis Lower",
            "thing-general.config.milllan.hysteresis-lower.description",
            "The lower limit: Set temperature - lower limit = start heating."
        ),
        CONFIG_PARAM_COMMERCIAL_LOCK_MIN(
            MillBindingConstants.CONFIG_PARAM_COMMERCIAL_LOCK_MIN,
            "thing-general.config.milllan.commercial-lock-min.label",
            "Commercial Lock Minimum Temperature",
            "thing-general.config.milllan.commercial-lock-min.description",
            "The minimum temperature that can be set while the commercial lock is active."
        ),
        CONFIG_PARAM_COMMERCIAL_LOCK_MAX(
            MillBindingConstants.CONFIG_PARAM_COMMERCIAL_LOCK_MAX,
            "thing-general.config.milllan.commercial-lock-max.label",
            "Commercial Lock Maximum Temperature",
            "thing-general.config.milllan.commercial-lock-max.description",
            "The maximum temperature that can be set while the commercial lock is active."
        ),
        CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR(
            MillBindingConstants.CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR,
            "thing-general.config.milllan.open-window-drop-temp-thr.label",
            "Drop Temperature Threshold",
            "thing-general.config.milllan.open-window-drop-temp-thr.description",
            "The temperature drop required to trigger (activate) the open window function."
        ),
        CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE(
            MillBindingConstants.CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE,
            "thing-general.config.milllan.open-window-drop-time-range.label",
            "Drop Time Range",
            "thing-general.config.milllan.open-window-drop-time-range.description",
            "The time range for which a drop in temperature will be evaluated."
        ),
        CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR(
            MillBindingConstants.CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR,
            "thing-general.config.milllan.open-window-inc-temp-thr.label",
            "Increase Temperature Threshold",
            "thing-general.config.milllan.open-window-inc-temp-thr.description",
            "The temperature increase required to deactivate the open window function."
        ),
        CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE(
            MillBindingConstants.CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE,
            "thing-general.config.milllan.open-window-inc-time-range.label",
            "Increase Time Range",
            "thing-general.config.milllan.open-window-inc-time-range.description",
            "The time range for which an increase in temperature will be evaluated."
        ),
        CONFIG_PARAM_OPEN_WINDOW_MAX_TIME(
            MillBindingConstants.CONFIG_PARAM_OPEN_WINDOW_MAX_TIME,
            "thing-general.config.milllan.open-window-max-time.label",
            "Maximum Time",
            "thing-general.config.milllan.open-window-max-time.description",
            "The maximum time the open window function will remain active."
        );

        private final String name;

        private final String labelKey;
        private final String defaultLabel;
        private final String descrKey;
        private final String defaultDescr;

        private DescriptionParameter(
            String name,
            String labelKey,
            String defaultLabel,
            String descrKey,
            String defaultDescr
        ) {
            this.name = name;
            this.labelKey = labelKey;
            this.defaultLabel = defaultLabel;
            this.descrKey = descrKey;
            this.defaultDescr = defaultDescr;
        }

        public ConfigDescriptionParameter getConfigDescriptionParameter(
            @Nullable Bundle bundle,
            @Nullable TranslationProvider provider,
            @Nullable Locale locale
        ) {
            ConfigDescriptionParameterBuilder builder;
            switch (this) {
                case CONFIG_PARAM_TIMEZONE_OFFSET:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.INTEGER)
                        .withUnit("min").withMinimum(BigDecimal.valueOf(-840L))
                        .withMaximum(BigDecimal.valueOf(720L)).withStepSize(BigDecimal.valueOf(15L))
                        .withGroupName("general").withAdvanced(true).withDefault("0");
                    break;
                case CONFIG_PARAM_PID_KP:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withStepSize(BigDecimal.valueOf(1L))
                        .withGroupName("pid").withAdvanced(true).withDefault("70").withLabel(defaultLabel);

                    if (bundle == null || provider == null || locale == null) {
                        builder.withDescription(defaultDescr);
                    } else {
                        builder.withDescription(provider.getText(bundle, descrKey, defaultDescr, locale));
                    }
                    return builder.build();
                case CONFIG_PARAM_PID_KI:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withStepSize(BigDecimal.valueOf(0.01))
                        .withGroupName("pid").withAdvanced(true).withDefault("0.02").withLabel(defaultLabel);
                    if (bundle == null || provider == null || locale == null) {
                        builder.withDescription(defaultDescr);
                    } else {
                        builder.withDescription(provider.getText(bundle, descrKey, defaultDescr, locale));
                    }
                    return builder.build();
                case CONFIG_PARAM_PID_KD:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withStepSize(BigDecimal.valueOf(1L))
                        .withGroupName("pid").withAdvanced(true).withDefault("4500").withLabel(defaultLabel);
                    if (bundle == null || provider == null || locale == null) {
                        builder.withDescription(defaultDescr);
                    } else {
                        builder.withDescription(provider.getText(bundle, descrKey, defaultDescr, locale));
                    }
                    return builder.build();
                case CONFIG_PARAM_PID_KD_FILTER_N:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withStepSize(BigDecimal.valueOf(1L))
                        .withGroupName("pid").withAdvanced(true).withDefault("60");
                    break;
                case CONFIG_PARAM_PID_WINDUP_LIMIT_PCT:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(100L))
                        .withStepSize(BigDecimal.valueOf(1L)).withUnit("%")
                        .withGroupName("pid").withAdvanced(true).withDefault("95");
                    break;
                case CONFIG_PARAM_CLOUD_COMMUNICATION:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.BOOLEAN)
                        .withGroupName("general").withAdvanced(true).withDefault("false");
                    break;
                case CONFIG_PARAM_HYSTERESIS_UPPER:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(5L))
                        .withStepSize(BigDecimal.valueOf(0.25)).withGroupName("hysteresis")
                        .withAdvanced(true).withDefault("1");
                    break;
                case CONFIG_PARAM_HYSTERESIS_LOWER:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(5L))
                        .withStepSize(BigDecimal.valueOf(0.25)).withGroupName("hysteresis")
                        .withAdvanced(true).withDefault("0.5");
                    break;
                case CONFIG_PARAM_COMMERCIAL_LOCK_MIN:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(99L))
                        .withStepSize(BigDecimal.valueOf(0.5)).withGroupName("commercialLock")
                        .withAdvanced(true).withDefault("21");
                    break;
                case CONFIG_PARAM_COMMERCIAL_LOCK_MAX:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(99L))
                        .withStepSize(BigDecimal.valueOf(0.5)).withGroupName("commercialLock")
                        .withAdvanced(true).withDefault("23");
                    break;
                case CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(99L)).withUnit("Cel")
                        .withStepSize(BigDecimal.valueOf(0.25)).withGroupName("openWindowFunction")
                        .withAdvanced(true).withDefault("5");
                    break;
                case CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.INTEGER)
                        .withMinimum(BigDecimal.valueOf(0L)).withUnit("s")
                        .withStepSize(BigDecimal.valueOf(1)).withGroupName("openWindowFunction")
                        .withAdvanced(true).withDefault("900");
                    break;
                case CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.DECIMAL)
                        .withMinimum(BigDecimal.valueOf(0L)).withMaximum(BigDecimal.valueOf(99L)).withUnit("Cel")
                        .withStepSize(BigDecimal.valueOf(0.25)).withGroupName("openWindowFunction")
                        .withAdvanced(true).withDefault("3");
                    break;
                case CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.INTEGER)
                        .withMinimum(BigDecimal.valueOf(0L)).withUnit("s")
                        .withStepSize(BigDecimal.valueOf(1)).withGroupName("openWindowFunction")
                        .withAdvanced(true).withDefault("900");
                    break;
                case CONFIG_PARAM_OPEN_WINDOW_MAX_TIME:
                    builder = ConfigDescriptionParameterBuilder.create(name, Type.INTEGER)
                        .withMinimum(BigDecimal.valueOf(0L)).withUnit("s")
                        .withStepSize(BigDecimal.valueOf(1)).withGroupName("openWindowFunction")
                        .withAdvanced(true).withDefault("3600");
                    break;
                default:
                    throw new IllegalStateException("Unimplemented config description parameter: " + name());

            }
            if (bundle == null || provider == null || locale == null) {
                builder.withLabel(defaultLabel).withDescription(defaultDescr);
            } else {
                builder.withLabel(provider.getText(bundle, labelKey, defaultLabel, locale));
                builder.withDescription(provider.getText(bundle, descrKey, defaultDescr, locale));
            }
            return builder.build();
        }

        @Override
        public String toString() {
            return name;
        }

        public static DescriptionParameter typeOf(String name) {
            for (DescriptionParameter parameter : values()) {
                if (parameter.name.equals(name)) {
                    return parameter;
                }
            }
            throw new IllegalArgumentException('"' + name + "\" is not a valid " + DescriptionParameter.class.getSimpleName());
        }
    }
}
