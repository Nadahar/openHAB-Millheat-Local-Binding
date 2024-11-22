/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.milllan.internal;

import static org.openhab.binding.milllan.internal.MillBindingConstants.*;
import static org.openhab.binding.milllan.internal.MillUtil.isBlank;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.api.ControllerType;
import org.openhab.binding.milllan.internal.api.DisplayUnit;
import org.openhab.binding.milllan.internal.api.LockStatus;
import org.openhab.binding.milllan.internal.api.MillAPITool;
import org.openhab.binding.milllan.internal.api.OpenWindowStatus;
import org.openhab.binding.milllan.internal.api.OperationMode;
import org.openhab.binding.milllan.internal.api.PredictiveHeatingType;
import org.openhab.binding.milllan.internal.api.ResponseStatus;
import org.openhab.binding.milllan.internal.api.TemperatureType;
import org.openhab.binding.milllan.internal.api.request.OpenWindowParameters;
import org.openhab.binding.milllan.internal.api.response.ChildLockResponse;
import org.openhab.binding.milllan.internal.api.response.CloudCommunicationResponse;
import org.openhab.binding.milllan.internal.api.response.CommercialLockCustomizationResponse;
import org.openhab.binding.milllan.internal.api.response.CommercialLockResponse;
import org.openhab.binding.milllan.internal.api.response.ControlStatusResponse;
import org.openhab.binding.milllan.internal.api.response.ControllerTypeResponse;
import org.openhab.binding.milllan.internal.api.response.DisplayUnitResponse;
import org.openhab.binding.milllan.internal.api.response.HysteresisParametersResponse;
import org.openhab.binding.milllan.internal.api.response.LimitedHeatingPowerResponse;
import org.openhab.binding.milllan.internal.api.response.OilHeaterPowerResponse;
import org.openhab.binding.milllan.internal.api.response.OpenWindowParametersResponse;
import org.openhab.binding.milllan.internal.api.response.OperationModeResponse;
import org.openhab.binding.milllan.internal.api.response.PIDParametersResponse;
import org.openhab.binding.milllan.internal.api.response.PredictiveHeatingTypeResponse;
import org.openhab.binding.milllan.internal.api.response.Response;
import org.openhab.binding.milllan.internal.api.response.SetTemperatureResponse;
import org.openhab.binding.milllan.internal.api.response.StatusResponse;
import org.openhab.binding.milllan.internal.api.response.TemperatureCalibrationOffsetResponse;
import org.openhab.binding.milllan.internal.api.response.TimeZoneOffsetResponse;
import org.openhab.binding.milllan.internal.configuration.MillConfigDescriptionProvider;
import org.openhab.binding.milllan.internal.exception.MillException;
import org.openhab.binding.milllan.internal.exception.MillHTTPResponseException;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.core.status.ConfigStatusCallback;
import org.openhab.core.config.core.status.ConfigStatusMessage;
import org.openhab.core.config.core.status.ConfigStatusProvider;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingConfigStatusSource;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MillHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Nadahar - Initial contribution
 */
@NonNullByDefault
public class MillHandler extends BaseThingHandler implements ConfigStatusProvider {

    private final Logger logger = LoggerFactory.getLogger(MillHandler.class);

    @Nullable
    protected ConfigStatusCallback configStatusCallback;

    protected final MillConfigDescriptionProvider configDescriptionProvider;

    protected final MillHTTPClientProvider httpClientProvider;

    //Doc: Must be synced on itself
    protected final Map<String, ConfigStatusMessage> configStatusMessages = new HashMap<>();

    /** Current online state, must be synchronized on {@code this} */
    protected boolean isOnline;

    // must be synched on this
    protected boolean onlineWithError;

    protected final Object pollingLock = new Object();

    // Must be synced on pollingLock
    @Nullable
    protected ScheduledFuture<?> frequentPollTask;

    // Must be synced on pollingLock
    @Nullable
    protected ScheduledFuture<?> infrequentPollTask;

    // Must be synced on pollingLock
    @Nullable
    protected ScheduledFuture<?> offlinePollTask;

    protected final MillAPITool apiTool;

    public MillHandler(
        Thing thing,
        MillConfigDescriptionProvider configDescriptionProvider,
        MillHTTPClientProvider httpClientProvider
    ) {
        super(thing);
        this.configDescriptionProvider = configDescriptionProvider;
        this.httpClientProvider = httpClientProvider;
        this.apiTool = new MillAPITool(this.httpClientProvider);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            switch (channelUID.getId()) {
                case AMBIENT_TEMPERATURE:
                case RAW_AMBIENT_TEMPERATURE:
                case CURRENT_POWER:
                case CONTROL_SIGNAL:
                case SET_TEMPERATURE:
                case LOCK_STATUS:
                case OPEN_WINDOW_STATUS:
                case CONNECTED_CLOUD:
                    if (command instanceof RefreshType) {
                        pollControlStatus();
                    }
                    break;
                case OPERATION_MODE:
                    if (command instanceof RefreshType) {
                        pollOperationMode();
                    } else if (command instanceof StringType) {
                        setOperationMode(command.toString());
                    }
                    break;
                case TEMPERATURE_CALIBRATION_OFFSET:
                    if (command instanceof RefreshType) {
                        pollTemperatureCalibrationOffset();
                    } else if (command instanceof QuantityType) {
                        @SuppressWarnings("unchecked")
                        QuantityType<?> celsiusOffset = ((QuantityType<Temperature>) command).toUnitRelative(SIUnits.CELSIUS);
                        if (celsiusOffset == null) {
                            logger.warn("Failed to set temperature calibration offset: Could not convert {} to degrees celsius", command);
                        } else {
                            setTemperatureCalibrationOffset(celsiusOffset.toBigDecimal());
                        }
                    }
                    break;
                case COMMERCIAL_LOCK:
                    if (command instanceof RefreshType) {
                        pollCommercialLock();
                    } else if (command instanceof OnOffType) {
                        setCommercialLock(command == OnOffType.ON);
                    }
                    break;
                case CHILD_LOCK:
                    if (command instanceof RefreshType) {
                        pollChildLock();
                    } else if (command instanceof OnOffType) {
                        setChildLock(command == OnOffType.ON);
                    }
                    break;
                case DISPLAY_UNIT:
                    if (command instanceof RefreshType) {
                        pollDisplayUnit();
                    } else if (command instanceof StringType) {
                        setDisplayUnit(command.toString());
                    }
                    break;
                case NORMAL_SET_TEMPERATURE:
                    if (command instanceof RefreshType) {
                        pollSetTemperature(NORMAL_SET_TEMPERATURE, TemperatureType.NORMAL);
                    } else if (command instanceof QuantityType) {
                        @SuppressWarnings("unchecked")
                        QuantityType<?> celsiusValue = ((QuantityType<Temperature>) command).toUnit(SIUnits.CELSIUS);
                        if (celsiusValue == null) {
                            logger.warn("Failed to set \"normal\" set-temperature: Could not convert {} to degrees celsius", command);
                        } else {
                            setSetTemperature(NORMAL_SET_TEMPERATURE, TemperatureType.NORMAL, celsiusValue.toBigDecimal());
                        }
                    }
                    break;
                case COMFORT_SET_TEMPERATURE:
                    if (command instanceof RefreshType) {
                        pollSetTemperature(COMFORT_SET_TEMPERATURE, TemperatureType.COMFORT);
                    } else if (command instanceof QuantityType) {
                        @SuppressWarnings("unchecked")
                        QuantityType<?> celsiusValue = ((QuantityType<Temperature>) command).toUnit(SIUnits.CELSIUS);
                        if (celsiusValue == null) {
                            logger.warn("Failed to set \"comfort\" set-temperature: Could not convert {} to degrees celsius", command);
                        } else {
                            setSetTemperature(COMFORT_SET_TEMPERATURE, TemperatureType.COMFORT, celsiusValue.toBigDecimal());
                        }
                    }
                    break;
                case SLEEP_SET_TEMPERATURE:
                    if (command instanceof RefreshType) {
                        pollSetTemperature(SLEEP_SET_TEMPERATURE, TemperatureType.SLEEP);
                    } else if (command instanceof QuantityType) {
                        @SuppressWarnings("unchecked")
                        QuantityType<?> celsiusValue = ((QuantityType<Temperature>) command).toUnit(SIUnits.CELSIUS);
                        if (celsiusValue == null) {
                            logger.warn("Failed to set \"sleep\" set-temperature: Could not convert {} to degrees celsius", command);
                        } else {
                            setSetTemperature(SLEEP_SET_TEMPERATURE, TemperatureType.SLEEP, celsiusValue.toBigDecimal());
                        }
                    }
                    break;
                case AWAY_SET_TEMPERATURE:
                    if (command instanceof RefreshType) {
                        pollSetTemperature(AWAY_SET_TEMPERATURE, TemperatureType.AWAY);
                    } else if (command instanceof QuantityType) {
                        @SuppressWarnings("unchecked")
                        QuantityType<?> celsiusValue = ((QuantityType<Temperature>) command).toUnit(SIUnits.CELSIUS);
                        if (celsiusValue == null) {
                            logger.warn("Failed to set \"away\" set-temperature: Could not convert {} to degrees celsius", command);
                        } else {
                            setSetTemperature(AWAY_SET_TEMPERATURE, TemperatureType.AWAY, celsiusValue.toBigDecimal());
                        }
                    }
                    break;
                case LIMITED_HEATING_POWER:
                    if (command instanceof RefreshType) {
                        pollLimitedHeatingPower();
                    } else if (command instanceof Number) {
                        int i = ((Number) command).intValue();
                        if (i < 10 || i > 100) {
                            logger.warn("Failed to set limited heating power: {} is outside valid range 10-100", i);
                        } else {
                            setLimitedHeatingPower(Integer.valueOf(i));
                        }
                    }
                    break;
                case CONTROLLER_TYPE:
                    if (command instanceof RefreshType) {
                        pollControllerType();
                    } else if (command instanceof StringType) {
                        setControllerType(command.toString());
                    }
                    break;
                case PREDICTIVE_HEATING_TYPE:
                    if (command instanceof RefreshType) {
                        pollPredictiveHeatingType();
                    } else if (command instanceof StringType) {
                        setPredictiveHeatingType(command.toString());
                    }
                    break;
                case OIL_HEATER_POWER:
                    if (command instanceof RefreshType) {
                        pollOilHeaterPower();
                    } else if (command instanceof Number) {
                        int i = ((Number) command).intValue();
                        if (i != 40 && i != 60 && i != 100) {
                            logger.warn("Failed to set limited heating power: {} is outside valid range 40,60,100", i);
                        } else {
                            setOilHeaterPower(Integer.valueOf(i));
                        }
                    }
                    break;
                case OPEN_WINDOW_ACTIVE:
                    if (command instanceof RefreshType) {
                        pollOpenWindow();
                    }
                    break;
                case OPEN_WINDOW_ENABLED:
                    if (command instanceof RefreshType) {
                        pollOpenWindow();
                    } else if (command instanceof OnOffType) {
                        setOpenWindowEnabled(command == OnOffType.ON);
                    }
                    break;
            }
        } catch (MillException e) {
            setOffline(e);
        }
    }

    @Override
    public void initialize() {
        logger.trace("Initializing Thing handler {}", this);
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(new Initializer());
    }

    @Override
    public void dispose() {
        logger.trace("Disposing of Thing handler {}", this);
        configDescriptionProvider.disableDescriptions(getThing().getUID());
        clearAllConfigParameterMessages();
        ScheduledFuture<?> future;
        synchronized (pollingLock) {
            if ((future = frequentPollTask) != null) {
                future.cancel(true);
                frequentPollTask = null;
            }
            if ((future = infrequentPollTask) != null) {
                future.cancel(true);
                infrequentPollTask = null;
            }
            if ((future = offlinePollTask) != null) {
                future.cancel(true);
                offlinePollTask = null;
            }
        }
        synchronized (this) {
            isOnline = false;
            onlineWithError = false;
        }
    }

    public void pollStatus() throws MillException { //TODO: (Nad) Remember to run: mvn i18n:generate-default-translations
        StatusResponse statusResponse = apiTool.getStatus(getHostname(), getAPIKey());
        setOnline();
        Map<String, String> properties = editProperties();
        boolean changed = false;
        boolean removed = false;
        String s = statusResponse.getName();
        if (s == null || isBlank(s)) {
            removed |= properties.remove(PROPERTY_NAME) != null;
        } else if (!s.equals(properties.get(PROPERTY_NAME))) {
            properties.put(PROPERTY_NAME, s);
            changed |= true;
        }
        s = statusResponse.getCustomName();
        if (s == null || isBlank(s)) {
            removed |= properties.remove(PROPERTY_CUSTOM_NAME) != null;
        } else if (!s.equals(properties.get(PROPERTY_CUSTOM_NAME))) {
            properties.put(PROPERTY_CUSTOM_NAME, s);
            changed |= true;
        }
        s = statusResponse.getVersion();
        if (s == null || isBlank(s)) {
            removed |= properties.remove(Thing.PROPERTY_FIRMWARE_VERSION) != null;
        } else if (!s.equals(properties.get(Thing.PROPERTY_FIRMWARE_VERSION))) {
            properties.put(Thing.PROPERTY_FIRMWARE_VERSION, s);
            changed |= true;
        }
        s = statusResponse.getOperationKey();
        if (s == null || isBlank(s)) {
            removed |= properties.remove(PROPERTY_OPERATION_KEY) != null;
        } else if (!s.equals(properties.get(PROPERTY_OPERATION_KEY))) {
            properties.put(PROPERTY_OPERATION_KEY, s);
            changed |= true;
        }
        s = statusResponse.getMacAddress();
        if (s == null || isBlank(s)) {
            removed |= properties.remove(Thing.PROPERTY_MAC_ADDRESS) != null;
        } else if (!s.equals(properties.get(Thing.PROPERTY_MAC_ADDRESS))) {
            properties.put(Thing.PROPERTY_MAC_ADDRESS, s);
            changed |= true;
        }
        if (removed) {
            updateProperties(null);
        }
        if (changed || removed) {
            updateProperties(properties);
        }
    }

    public void pollControlStatus() throws MillException {
        ControlStatusResponse controlStatusResponse = apiTool.getControlStatus(getHostname(), getAPIKey());
        setOnline();
        Double d;
        if ((d = controlStatusResponse.getAmbientTemperature()) != null) {
            updateState(AMBIENT_TEMPERATURE, new QuantityType<>(d, SIUnits.CELSIUS));
        }
        if ((d = controlStatusResponse.getCurrentPower()) != null) {
            updateState(CURRENT_POWER, new QuantityType<>(d, Units.WATT));
        }
        if ((d = controlStatusResponse.getControlSignal()) != null) {
            updateState(CONTROL_SIGNAL, new QuantityType<>(d, Units.PERCENT));
        }
        if ((d = controlStatusResponse.getRawAmbientTemperature()) != null) {
            updateState(RAW_AMBIENT_TEMPERATURE, new QuantityType<>(d, SIUnits.CELSIUS));
        }
        LockStatus ls;
        if ((ls = controlStatusResponse.getLockActive()) != null) {
            updateState(LOCK_STATUS, new StringType(ls.name()));
        }
        OpenWindowStatus ows;
        if ((ows = controlStatusResponse.getOpenWindowStatus()) != null) {
            updateState(OPEN_WINDOW_STATUS, new StringType(ows.name()));
        }
        if ((d = controlStatusResponse.getSetTemperature()) != null) {
            updateState(SET_TEMPERATURE, new QuantityType<>(d, SIUnits.CELSIUS));
        }
        Boolean b;
        if ((b = controlStatusResponse.getConnectedToCloud()) != null) {
            updateState(CONNECTED_CLOUD, b.booleanValue() ? OnOffType.ON : OnOffType.OFF);
        }
        OperationMode om;
        if ((om = controlStatusResponse.getOperatingMode()) != null) {
            updateState(OPERATION_MODE, new StringType(om.name()));
        }
    }

    public void pollOperationMode() throws MillException {
        OperationModeResponse operationModeResponse = apiTool.getOperationMode(getHostname(), getAPIKey());
        setOnline();
        OperationMode om;
        if ((om = operationModeResponse.getMode()) != null) {
            updateState(OPERATION_MODE, new StringType(om.name()));
        }
    }

    public void setOperationMode(@Nullable String modeValue) throws MillException {
        OperationMode mode = OperationMode.typeOf(modeValue);
        if (mode == null) {
            logger.warn("setOperationMode() received an invalid operation mode {} - ignoring", modeValue);
            return;
        }

        Response response = apiTool.setOperationMode(getHostname(), getAPIKey(), mode);
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set operation mode to \"{}\": {}",
                mode,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    public void pollTemperatureCalibrationOffset() throws MillException {
        TemperatureCalibrationOffsetResponse calibrationOffsetResponse = apiTool.getTemperatureCalibrationOffset(getHostname(), getAPIKey());
        setOnline();
        Double d;
        if ((d = calibrationOffsetResponse.getValue()) != null) {
            updateState(TEMPERATURE_CALIBRATION_OFFSET, new QuantityType<>(d, SIUnits.CELSIUS));
        }
    }

    public void setTemperatureCalibrationOffset(BigDecimal offset) throws MillException {
        Response response = apiTool.setTemperatureCalibrationOffset(getHostname(), getAPIKey(), offset);
        pollTemperatureCalibrationOffset();
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set temperature calibration offset to \"{}\": {}",
                offset,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    public void pollCommercialLock() throws MillException {
        CommercialLockResponse commercialLockResponse = apiTool.getCommercialLock(getHostname(), getAPIKey());
        setOnline();
        Boolean b;
        if ((b = commercialLockResponse.getValue()) != null) {
            updateState(COMMERCIAL_LOCK, b.booleanValue() ? OnOffType.ON : OnOffType.OFF);
        }
    }

    public void setCommercialLock(Boolean value) throws MillException {
        Response response = apiTool.setCommercialLock(getHostname(), getAPIKey(), value);
        pollCommercialLock();
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set commercial-lock to \"{}\": {}",
                value,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    public void pollChildLock() throws MillException {
        ChildLockResponse childLockResponse = apiTool.getChildLock(getHostname(), getAPIKey());
        setOnline();
        Boolean b;
        if ((b = childLockResponse.getValue()) != null) {
            updateState(CHILD_LOCK, b.booleanValue() ? OnOffType.ON : OnOffType.OFF);
        }
    }

    public void setChildLock(Boolean value) throws MillException {
        Response response = apiTool.setChildLock(getHostname(), getAPIKey(), value);
        pollChildLock();
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set child-lock to \"{}\": {}",
                value,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    public void pollDisplayUnit() throws MillException {
        DisplayUnitResponse displayUnitResponse = apiTool.getDisplayUnit(getHostname(), getAPIKey());
        setOnline();
        DisplayUnit du;
        if ((du = displayUnitResponse.getDisplayUnit()) != null) {
            updateState(DISPLAY_UNIT, new StringType(du.name()));
        }
    }

    public void setDisplayUnit(@Nullable String unitValue) throws MillException {
        DisplayUnit displayUnit = DisplayUnit.typeOf(unitValue);
        if (displayUnit == null) {
            logger.warn("setDisplayUnit() received an invalid unit value {} - ignoring", unitValue);
            return;
        }

        Response response = apiTool.setDisplayUnit(getHostname(), getAPIKey(), displayUnit);
        pollDisplayUnit();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set display unit to \"{}\": {}",
                displayUnit,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    public void pollSetTemperature(String channel, TemperatureType temperatureType) throws MillException {
        SetTemperatureResponse setTemperatureResponse = apiTool.getSetTemperature(getHostname(), getAPIKey(), temperatureType);
        setOnline();
        BigDecimal bd;
        if ((bd = setTemperatureResponse.getSetTemperature()) != null) {
            updateState(channel, new QuantityType<>(bd, SIUnits.CELSIUS));
        }
    }

    public void setSetTemperature(String channel, TemperatureType temperatureType, BigDecimal value) throws MillException {
        Response response = apiTool.setSetTemperature(getHostname(), getAPIKey(), temperatureType, value);
        pollSetTemperature(channel, temperatureType);
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set {} set-temperature to \"{}\": {}",
                temperatureType.name(),
                value,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    public void pollLimitedHeatingPower() throws MillException {
        LimitedHeatingPowerResponse heatingPowerResponse = apiTool.getLimitedHeatingPower(getHostname(), getAPIKey());
        setOnline();
        Integer i;
        if ((i = heatingPowerResponse.getValue()) != null) {
            updateState(LIMITED_HEATING_POWER, new PercentType(i.intValue()));
        }
    }

    public void setLimitedHeatingPower(Integer value) throws MillException {
        Response response = apiTool.setLimitedHeatingPower(getHostname(), getAPIKey(), value);
        pollLimitedHeatingPower();
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set limited heating power to \"{}\": {}",
                value,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    public void pollControllerType() throws MillException {
        ControllerTypeResponse controllerTypeResponse = apiTool.getControllerType(getHostname(), getAPIKey());
        setOnline();
        ControllerType ct;
        if ((ct = controllerTypeResponse.getControllerType()) != null) {
            updateState(CONTROLLER_TYPE, new StringType(ct.name()));
        }
    }

    public void setControllerType(@Nullable String controllerTypeValue) throws MillException {
        ControllerType controllerType = ControllerType.typeOf(controllerTypeValue);
        if (controllerType == null) {
            logger.warn("setControllerType() received an invalid controller type value {} - ignoring", controllerTypeValue);
            return;
        }

        Response response = apiTool.setControllerType(getHostname(), getAPIKey(), controllerType);
        pollControllerType();
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set controller type to \"{}\": {}",
                controllerType,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    public void pollPredictiveHeatingType() throws MillException {
        PredictiveHeatingTypeResponse response = apiTool.getPredictiveHeatingType(getHostname(), getAPIKey());
        setOnline();
        PredictiveHeatingType pht;
        if ((pht = response.getPredictiveHeatingType()) != null) {
            updateState(PREDICTIVE_HEATING_TYPE, new StringType(pht.name()));
        }
    }

    public void setPredictiveHeatingType(@Nullable String typeValue) throws MillException {
        PredictiveHeatingType type = PredictiveHeatingType.typeOf(typeValue);
        if (type == null) {
            logger.warn("setPredictiveHeatingType() received an invalid predictive heating type value {} - ignoring", typeValue);
            return;
        }

        Response response = apiTool.setPredictiveHeatingType(getHostname(), getAPIKey(), type);
        pollPredictiveHeatingType();
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set predictive heating type to \"{}\": {}",
                type,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    public void pollOilHeaterPower() throws MillException {
        OilHeaterPowerResponse heatingPowerResponse = apiTool.getOilHeaterPower(getHostname(), getAPIKey());
        setOnline();
        Integer i;
        if ((i = heatingPowerResponse.getValue()) != null) {
            updateState(OIL_HEATER_POWER, new PercentType(i.intValue()));
        }
    }

    public void setOilHeaterPower(Integer value) throws MillException {
        Response response = apiTool.setLimitedHeatingPower(getHostname(), getAPIKey(), value);
        pollOilHeaterPower();
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set limited heating power to \"{}\": {}",
                value,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    @Nullable
    public Integer pollTimeZoneOffset(boolean updateConfiguration) throws MillException {
        TimeZoneOffsetResponse offset;
        try {
            offset = apiTool.getTimeZoneOffset(getHostname(), getAPIKey());
            setOnline();
        } catch (MillHTTPResponseException e) {
            // API function not implemented
            if (e.getHttpStatus() >= 400) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Thing \"{}\" doesn't seem to support timezone offset", getThing().getUID());
                }
                return null;
            }
            throw e;
        }
        Integer i;
        if ((i = offset.getOffset()) != null) {
            Thing thing = getThing();
            configDescriptionProvider.enableDescriptions(thing.getUID(), CONFIG_PARAM_TIMEZONE_OFFSET);
            if (updateConfiguration) {
                Configuration configuration = editConfiguration();
                Object object = configuration.get(CONFIG_PARAM_TIMEZONE_OFFSET);
                if (!(object instanceof Number) || ((Number) object).intValue() != i.intValue()) {
                    configuration.put(CONFIG_PARAM_TIMEZONE_OFFSET, BigDecimal.valueOf(i));
                    updateConfiguration(configuration);
                }
            }
        }
        return i;
    }

    @Nullable
    public Integer setTimeZoneOffset(Integer value, boolean updateConfiguration) throws MillException {
        Response response = apiTool.setTimeZoneOffset(getHostname(), getAPIKey(), value);
        Integer result = pollTimeZoneOffset(updateConfiguration);

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set timezone offset to \"{}\": {}",
                value,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
        return result;
    }

    @Nullable
    public PIDParametersResponse pollPIDParameters(boolean updateConfiguration) throws MillException {
        PIDParametersResponse params;
        try {
            params = apiTool.getPIDParameters(getHostname(), getAPIKey());
            setOnline();
        } catch (MillHTTPResponseException e) {
            // API function not implemented
            if (e.getHttpStatus() >= 400) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Thing \"{}\" doesn't seem to support PID parameters", getThing().getUID());
                }
                return null;
            }
            throw e;
        }
        if (params.isComplete()) {
            enablePIDDescriptions();
        }
        if (updateConfiguration) {
            Configuration configuration = editConfiguration();
            if (applyPIDParamsResponseToConfig(params, configuration)) {
                updateConfiguration(configuration);
            }
        }
        return params;
    }

    /**
     * Sends the specified PID parameters to the device and immediately queries the device for the same parameters,
     * so that the result of the operation is known.
     *
     * @param kp the proportional gain factor.
     * @param ki the integral gain factor.
     * @param kd the derivative gain factor.
     * @param kdFilterN the derivative filter time coefficient.
     * @param windupLimitPercentage the wind-up limit for integral part from 0 to 100.
     * @return The resulting {@link PIDParametersResponse} from the follow-up query.
     * @throws MillException If an error occurs during the operation.
     */
    @Nullable
    public PIDParametersResponse setPIDParameters(
        Number kp,
        Number ki,
        Number kd,
        Number kdFilterN,
        Number windupLimitPercentage,
        boolean updateConfiguration
    ) throws MillException {
        Response response = apiTool.setPIDParameters(
            getHostname(),
            getAPIKey(),
            kp.doubleValue(),
            ki.doubleValue(),
            kd.doubleValue(),
            kdFilterN.doubleValue(),
            windupLimitPercentage.doubleValue()
        );
        PIDParametersResponse result = pollPIDParameters(updateConfiguration);

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set PID parameters: {}",
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
        return result;
    }

    @Nullable
    public Boolean pollCloudCommunication(boolean updateConfiguration) throws MillException {
        CloudCommunicationResponse enabled;
        try {
            enabled = apiTool.getCloudCommunication(getHostname(), getAPIKey());
            setOnline();
        } catch (MillHTTPResponseException e) {
            // API function not implemented
            if (e.getHttpStatus() >= 400) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Thing \"{}\" doesn't seem to support cloud communication setting", getThing().getUID());
                }
                return null;
            }
            throw e;
        }
        Boolean b;
        if ((b = enabled.isEnabled()) != null) {
            Thing thing = getThing();
            configDescriptionProvider.enableDescriptions(thing.getUID(), CONFIG_PARAM_CLOUD_COMMUNICATION);
            if (updateConfiguration) {
                Configuration configuration = editConfiguration();
                Object object = configuration.get(CONFIG_PARAM_CLOUD_COMMUNICATION);
                if (!(object instanceof Boolean) || ((Boolean) object).booleanValue() != b.booleanValue()) {
                    configuration.put(CONFIG_PARAM_CLOUD_COMMUNICATION, b);
                    updateConfiguration(configuration);
                }
            }
        }
        return b;
    }

    @Nullable
    public Boolean setCloudCommunication(Boolean enabled, boolean updateConfiguration) throws MillException {
        Response response = apiTool.setCloudCommunication(getHostname(), getAPIKey(), enabled);
        Boolean result = pollCloudCommunication(updateConfiguration);

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set cloud communication to \"{}\": {}",
                enabled,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
        return result;
    }

    @Nullable
    public HysteresisParametersResponse pollHysteresisParameters(boolean updateConfiguration) throws MillException {
        HysteresisParametersResponse params;
        try {
            params = apiTool.getHysteresisParameters(getHostname(), getAPIKey());
            setOnline();
        } catch (MillHTTPResponseException e) {
            // API function not implemented
            if (e.getHttpStatus() >= 400) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Thing \"{}\" doesn't seem to support hysteresis parameters", getThing().getUID());
                }
                return null;
            }
            throw e;
        }
        if (params.isComplete()) {
            configDescriptionProvider.enableDescriptions(
                thing.getUID(),
                CONFIG_PARAM_HYSTERESIS_UPPER,
                CONFIG_PARAM_HYSTERESIS_LOWER
            );
        }
        if (updateConfiguration) {
            Configuration configuration = editConfiguration();
            Double d;
            boolean changed = false;
            if ((d = params.getUpper()) != null) {
                Object object = configuration.get(CONFIG_PARAM_HYSTERESIS_UPPER);
                if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                    configuration.put(CONFIG_PARAM_HYSTERESIS_UPPER, BigDecimal.valueOf(d));
                    changed |= true;
                }
            }
            if ((d = params.getLower()) != null) {
                Object object = configuration.get(CONFIG_PARAM_HYSTERESIS_LOWER);
                if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                    configuration.put(CONFIG_PARAM_HYSTERESIS_LOWER, BigDecimal.valueOf(d));
                    changed |= true;
                }
            }
            if (changed) {
                updateConfiguration(configuration);
            }
        }
        return params;
    }

    @Nullable
    public HysteresisParametersResponse setHysteresisParameters(
        Number upper,
        Number lower,
        boolean updateConfiguration
    ) throws MillException {
        Response response = apiTool.setHysteresisParameters(getHostname(), getAPIKey(), upper.doubleValue(), lower.doubleValue());
        HysteresisParametersResponse result = pollHysteresisParameters(updateConfiguration);

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set hysteresis parameters: {}",
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
        return result;
    }

    //Doc: Will return http status 503 if not in "independent device" mode
    @Nullable
    public ResponseStatus setTemperatureInIndependentMode(BigDecimal value) throws MillException {
        Response response = apiTool.setTemperatureInIndependentMode(getHostname(), getAPIKey(), value);
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set temperature in \"independent device\" mode to \"{}\": {}",
                value,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
        return responseStatus;
    }

    @Nullable
    public ResponseStatus setCustomName(@Nullable String customName) throws MillException {
        Response response = apiTool.setCustomName(getHostname(), getAPIKey(), customName == null ? "" : customName);
        pollStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set custom name to \"{}\": {}",
                customName,
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
        return responseStatus;
    }

    @Nullable
    public CommercialLockCustomizationResponse pollCommercialLockCustomization(boolean updateConfiguration) throws MillException {
        CommercialLockCustomizationResponse response;
        try {
            response = apiTool.getCommercialLockCustomization(getHostname(), getAPIKey());
            setOnline();
        } catch (MillHTTPResponseException e) {
            // API function not implemented
            if (e.getHttpStatus() >= 400) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Thing \"{}\" doesn't seem to support commercial lock customization", getThing().getUID());
                }
                return null;
            }
            throw e;
        }

        Boolean b = response.getEnabled();
        if (b != null) {
            updateState(COMMERCIAL_LOCK, b.booleanValue() ? OnOffType.ON : OnOffType.OFF);
        }

        if (response.isComplete()) {
            configDescriptionProvider.enableDescriptions(
                thing.getUID(),
                CONFIG_PARAM_COMMERCIAL_LOCK_MIN,
                CONFIG_PARAM_COMMERCIAL_LOCK_MAX
            );
        }
        if (updateConfiguration) {
            Configuration configuration = editConfiguration();
            Double d;
            boolean changed = false;
            if ((d = response.getMinimum()) != null) {
                Object object = configuration.get(CONFIG_PARAM_COMMERCIAL_LOCK_MIN);
                if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                    configuration.put(CONFIG_PARAM_COMMERCIAL_LOCK_MIN, BigDecimal.valueOf(d));
                    changed |= true;
                }
            }
            if ((d = response.getMaximum()) != null) {
                Object object = configuration.get(CONFIG_PARAM_COMMERCIAL_LOCK_MAX);
                if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                    configuration.put(CONFIG_PARAM_COMMERCIAL_LOCK_MAX, BigDecimal.valueOf(d));
                    changed |= true;
                }
            }
            if (changed) {
                updateConfiguration(configuration);
            }
        }
        return response;
    }

    @Nullable
    public CommercialLockCustomizationResponse setCommercialLockCustomization(
        Number min,
        Number max,
        boolean updateConfiguration
    ) throws MillException {
        Response response = apiTool.setCommercialLockCustomization(getHostname(), getAPIKey(), min.doubleValue(), max.doubleValue());
        CommercialLockCustomizationResponse result = pollCommercialLockCustomization(updateConfiguration);

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set commercial lock parameters: {}",
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
        return result;
    }

    public void pollOpenWindow() throws MillException {
        OpenWindowParametersResponse params = apiTool.getOpenWindowParameters(getHostname(), getAPIKey());
        setOnline();
        Boolean b;
        if ((b = params.getActiveNow()) != null) {
            updateState(OPEN_WINDOW_ACTIVE, OnOffType.from(b.booleanValue()));
        }
        if ((b = params.getEnabled()) != null) {
            updateState(OPEN_WINDOW_ENABLED, OnOffType.from(b.booleanValue()));
        }
    }

    public void setOpenWindowEnabled(Boolean enabled) throws MillException {
        OpenWindowParameters parameters = new OpenWindowParameters();
        parameters.setEnabled(enabled);
        String hostname = getHostname();
        String apiKey = getAPIKey();
        OpenWindowParametersResponse current = apiTool.getOpenWindowParameters(hostname, apiKey);
        if (!current.isComplete()) {
            throw new MillException(
                "Received incomplete data from \"/open-window\" API call",
                ThingStatusDetail.COMMUNICATION_ERROR
            );
        }
        Double d;
        Integer i;
        // Null-check silencers, the ifs below are always non-null
        if ((d = current.getDropTemperatureThreshold()) != null) {
            parameters.setDropTemperatureThreshold(d);
        }
        if ((i = current.getDropTimeRange()) != null) {
            parameters.setDropTimeRange(i);
        }
        if ((d = current.getIncreaseTemperatureThreshold()) != null) {
            parameters.setIncreaseTemperatureThreshold(d);
        }
        if ((i = current.getIncreaseTimeRange()) != null) {
            parameters.setIncreaseTimeRange(i);
        }
        if ((i = current.getMaxTime()) != null) {
            parameters.setMaxTime(i);
        }
        Response response = apiTool.setOpenWindowParameters(hostname, apiKey, parameters);
        pollOpenWindow();
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set open window enabled: {}",
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
    }

    @Nullable
    public OpenWindowParametersResponse pollOpenWindowParameters(boolean updateConfiguration) throws MillException {
        OpenWindowParametersResponse params;
        try {
            params = apiTool.getOpenWindowParameters(getHostname(), getAPIKey());
            setOnline();
        } catch (MillHTTPResponseException e) {
            // API function not implemented
            if (e.getHttpStatus() >= 400) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Thing \"{}\" doesn't seem to support open window parameters", getThing().getUID());
                }
                return null;
            }
            throw e;
        }
        Boolean b;
        if ((b = params.getActiveNow()) != null) {
            updateState(OPEN_WINDOW_ACTIVE, OnOffType.from(b.booleanValue()));
        }
        if ((b = params.getEnabled()) != null) {
            updateState(OPEN_WINDOW_ENABLED, OnOffType.from(b.booleanValue()));
        }

        if (params.isComplete()) {
            enableOpenWindowDescriptions();
        }
        if (updateConfiguration) {
            Configuration configuration = editConfiguration();
            if (applyOpenWindowParamsResponseToConfig(params, configuration)) {
                updateConfiguration(configuration);
            }
        }
        return params;
    }

    @Nullable
    public OpenWindowParametersResponse setOpenWindowParameters(
        Number dropTemperatureThreshold,
        Number dropTimeRange,
        Number increaseTemperatureThreshold,
        Number increaseTimeRange,
        Number maxTime,
        boolean updateConfiguration
    ) throws MillException {
        OpenWindowParameters parameters = new OpenWindowParameters();
        parameters.setDropTemperatureThreshold(dropTemperatureThreshold instanceof Double ?
            (Double) dropTemperatureThreshold :
            dropTemperatureThreshold.doubleValue()
        );
        parameters.setDropTimeRange(dropTimeRange instanceof Integer ?
            (Integer) dropTimeRange :
            dropTimeRange.intValue()
        );
        String hostname = getHostname();
        String apiKey = getAPIKey();
        OpenWindowParametersResponse result = apiTool.getOpenWindowParameters(hostname, apiKey);
        Boolean b;
        parameters.setEnabled((b = result.getEnabled()) == null ? Boolean.TRUE : b);
        parameters.setIncreaseTemperatureThreshold(increaseTemperatureThreshold instanceof Double ?
            (Double) increaseTemperatureThreshold :
            increaseTemperatureThreshold.doubleValue()
        );
        parameters.setIncreaseTimeRange(increaseTimeRange instanceof Integer ?
            (Integer) increaseTimeRange :
            increaseTimeRange.intValue()
        );
        parameters.setMaxTime(maxTime instanceof Integer ?
            (Integer) maxTime :
            maxTime.intValue()
        );
        Response response = apiTool.setOpenWindowParameters(hostname, apiKey, parameters);
        result = pollOpenWindowParameters(updateConfiguration);
        pollControlStatus();

        // Set status after polling, or it will be overwritten
        ResponseStatus responseStatus;
        if ((responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to set open-window parameters: {}",
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOnline();
        }
        return result;
    }

    public void sendReboot() throws MillException {
        Response response = null;
        try {
            response = apiTool.sendReboot(getHostname(), getAPIKey());
        } catch (MillException e) {
            if (!(e.getCause() instanceof TimeoutException)) {
                throw e;
            }
        }
        ResponseStatus responseStatus;
        if (response != null && (responseStatus = response.getStatus()) != ResponseStatus.OK) {
            logger.warn(
                "Failed to send reboot command to \"{}\": {}",
                getThing().getUID(),
                responseStatus == null ? null : responseStatus.getDescription()
            );
            setOnline(
                ThingStatusDetail.COMMUNICATION_ERROR,
                responseStatus == null ? null : responseStatus.getDescription()
            );
        } else {
            setOffline(ThingStatusDetail.CONFIGURATION_PENDING, "Device is rebooting");

            // The devices reboots relatively quickly, so let's do a couple off one-off
            // offline polls to set it online again quickly
            InetAddress[] addresses = resolveOfflineAddresses();
            if (addresses != null) {
                scheduler.schedule(new PingOffline(addresses), 8L, TimeUnit.SECONDS);
                scheduler.schedule(new PingOffline(addresses), 12L, TimeUnit.SECONDS);
            }
        }
    }

    protected void enablePIDDescriptions() {
        configDescriptionProvider.enableDescriptions(
            thing.getUID(),
            CONFIG_PARAM_PID_KP,
            CONFIG_PARAM_PID_KI,
            CONFIG_PARAM_PID_KD,
            CONFIG_PARAM_PID_KD_FILTER_N,
            CONFIG_PARAM_PID_WINDUP_LIMIT_PCT
        );
    }

    protected void disablePIDDescriptions() {
        configDescriptionProvider.disableDescriptions(
            thing.getUID(),
            CONFIG_PARAM_PID_KP,
            CONFIG_PARAM_PID_KI,
            CONFIG_PARAM_PID_KD,
            CONFIG_PARAM_PID_KD_FILTER_N,
            CONFIG_PARAM_PID_WINDUP_LIMIT_PCT
        );
    }

    protected void enableOpenWindowDescriptions() {
        configDescriptionProvider.enableDescriptions(
            thing.getUID(),
            CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR,
            CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE,
            CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR,
            CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE,
            CONFIG_PARAM_OPEN_WINDOW_MAX_TIME
        );
    }

    protected void disableOpenWindowDescriptions() {
        configDescriptionProvider.disableDescriptions(
            thing.getUID(),
            CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR,
            CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE,
            CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR,
            CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE,
            CONFIG_PARAM_OPEN_WINDOW_MAX_TIME
        );
    }

    protected boolean applyPIDParamsResponseToConfig(PIDParametersResponse parametersResponse, Configuration configuration) {
        Double d;
        Object object;
        boolean result = false;
        if ((d = parametersResponse.getKp()) != null) {
            object = configuration.get(CONFIG_PARAM_PID_KP);
            if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                configuration.put(CONFIG_PARAM_PID_KP, BigDecimal.valueOf(d));
                result |= true;
            }
        }
        if ((d = parametersResponse.getKi()) != null) {
            object = configuration.get(CONFIG_PARAM_PID_KI);
            if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                configuration.put(CONFIG_PARAM_PID_KI, BigDecimal.valueOf(d));
                result |= true;
            }
        }
        if ((d = parametersResponse.getKd()) != null) {
            object = configuration.get(CONFIG_PARAM_PID_KD);
            if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                configuration.put(CONFIG_PARAM_PID_KD, BigDecimal.valueOf(d));
                result |= true;
            }
        }
        if ((d = parametersResponse.getKdFilterN()) != null) {
            object = configuration.get(CONFIG_PARAM_PID_KD_FILTER_N);
            if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                configuration.put(CONFIG_PARAM_PID_KD_FILTER_N, BigDecimal.valueOf(d));
                result |= true;
            }
        }
        if ((d = parametersResponse.getWindupLimitPercentage()) != null) {
            object = configuration.get(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT);
            if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                configuration.put(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT, BigDecimal.valueOf(d));
                result |= true;
            }
        }
        return result;
    }

    protected boolean applyOpenWindowParamsResponseToConfig(OpenWindowParametersResponse parametersResponse, Configuration configuration) {
        Double d;
        Integer i;
        Object object;
        boolean result = false;
        if ((d = parametersResponse.getDropTemperatureThreshold()) != null) {
            object = configuration.get(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR);
            if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                configuration.put(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR, BigDecimal.valueOf(d));
                result |= true;
            }
        }
        if ((i = parametersResponse.getDropTimeRange()) != null) {
            object = configuration.get(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE);
            if (!(object instanceof Number) || ((Number) object).intValue() != i.intValue()) {
                configuration.put(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE, BigDecimal.valueOf(i));
                result |= true;
            }
        }
        if ((d = parametersResponse.getIncreaseTemperatureThreshold()) != null) {
            object = configuration.get(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR);
            if (!(object instanceof Number) || ((Number) object).doubleValue() != d.doubleValue()) {
                configuration.put(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR, BigDecimal.valueOf(d));
                result |= true;
            }
        }
        if ((i = parametersResponse.getIncreaseTimeRange()) != null) {
            object = configuration.get(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE);
            if (!(object instanceof Number) || ((Number) object).intValue() != i.intValue()) {
                configuration.put(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE, BigDecimal.valueOf(i));
                result |= true;
            }
        }
        if ((i = parametersResponse.getMaxTime()) != null) {
            object = configuration.get(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME);
            if (!(object instanceof Number) || ((Number) object).intValue() != i.intValue()) {
                configuration.put(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME, BigDecimal.valueOf(i));
                result |= true;
            }
        }
        return result;
    }

    protected synchronized boolean isOnline() {
        return isOnline;
    }

    protected void setOnline() {
        setOnline(null, null);
    }

    protected void setOnline(@Nullable ThingStatusDetail statusDetail, @Nullable String description) {
        boolean isError = statusDetail != null && statusDetail != ThingStatusDetail.NONE;
        synchronized (this) {
            if (isOnline && !isError && !onlineWithError) {
                return;
            }
            isOnline = true;
            onlineWithError = isError;
        }
        clearConfigParameterMessages(CONFIG_PARAM_HOSTNAME);

        // Clear dynamic configuration parameters and properties
        Map<String, String> properties = editProperties();
        for (String property : PROPERTIES_DYNAMIC) {
            properties.remove(property);
        }
        updateProperties(properties);
        Configuration configuration = editConfiguration();
        for (String parameter : CONFIG_DYNAMIC_PARAMETERS) {
            configuration.remove(parameter);
        }
        updateConfiguration(configuration);

        if (isError && statusDetail != null) {
            updateStatus(ThingStatus.ONLINE, statusDetail, description);
        } else {
            updateStatus(ThingStatus.ONLINE);
        }
        int refreshInterval;
        try {
            refreshInterval = getRefreshInterval();
            clearConfigParameterMessages(CONFIG_PARAM_REFRESH_INTERVAL);
        } catch (MillException e) {
            logger.error(
                "Unable to schedule polling for Mill device \"{}\" because the refresh interval is missing",
                getThing().getUID()
            );
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Missing refresh interval");
            return;
        }

        logger.debug("Mill device \"{}\" is online, starting polling", getThing().getUID());
        ScheduledFuture<?> future;
        synchronized (pollingLock) {
            if ((future = offlinePollTask) != null) {
                future.cancel(true);
                offlinePollTask = null;
            }
            if ((future = frequentPollTask) == null || future.isDone()) {
                frequentPollTask = scheduler.scheduleWithFixedDelay(new PollFrequent(), 0L, refreshInterval, TimeUnit.SECONDS);
            }
            if ((future = infrequentPollTask) == null || future.isDone()) {
                infrequentPollTask = scheduler.scheduleWithFixedDelay(new PollInfrequent(), 700L, refreshInterval * 10000L, TimeUnit.MILLISECONDS); //TODO: (Nad) Separate parameter?
            }
        }
    }

    protected void setOffline(MillException e) {
        Object object;
        if (e.getCause() instanceof ConnectException) {
            setOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Connection refused: Verify hostname and API key");
        } else if (
            e instanceof MillHTTPResponseException &&
            ((MillHTTPResponseException) e).getHttpStatus() == 500 &&
            (object = getConfig().get(CONFIG_PARAM_API_KEY)) != null &&
            object instanceof String && ((String) object).length() > 0
        ) {
            setOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Request rejected: Verify API key");
        } else {
            setOffline(e.getThingStatusDetail(), e.getThingStatusDescription());
        }
    }

    protected void setOffline(@Nullable ThingStatusDetail statusDetail, @Nullable String description) {
        boolean startOfflinePolling;
        synchronized (this) {
            startOfflinePolling = isOnline;
            isOnline = false;
        }
        if (!startOfflinePolling) {
            synchronized (pollingLock) {
                startOfflinePolling = offlinePollTask == null;
            }
        }

        // Set the status regardless of the previous online state, in case the "reason" changed
        updateStatus(
            ThingStatus.OFFLINE,
            statusDetail == null ? ThingStatusDetail.NONE : statusDetail,
            isBlank(description) ? null : description
        );

        if (startOfflinePolling) {
            configDescriptionProvider.disableDescriptions(getThing().getUID());
            clearConfigParameterMessages(CONFIG_DYNAMIC_PARAMETERS.toArray(String[]::new));
            int refreshInterval;
            try {
                refreshInterval = getRefreshInterval();
                clearConfigParameterMessages(CONFIG_PARAM_REFRESH_INTERVAL);
            } catch (MillException e) {
                refreshInterval = Integer.MIN_VALUE;
                logger.warn(
                    "Unable to poll offline Mill device \"{}\" because the configuration is missing or invalid: {}",
                    getThing().getUID(),
                    e.getMessage()
                );
                setConfigParameterMessage(ConfigStatusMessage.Builder
                    .error(CONFIG_PARAM_REFRESH_INTERVAL)
                    .withMessageKeySuffix("invalid-parameter-ex")
                    .withArguments(e.getThingStatusDescription()).build()
                );
            }
            InetAddress[] addresses = resolveOfflineAddresses();
            ScheduledFuture<?> future;
            synchronized (pollingLock) {
                if ((future = frequentPollTask) != null) {
                    future.cancel(true);
                    frequentPollTask = null;
                }
                if ((future = infrequentPollTask) != null) {
                    future.cancel(true);
                    infrequentPollTask = null;
                }
                if (addresses != null && refreshInterval > 0 && ((future = offlinePollTask) == null || future.isDone())) {
                    logger.debug("Mill device \"{}\" is offline, starting offline polling", getThing().getUID());
                    offlinePollTask = scheduler.scheduleWithFixedDelay(
                        new PingOffline(addresses),
                        1L,
                        refreshInterval,
                        TimeUnit.SECONDS
                    );
                }
            }
        }
    }

    protected String getHostname() throws MillException {
        Object object = getConfig().get(CONFIG_PARAM_HOSTNAME);
        if (!(object instanceof String)) {
            logger.warn("Configuration parameter hostname is \"{}\"", object);
            setConfigParameterMessage(ConfigStatusMessage.Builder
                .error(CONFIG_PARAM_HOSTNAME).withMessageKeySuffix("invalid-parameter").withArguments(object).build()
            );
            throw new MillException(
                "Invalid configuration: hostname must be a string",
                ThingStatusDetail.CONFIGURATION_ERROR
            );
        }
        String result = (String) object;
        if (isBlank(result)) {
            logger.warn("Configuration parameter hostname is blank");
            setConfigParameterMessage(ConfigStatusMessage.Builder
                .error(CONFIG_PARAM_HOSTNAME).withMessageKeySuffix("blank-hostname").build()
            );
            throw new MillException(
                "Invalid configuration: hostname can't be blank",
                ThingStatusDetail.CONFIGURATION_ERROR
            );
        }
        return result;
    }

    @Nullable
    protected String getAPIKey() throws MillException {
        Object object = getConfig().get(CONFIG_PARAM_API_KEY);
        if (object == null) {
            return null;
        }
        if (!(object instanceof String)) {
            logger.warn("Configuration parameter apiKey is \"{}\"", object);
            setConfigParameterMessage(ConfigStatusMessage.Builder
                .error(CONFIG_PARAM_HOSTNAME).withMessageKeySuffix("invalid-parameter").withArguments(object).build()
            );
            throw new MillException(
                "Invalid configuration: hostname must be a string",
                ThingStatusDetail.CONFIGURATION_ERROR
            );
        }
        return isBlank((String) object) ? null : (String) object;
    }

    protected int getRefreshInterval() throws MillException {
        Object object = getConfig().get(CONFIG_PARAM_REFRESH_INTERVAL);
        if (!(object instanceof Number)) {
            logger.warn("Configuration parameter refresh interval is \"{}\"", object);
            setConfigParameterMessage(ConfigStatusMessage.Builder
                .error(CONFIG_PARAM_REFRESH_INTERVAL).withMessageKeySuffix("invalid-parameter")
                .withArguments(object).build()
            );
            throw new MillException(
                "Invalid configuration: refresh interval must be a number",
                ThingStatusDetail.CONFIGURATION_ERROR
            );
        }
        int i = ((Number) object).intValue();
        if (i <= 0) {
            logger.warn("Configuration parameter refresh interval must be positive ({})", object);
            setConfigParameterMessage(ConfigStatusMessage.Builder
                .error(CONFIG_PARAM_REFRESH_INTERVAL).withMessageKeySuffix("illegal-refresh-interval").build()
            );
            throw new MillException(
                "Invalid configuration: refresh interval must be positive",
                ThingStatusDetail.CONFIGURATION_ERROR
            );
        }
        return i;
    }

    protected InetAddress @Nullable [] resolveOfflineAddresses() {
        String hostname;
        try {
            hostname = getHostname();
        } catch (MillException e) {
            logger.warn(
                "Unable to poll offline Mill device \"{}\" because the configuration is missing or invalid: {}",
                getThing().getUID(),
                e.getMessage()
            );
            setConfigParameterMessage(ConfigStatusMessage.Builder
                .error(CONFIG_PARAM_HOSTNAME)
                .withMessageKeySuffix("invalid-parameter-ex")
                .withArguments(e.getThingStatusDescription()).build()
            );
            return null;
        }
        InetAddress[] result = null;
        if (isBlank(hostname)) {
            logger.warn(
                "Unable to poll offline Mill device \"{}\" because the hostname is blank",
                getThing().getUID()
            );
            setConfigParameterMessage(ConfigStatusMessage.Builder
                .error(CONFIG_PARAM_HOSTNAME).withMessageKeySuffix("blank-hostname").build()
            );
        } else {
            try {
                result = InetAddress.getAllByName(hostname);
                clearConfigParameterMessages(CONFIG_PARAM_HOSTNAME);
            } catch (UnknownHostException e) {
                logger.warn(
                    "Unable to poll offline Mill device \"{}\" because the hostname ({}) is unresolvable: {}",
                    getThing().getUID(),
                    hostname,
                    e.getMessage()
                );
                setConfigParameterMessage(ConfigStatusMessage.Builder
                    .error(CONFIG_PARAM_HOSTNAME).withMessageKeySuffix("unresolvable-hostname").build()
                );
            }
        }
        return result;
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        Set<String> modifiedParameters = getModifiedParameters(configurationParameters);
        if (modifiedParameters.isEmpty()) {
            return;
        }

        ThingHandlerCallback callback = getCallback();
        if (callback == null) {
            logger.warn("Unable to update configuration since the callback is null");
            return;
        }
        callback.validateConfigurationParameters(getThing(), configurationParameters);

        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
        }

        boolean rebootRequired = false;
        boolean online = isOnline();
        if (modifiedParameters.contains(CONFIG_PARAM_TIMEZONE_OFFSET)) {
            if (online) {
                try {
                    int i = ((Number) configuration.get(CONFIG_PARAM_TIMEZONE_OFFSET)).intValue();
                    Integer result = setTimeZoneOffset(i, false);
                    if (result == null) {
                        logger.warn(
                            "A null timezone offset value was received when attempting to set ({})",
                            i
                        );
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_TIMEZONE_OFFSET).withMessageKeySuffix("store-failed")
                            .withArguments(Integer.valueOf(i)).build());
                        configuration.remove(CONFIG_PARAM_TIMEZONE_OFFSET);
                    } else if (result.intValue() != i) {
                        logger.warn(
                            "The device returned a different timezone offset value ({}) than " +
                            "what was attempted set ({})",
                            result,
                            i
                        );
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_TIMEZONE_OFFSET).withMessageKeySuffix("store-failed")
                                .withArguments(Integer.valueOf(i)).build());
                        configuration.put(CONFIG_PARAM_TIMEZONE_OFFSET, BigDecimal.valueOf(result.longValue()));
                    } else {
                        clearConfigParameterMessages(CONFIG_PARAM_TIMEZONE_OFFSET);
                    }
                } catch (MillException e) {
                    logger.warn(
                        "An error occurred when trying to send time zone offset to {}: {}",
                        getThing().getUID(),
                        e.getMessage()
                    );
                    // Set old value
                    Object object = getConfig().get(CONFIG_PARAM_TIMEZONE_OFFSET);
                    if (object instanceof Number) {
                        configuration.put(CONFIG_PARAM_TIMEZONE_OFFSET, object);
                    } else {
                        configuration.remove(CONFIG_PARAM_TIMEZONE_OFFSET);
                    }
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_TIMEZONE_OFFSET).withMessageKeySuffix("store-failed-ex")
                        .withArguments(e.getMessage()).build());
                }
            } else {
                configuration.remove(CONFIG_PARAM_TIMEZONE_OFFSET);
            }
        }
        if (
            modifiedParameters.contains(CONFIG_PARAM_PID_KP) ||
            modifiedParameters.contains(CONFIG_PARAM_PID_KI) ||
            modifiedParameters.contains(CONFIG_PARAM_PID_KD) ||
            modifiedParameters.contains(CONFIG_PARAM_PID_KD_FILTER_N) ||
            modifiedParameters.contains(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT)
        ) {
            if (online) {
                Object kp = configuration.get(CONFIG_PARAM_PID_KP);
                Object ki = configuration.get(CONFIG_PARAM_PID_KI);
                Object kd = configuration.get(CONFIG_PARAM_PID_KD);
                Object kdFilterN = configuration.get(CONFIG_PARAM_PID_KD_FILTER_N);
                Object windupLimit = configuration.get(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT);
                if (
                    kp instanceof Number &&
                    ki instanceof Number &&
                    kd instanceof Number &&
                    kdFilterN instanceof Number &&
                    windupLimit instanceof Number
                ) {
                    try {
                        PIDParametersResponse result = setPIDParameters(
                            (Number) kp,
                            (Number) ki,
                            (Number) kd,
                            (Number) kdFilterN,
                            (Number) windupLimit,
                            false
                        );
                        Double d;
                        if (result == null || !result.isComplete()) {
                            logger.warn("An empty or partial response was received after setting PID parameters");
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_PID_KP).withMessageKeySuffix("store-failed-pid").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_PID_KI).withMessageKeySuffix("store-failed-pid").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_PID_KD).withMessageKeySuffix("store-failed-pid").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_PID_KD_FILTER_N).withMessageKeySuffix("store-failed-pid").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT)
                                .withMessageKeySuffix("store-failed-pid").build());
                            configuration.remove(CONFIG_PARAM_PID_KP);
                            configuration.remove(CONFIG_PARAM_PID_KI);
                            configuration.remove(CONFIG_PARAM_PID_KD);
                            configuration.remove(CONFIG_PARAM_PID_KD_FILTER_N);
                            configuration.remove(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT);
                        } else if ((d = result.getKp()) != null && d.doubleValue() != ((Number) kp).doubleValue()) {
                            logger.warn(
                                "The device returned a different PID Kp ({}) than what was attempted set ({})",
                                d,
                                kp
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                    .error(CONFIG_PARAM_PID_KP).withMessageKeySuffix("store-failed")
                                    .withArguments(kp).build());
                            configuration.put(CONFIG_PARAM_PID_KP, BigDecimal.valueOf(d));
                        } else if ((d = result.getKi()) != null && d.doubleValue() != ((Number) ki).doubleValue()) {
                            logger.warn(
                                "The device returned a different PID Ki ({}) than what was attempted set ({})",
                                d,
                                ki
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                    .error(CONFIG_PARAM_PID_KI).withMessageKeySuffix("store-failed")
                                    .withArguments(ki).build());
                            configuration.put(CONFIG_PARAM_PID_KI, BigDecimal.valueOf(d));
                        } else if ((d = result.getKd()) != null && d.doubleValue() != ((Number) kd).doubleValue()) {
                            logger.warn(
                                "The device returned a different PID Kd ({}) than what was attempted set ({})",
                                d,
                                kd
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                    .error(CONFIG_PARAM_PID_KD).withMessageKeySuffix("store-failed")
                                    .withArguments(kd).build());
                            configuration.put(CONFIG_PARAM_PID_KD, BigDecimal.valueOf(d));
                        } else if ((d = result.getKdFilterN()) != null && d.doubleValue() != ((Number) kdFilterN).doubleValue()) {
                            logger.warn(
                                "The device returned a different PID Kd filter value ({}) than what was attempted set ({})",
                                d,
                                kdFilterN
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                    .error(CONFIG_PARAM_PID_KD_FILTER_N).withMessageKeySuffix("store-failed")
                                    .withArguments(kdFilterN).build());
                            configuration.put(CONFIG_PARAM_PID_KD_FILTER_N, BigDecimal.valueOf(d));
                        } else if (
                            (d = result.getWindupLimitPercentage()) != null &&
                            d.doubleValue() != ((Number) windupLimit).doubleValue()
                        ) {
                            logger.warn(
                                "The device returned a different PID windup limit ({}) than what was attempted set ({})",
                                d,
                                windupLimit
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                    .error(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT).withMessageKeySuffix("store-failed")
                                    .withArguments(windupLimit).build());
                            configuration.put(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT, BigDecimal.valueOf(d));
                        } else {
                            clearConfigParameterMessages(
                                CONFIG_PARAM_PID_KP,
                                CONFIG_PARAM_PID_KI,
                                CONFIG_PARAM_PID_KD,
                                CONFIG_PARAM_PID_KD_FILTER_N,
                                CONFIG_PARAM_PID_WINDUP_LIMIT_PCT
                            );
                        }
                    } catch (MillException e) {
                        logger.warn(
                            "An error occurred when trying to send PID paramteres to {}: {}",
                            getThing().getUID(),
                            e.getMessage()
                        );
                        // Set old value
                        Object object = getConfig().get(CONFIG_PARAM_PID_KP);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_PID_KP, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_PID_KP);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_PID_KP).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_PID_KI);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_PID_KI, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_PID_KI);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_PID_KI).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_PID_KD);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_PID_KD, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_PID_KD);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_PID_KD).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_PID_KD_FILTER_N);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_PID_KD_FILTER_N, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_PID_KD_FILTER_N);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_PID_KD_FILTER_N).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                    }
                } else {
                    logger.warn(
                        "Failed to send PID parameters to {} because some parameters are missing",
                        getThing().getUID()
                    );
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_PID_KP).withMessageKeySuffix("incomplete-parameters-pid").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_PID_KI).withMessageKeySuffix("incomplete-parameters-pid").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_PID_KD).withMessageKeySuffix("incomplete-parameters-pid").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_PID_KD_FILTER_N).withMessageKeySuffix("incomplete-parameters-pid").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT).withMessageKeySuffix("incomplete-parameters-pid").build());
                }
            } else {
                configuration.remove(CONFIG_PARAM_PID_KP);
                configuration.remove(CONFIG_PARAM_PID_KI);
                configuration.remove(CONFIG_PARAM_PID_KD);
                configuration.remove(CONFIG_PARAM_PID_KD_FILTER_N);
                configuration.remove(CONFIG_PARAM_PID_WINDUP_LIMIT_PCT);
                clearConfigParameterMessages(
                    CONFIG_PARAM_PID_KP,
                    CONFIG_PARAM_PID_KI,
                    CONFIG_PARAM_PID_KD,
                    CONFIG_PARAM_PID_KD_FILTER_N,
                    CONFIG_PARAM_PID_WINDUP_LIMIT_PCT
                );
            }
        }
        if (modifiedParameters.contains(CONFIG_PARAM_CLOUD_COMMUNICATION)) {
            if (online) {
                try {
                    boolean b = ((Boolean) configuration.get(CONFIG_PARAM_CLOUD_COMMUNICATION)).booleanValue();
                    Boolean result = setCloudCommunication(b, false);
                    if (result == null) {
                        logger.warn(
                            "A null cloud communication value was received when attempting to set ({})",
                            b
                        );
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_CLOUD_COMMUNICATION).withMessageKeySuffix("store-failed")
                            .withArguments(Boolean.valueOf(b)).build());
                        configuration.remove(CONFIG_PARAM_CLOUD_COMMUNICATION);
                    } else if (result.booleanValue() != b) {
                        logger.warn(
                            "The device returned a different cloud communication value ({}) than " +
                            "what was attempted set ({})",
                            result,
                            b
                        );
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_CLOUD_COMMUNICATION).withMessageKeySuffix("store-failed")
                                .withArguments(Boolean.valueOf(b)).build());
                        configuration.put(CONFIG_PARAM_CLOUD_COMMUNICATION, result);
                    } else {
                        clearConfigParameterMessages(CONFIG_PARAM_TIMEZONE_OFFSET);
                        rebootRequired = true;
                    }
                } catch (MillException e) {
                    logger.warn(
                        "An error occurred when trying to send cloud communication to {}: {}",
                        getThing().getUID(),
                        e.getMessage()
                    );
                    // Set old value
                    Object object = getConfig().get(CONFIG_PARAM_CLOUD_COMMUNICATION);
                    if (object instanceof Boolean) {
                        configuration.put(CONFIG_PARAM_CLOUD_COMMUNICATION, object);
                    } else {
                        configuration.remove(CONFIG_PARAM_CLOUD_COMMUNICATION);
                    }
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_CLOUD_COMMUNICATION).withMessageKeySuffix("store-failed-ex")
                        .withArguments(e.getMessage()).build());
                }
            } else {
                configuration.remove(CONFIG_PARAM_CLOUD_COMMUNICATION);
            }
        }
        if (
            modifiedParameters.contains(CONFIG_PARAM_HYSTERESIS_UPPER) ||
            modifiedParameters.contains(CONFIG_PARAM_HYSTERESIS_LOWER)
        ) {
            if (online) {
                Object upper = configuration.get(CONFIG_PARAM_HYSTERESIS_UPPER);
                Object lower = configuration.get(CONFIG_PARAM_HYSTERESIS_LOWER);
                if (upper instanceof Number && lower instanceof Number) {
                    try {
                        HysteresisParametersResponse result = setHysteresisParameters(
                            (Number) upper,
                            (Number) lower,
                            false
                        );
                        Double d;
                        if (result == null || !result.isComplete()) {
                            logger.warn("An empty or partial response was received after setting hysteresis parameters");
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_HYSTERESIS_UPPER).withMessageKeySuffix("store-failed-hysteresis").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_HYSTERESIS_LOWER).withMessageKeySuffix("store-failed-hysteresis").build());
                            configuration.remove(CONFIG_PARAM_HYSTERESIS_UPPER);
                            configuration.remove(CONFIG_PARAM_HYSTERESIS_LOWER);
                        } else if ((d = result.getUpper()) != null && d.doubleValue() != ((Number) upper).doubleValue()) {
                            logger.warn(
                                "The device returned a different upper limit ({}) than what was attempted set ({})",
                                d,
                                upper
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                    .error(CONFIG_PARAM_HYSTERESIS_UPPER).withMessageKeySuffix("store-failed")
                                    .withArguments(upper).build());
                            configuration.put(CONFIG_PARAM_HYSTERESIS_UPPER, BigDecimal.valueOf(d));
                        } else if ((d = result.getLower()) != null && d.doubleValue() != ((Number) lower).doubleValue()) {
                            logger.warn(
                                "The device returned a different lower limit ({}) than what was attempted set ({})",
                                d,
                                lower
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                    .error(CONFIG_PARAM_HYSTERESIS_LOWER).withMessageKeySuffix("store-failed")
                                    .withArguments(lower).build());
                            configuration.put(CONFIG_PARAM_HYSTERESIS_LOWER, BigDecimal.valueOf(d));
                        } else {
                            clearConfigParameterMessages(CONFIG_PARAM_HYSTERESIS_UPPER, CONFIG_PARAM_HYSTERESIS_LOWER);
                            rebootRequired = true;
                        }
                    } catch (MillException e) {
                        logger.warn(
                            "An error occurred when trying to send hysteresis paramteres to {}: {}",
                            getThing().getUID(),
                            e.getMessage()
                        );
                        // Set old value
                        Object object = getConfig().get(CONFIG_PARAM_HYSTERESIS_UPPER);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_HYSTERESIS_UPPER, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_HYSTERESIS_UPPER);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_HYSTERESIS_UPPER).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_HYSTERESIS_LOWER);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_HYSTERESIS_LOWER, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_HYSTERESIS_LOWER);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_HYSTERESIS_LOWER).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                    }
                } else {
                    logger.warn(
                        "Failed to send hysteresis parameters to {} because some parameters are missing",
                        getThing().getUID()
                    );
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_HYSTERESIS_UPPER).withMessageKeySuffix("incomplete-parameters-hysteresis").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_HYSTERESIS_LOWER).withMessageKeySuffix("incomplete-parameters-hysteresis").build());
                }
            } else {
                configuration.remove(CONFIG_PARAM_HYSTERESIS_UPPER);
                configuration.remove(CONFIG_PARAM_HYSTERESIS_LOWER);
                clearConfigParameterMessages(CONFIG_PARAM_HYSTERESIS_UPPER, CONFIG_PARAM_HYSTERESIS_LOWER);
            }
        }
        if (
            modifiedParameters.contains(CONFIG_PARAM_COMMERCIAL_LOCK_MIN) ||
            modifiedParameters.contains(CONFIG_PARAM_COMMERCIAL_LOCK_MAX)
        ) {
            if (online) {
                Object min = configuration.get(CONFIG_PARAM_COMMERCIAL_LOCK_MIN);
                Object max = configuration.get(CONFIG_PARAM_COMMERCIAL_LOCK_MAX);
                if (min instanceof Number && max instanceof Number) {
                    try {
                        CommercialLockCustomizationResponse result = setCommercialLockCustomization(
                            (Number) min,
                            (Number) max,
                            false
                        );
                        Double d;
                        if (result == null || !result.isComplete()) {
                            logger.warn("An empty or partial response was received after setting commercial lock parameters");
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_COMMERCIAL_LOCK_MIN).withMessageKeySuffix("store-failed-commercial-lock").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_COMMERCIAL_LOCK_MAX).withMessageKeySuffix("store-failed-commercial-lock").build());
                            configuration.remove(CONFIG_PARAM_COMMERCIAL_LOCK_MIN);
                            configuration.remove(CONFIG_PARAM_COMMERCIAL_LOCK_MAX);
                        } else if ((d = result.getMinimum()) != null && d.doubleValue() != ((Number) min).doubleValue()) {
                            logger.warn(
                                "The device returned a different minimum temperature ({}) than what was attempted set ({})",
                                d,
                                min
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                    .error(CONFIG_PARAM_COMMERCIAL_LOCK_MIN).withMessageKeySuffix("store-failed")
                                    .withArguments(min).build());
                            configuration.put(CONFIG_PARAM_COMMERCIAL_LOCK_MIN, BigDecimal.valueOf(d));
                        } else if ((d = result.getMaximum()) != null && d.doubleValue() != ((Number) max).doubleValue()) {
                            logger.warn(
                                "The device returned a different maximum temperature ({}) than what was attempted set ({})",
                                d,
                                max
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                    .error(CONFIG_PARAM_COMMERCIAL_LOCK_MAX).withMessageKeySuffix("store-failed")
                                    .withArguments(max).build());
                            configuration.put(CONFIG_PARAM_COMMERCIAL_LOCK_MAX, BigDecimal.valueOf(d));
                        } else {
                            clearConfigParameterMessages(CONFIG_PARAM_COMMERCIAL_LOCK_MIN, CONFIG_PARAM_COMMERCIAL_LOCK_MAX);
                        }
                    } catch (MillException e) {
                        logger.warn(
                            "An error occurred when trying to send commercial lock paramteres to {}: {}",
                            getThing().getUID(),
                            e.getMessage()
                        );
                        // Set old value
                        Object object = getConfig().get(CONFIG_PARAM_COMMERCIAL_LOCK_MIN);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_COMMERCIAL_LOCK_MIN, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_COMMERCIAL_LOCK_MIN);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_COMMERCIAL_LOCK_MIN).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_COMMERCIAL_LOCK_MAX);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_COMMERCIAL_LOCK_MAX, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_COMMERCIAL_LOCK_MAX);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_COMMERCIAL_LOCK_MAX).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                    }
                } else {
                    logger.warn(
                        "Failed to send commercial lock parameters to {} because some parameters are missing",
                        getThing().getUID()
                    );
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_COMMERCIAL_LOCK_MIN).withMessageKeySuffix("incomplete-parameters-commercial-lock").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_COMMERCIAL_LOCK_MAX).withMessageKeySuffix("incomplete-parameters-commercial-lock").build());
                }
            } else {
                configuration.remove(CONFIG_PARAM_COMMERCIAL_LOCK_MIN);
                configuration.remove(CONFIG_PARAM_COMMERCIAL_LOCK_MAX);
                clearConfigParameterMessages(CONFIG_PARAM_COMMERCIAL_LOCK_MIN, CONFIG_PARAM_COMMERCIAL_LOCK_MAX);
            }
        }
        if (
            modifiedParameters.contains(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR) ||
            modifiedParameters.contains(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE) ||
            modifiedParameters.contains(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR) ||
            modifiedParameters.contains(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE) ||
            modifiedParameters.contains(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME)
        ) {
            if (online) {
                Object dropTempThr = configuration.get(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR);
                Object dropTimeRange = configuration.get(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE);
                Object incTempThr = configuration.get(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR);
                Object incTimeRange = configuration.get(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE);
                Object maxTime = configuration.get(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME);
                if (
                    dropTempThr instanceof Number &&
                    dropTimeRange instanceof Number &&
                    incTempThr instanceof Number &&
                    incTimeRange instanceof Number &&
                    maxTime instanceof Number
                ) {
                    try {
                        OpenWindowParametersResponse result = setOpenWindowParameters(
                            (Number) dropTempThr,
                            (Number) dropTimeRange,
                            (Number) incTempThr,
                            (Number) incTimeRange,
                            (Number) maxTime,
                            false
                        );
                        Double d;
                        Integer i;
                        if (result == null || !result.isComplete()) {
                            logger.warn("An empty or partial response was received after setting open window parameters");
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR)
                                .withMessageKeySuffix("store-failed-open-window").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE)
                                .withMessageKeySuffix("store-failed-open-window").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR)
                                .withMessageKeySuffix("store-failed-open-window").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE)
                                .withMessageKeySuffix("store-failed-open-window").build());
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME)
                                .withMessageKeySuffix("store-failed-open-window").build());
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR);
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE);
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR);
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE);
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME);
                        } else if ((d = result.getDropTemperatureThreshold()) != null && d.doubleValue() != ((Number) dropTempThr).doubleValue()) {
                            logger.warn(
                                "The device returned a different drop temperature threshold ({}) than what was attempted set ({})",
                                d,
                                dropTempThr
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR).withMessageKeySuffix("store-failed")
                                .withArguments(dropTempThr).build());
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR, BigDecimal.valueOf(d));
                        } else if ((i = result.getDropTimeRange()) != null && i.intValue() != ((Number) dropTimeRange).intValue()) {
                            logger.warn(
                                "The device returned a different drop time range ({}) than what was attempted set ({})",
                                i,
                                dropTimeRange
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE).withMessageKeySuffix("store-failed")
                                .withArguments(dropTimeRange).build());
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE, BigDecimal.valueOf(i));
                        } else if ((d = result.getIncreaseTemperatureThreshold()) != null && d.doubleValue() != ((Number) incTempThr).doubleValue()) {
                            logger.warn(
                                "The device returned a different increase temperature threshold ({}) than what was attempted set ({})",
                                d,
                                incTempThr
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR).withMessageKeySuffix("store-failed")
                                .withArguments(incTempThr).build());
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR, BigDecimal.valueOf(d));
                        } else if ((i = result.getIncreaseTimeRange()) != null && i.intValue() != ((Number) incTimeRange).intValue()) {
                            logger.warn(
                                "The device returned a different increase time range value ({}) than what was attempted set ({})",
                                i,
                                incTimeRange
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE).withMessageKeySuffix("store-failed")
                                .withArguments(incTimeRange).build());
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE, BigDecimal.valueOf(i));
                        } else if ((i = result.getMaxTime()) != null && i.intValue() != ((Number) maxTime).intValue()) {
                            logger.warn(
                                "The device returned a different max time value ({}) than what was attempted set ({})",
                                i,
                                maxTime
                            );
                            setConfigParameterMessage(ConfigStatusMessage.Builder
                                .error(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME).withMessageKeySuffix("store-failed")
                                .withArguments(maxTime).build());
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME, BigDecimal.valueOf(i));
                        } else {
                            clearConfigParameterMessages(
                                CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR,
                                CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE,
                                CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR,
                                CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE,
                                CONFIG_PARAM_OPEN_WINDOW_MAX_TIME
                            );
                        }
                    } catch (MillException e) {
                        logger.warn(
                            "An error occurred when trying to send open window paramteres to {}: {}",
                            getThing().getUID(),
                            e.getMessage()
                        );
                        // Set old value
                        Object object = getConfig().get(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                        object = getConfig().get(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME);
                        if (object instanceof Number) {
                            configuration.put(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME, object);
                        } else {
                            configuration.remove(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME);
                        }
                        setConfigParameterMessage(ConfigStatusMessage.Builder
                            .error(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME).withMessageKeySuffix("store-failed-ex")
                            .withArguments(e.getMessage()).build());
                    }
                } else {
                    logger.warn(
                        "Failed to send open window parameters to {} because some parameters are missing",
                        getThing().getUID()
                    );
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR).withMessageKeySuffix("incomplete-parameters-open-window").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE).withMessageKeySuffix("incomplete-parameters-open-window").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR).withMessageKeySuffix("incomplete-parameters-open-window").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE).withMessageKeySuffix("incomplete-parameters-open-window").build());
                    setConfigParameterMessage(ConfigStatusMessage.Builder
                        .error(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME).withMessageKeySuffix("incomplete-parameters-open-window").build());
                }
            } else {
                configuration.remove(CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR);
                configuration.remove(CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE);
                configuration.remove(CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR);
                configuration.remove(CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE);
                configuration.remove(CONFIG_PARAM_OPEN_WINDOW_MAX_TIME);
                clearConfigParameterMessages(
                    CONFIG_PARAM_OPEN_WINDOW_DROP_TEMP_THR,
                    CONFIG_PARAM_OPEN_WINDOW_DROP_TIME_RANGE,
                    CONFIG_PARAM_OPEN_WINDOW_INC_TEMP_THR,
                    CONFIG_PARAM_OPEN_WINDOW_INC_TIME_RANGE,
                    CONFIG_PARAM_OPEN_WINDOW_MAX_TIME
                );
            }
        }

        if ((
                modifiedParameters.contains(CONFIG_PARAM_HOSTNAME) ||
                modifiedParameters.contains(CONFIG_PARAM_API_KEY) ||
                modifiedParameters.contains(CONFIG_PARAM_REFRESH_INTERVAL)
            ) &&
            isInitialized()
        ) {
            // Persist new configuration and reinitialize handler
            dispose();
            updateConfiguration(configuration);
            initialize();
        } else {
            // Persist new configuration and notify Thing Manager
            updateConfiguration(configuration);
            callback.configurationUpdated(getThing());
        }

        ConfigStatusCallback confStatusCallback = configStatusCallback;
        if (confStatusCallback != null) { //TODO: (Nad) Is this needed? This is already sent in updateConfiguration above
            confStatusCallback.configUpdated(new ThingConfigStatusSource(getThing().getUID().getAsString()));
        }
        if (rebootRequired) {
            scheduler.schedule(() -> {
                try {
                    sendReboot();
                } catch (MillException e) {
                    logger.warn(
                        "Failed to reboot Thing \"{}\" after a configuration change that requires a reboot: {}",
                        getThing().getUID(),
                        e.getMessage()
                    );
                }
            }, 1000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected void updateConfiguration(Configuration configuration) {
        super.updateConfiguration(configuration);
        ConfigStatusCallback confStatusCallback = configStatusCallback;
        if (confStatusCallback != null) {
            confStatusCallback.configUpdated(new ThingConfigStatusSource(getThing().getUID().getAsString()));
        }
    }

    protected void setConfigParameterMessage(ConfigStatusMessage statusMessage) {
        synchronized (configStatusMessages) {
            configStatusMessages.put(statusMessage.parameterName, statusMessage);
        }
    }

    protected void clearConfigParameterMessages(String... parameterNames) {
        synchronized (configStatusMessages) {
            for (String parameterName : parameterNames) {
                configStatusMessages.remove(parameterName);
            }
        }
    }

    protected void clearAllConfigParameterMessages() {
        synchronized (configStatusMessages) {
            configStatusMessages.clear();
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(MillActions.class);
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() { //TODO: (Nad) Localize?
        synchronized (configStatusMessages) {
            return configStatusMessages.isEmpty() ?
                Collections.emptyList() :
                new ArrayList<>(configStatusMessages.values());
        }
    }

    protected Set<String> getModifiedParameters(Map<String, Object> configurationParameters) {
        Set<String> result = new HashSet<>();
        Configuration currentConfig = getConfig();
        for (Entry<String, Object> entry : configurationParameters.entrySet()) {
            if (!Objects.equals(currentConfig.get(entry.getKey()), entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    @Override
    public boolean supportsEntity(String entityId) {
        return getThing().getUID().getAsString().equals(entityId);
    }

    @Override
    public void setConfigStatusCallback(@Nullable ConfigStatusCallback configStatusCallback) {
        this.configStatusCallback = configStatusCallback;
    }

    protected class Initializer implements Runnable {

        @Override
        public void run() {
            try {
                pollStatus();
            } catch (MillException e) {
                setOffline(e);
            }
        }
    }

    protected class PollFrequent implements Runnable {

        @Override
        public void run() {
            try {
                pollControlStatus();
                pollSetTemperature(NORMAL_SET_TEMPERATURE, TemperatureType.NORMAL);
                pollSetTemperature(COMFORT_SET_TEMPERATURE, TemperatureType.COMFORT);
                pollSetTemperature(SLEEP_SET_TEMPERATURE, TemperatureType.SLEEP);
                pollSetTemperature(AWAY_SET_TEMPERATURE, TemperatureType.AWAY);
                pollChildLock();
                pollOpenWindow();
            } catch (MillException e) {
                setOffline(e);
            }
        }
    }

    protected class PollInfrequent implements Runnable {

        @Override
        public void run() {
            try {
                pollStatus();
                pollTemperatureCalibrationOffset();
                pollDisplayUnit();
                pollLimitedHeatingPower();
                pollControllerType();
                pollPredictiveHeatingType();
//                pollOilHeaterPower(); //TODO: (Nad) Oil only
                pollTimeZoneOffset(true);
                pollPIDParameters(true);
                pollCloudCommunication(true);
                pollHysteresisParameters(true); //TODO: (Nad) Which models?
                pollCommercialLock();
                /*
                 * Commercial lock functionality seems to be completely broken, at least in firmware
                 * 0x230630. It's thus commented out here as trying to use it will only
                 * lead to frustration. If this changes in the future, a logic that
                 * looks at the firmware and enables it in working versions could be implemented> here.
                 *
                 * The disabled call is: pollCommercialLockCustomization(true);
                 * If enabled, pollCommercialLock() can be disabled, as the commercial lock state is also
                 * fetched in pollCommercialLockCustomization()
                 */
                pollOpenWindowParameters(true); //TODO: (Nad) Which models?
            } catch (MillException e) {
                setOffline(e);
            }
        }
    }

    protected class PingOffline implements Runnable {

        private final InetAddress[] addresses;

        public PingOffline(InetAddress[] addresses) {
            this.addresses = addresses;
        }

        @Override
        public void run() {
            for (InetAddress address : addresses) {
                try {
                    if (address.isReachable(1000)) {
                        scheduler.execute(() -> {
                            try {
                                pollControlStatus();
                            } catch (MillException e) {
                                setOffline(e);
                            }
                        });
                    }
                } catch (IOException e) {
                    logger.trace(
                        "An IOException occurred while pinging offline Mill device {}: {}",
                        getThing().getLabel(),
                        e.getMessage()
                    );
                }
            }
        }
    }
}
