package org.openhab.binding.milllan.internal;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.exception.MillException;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.ActionOutput;
import org.openhab.core.automation.annotation.ActionOutputs;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(scope = ServiceScope.PROTOTYPE, service = MillActions.class)
@ThingActionsScope(name = "millian") //TODO: (Nad) One scope per thing type?
@NonNullByDefault
public class MillActions implements ThingActions { //TODO: (Nad) Header + Javadocs

    private final Logger logger = LoggerFactory.getLogger(MillActions.class);

    @Nullable
    protected MillHandler handler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        this.handler = (MillHandler) handler;
    }

    @Override
    @Nullable
    public ThingHandler getThingHandler() {
        return handler;
    }

    @ActionOutputs(value = {@ActionOutput(name = "result", type = "java.lang.String")})
    @RuleAction(label = "@text/actions.milllan.send-reboot.label", description = "@text/actions.milllan.send-reboot.description")
    public @ActionOutput(name = "result", type = "java.lang.String") Map<String, Object> sendReboot() {
        Map<String, Object> result = new HashMap<>();
        MillHandler handlerInst = handler;
        if (handlerInst == null) {
            logger.warn("Call to sendReboot Action failed because the handler was null");
            result.put("result", "Failed: The handler is null");
            return result;
        }
        try {
            handlerInst.sendReboot();
            result.put("result", "The device is rebooting.");
            return result;
        } catch (MillException e) {
            logger.warn(
                "Failed to execute sendReboot Action on Thing {}: {}",
                handlerInst.getThing().getUID(),
                e.getMessage()
            );
            result.put("result", "Failed to execute sendReboot Action: " + e.getMessage());
            return result;
        }
    }

    @ActionOutputs(value = {@ActionOutput(name = "result", type = "java.lang.String")})
    @RuleAction(label = "@text/actions.milllan.set-timezone-offset.label", description = "@text/actions.milllan.set-timezone-offset.description")
    public @ActionOutput(name = "result", type = "java.lang.String") Map<String, Object> setTimeZoneOffset(
        @Nullable @ActionInput(
            name = "offset",
            label = "@text/actions-input.milllan.set-timezone-offset.offset.label",
            description = "@text/actions-input.milllan.set-timezone-offset.offset.description",
            required = true
        ) Integer offset
    ) {
        Map<String, Object> result = new HashMap<>();
        MillHandler handlerInst = handler;
        if (handlerInst == null) {
            logger.warn("Call to setTimeZoneOffset Action failed because the handler was null");
            result.put("result", "Failed: The handler is null");
            return result;
        }
        if (offset == null) {
            logger.warn("Call to setTimeZoneOffset Action failed because the offset was null");
            result.put("result", "The time zone offset must be specified!");
            return result;
        }
        try {
            handlerInst.setTimeZoneOffset(offset, true);
            result.put("result", "The time zone offset was set to " + offset + '.');
            return result;
        } catch (MillException e) {
            logger.warn(
                "Failed to execute setTimeZoneOffset Action on Thing {}: {}",
                handlerInst.getThing().getUID(),
                e.getMessage()
            );
            result.put("result", "Failed to execute setTimeZoneOffset Action: " + e.getMessage());
            return result;
        }
    }

    @ActionOutputs(value = {@ActionOutput(name = "result", type = "java.lang.String")})
    @RuleAction(label = "@text/actions.milllan.set-pid-parameters.label", description = "@text/actions.milllan.set-pid-parameters.description")
    public @ActionOutput(name = "result", type = "java.lang.String") Map<String, Object> setPIDParameters(
        @Nullable @ActionInput(
            name = "kp",
            label = "Kp",
            description = "@text/actions-input.milllan.set-pid-parameters.kp.description",
            required = true
        ) Double kp,
        @Nullable @ActionInput(
            name = "ki",
            label = "Ki",
            description = "@text/actions-input.milllan.set-pid-parameters.ki.description",
            required = true
        ) Double ki,
        @Nullable @ActionInput(
            name = "kd",
            label = "Kd",
            description = "@text/actions-input.milllan.set-pid-parameters.kd.description",
            required = true
        ) Double kd,
        @Nullable @ActionInput(
            name = "kdFilterN",
            label = "@text/actions-input.milllan.set-pid-parameters.kd-filter.label",
            description = "@text/actions-input.milllan.set-pid-parameters.kd-filter.description",
            required = true
        ) Double kdFilterN,
        @Nullable @ActionInput(
            name = "windupLimitPct",
            label = "@text/actions-input.milllan.set-pid-parameters.windup-limit.label",
            description = "@text/actions-input.milllan.set-pid-parameters.windup-limit.description",
            required = true
        ) Double windupLimitPct
    ) {
        Map<String, Object> result = new HashMap<>();
        MillHandler handlerInst = handler;
        if (handlerInst == null) {
            logger.warn("Call to setPIDParameters Action failed because the handler was null");
            result.put("result", "Failed: The handler is null");
            return result;
        }
        if (kp == null || ki == null || kd == null || kdFilterN == null || windupLimitPct == null) {
            logger.warn("Call to setPIDParameters Action failed because some parameters were null");
            result.put("result", "All PID parameters must be specified!");
            return result;
        }
        try {
            handlerInst.setPIDParameters(kp, ki, kd, kdFilterN, windupLimitPct, true);
            result.put("result", "The PID parameters were set.");
            return result;
        } catch (MillException e) {
            logger.warn(
                "Failed to execute setPIDParameters Action on Thing {}: {}",
                handlerInst.getThing().getUID(),
                e.getMessage()
            );
            result.put("result", "Failed to execute setPIDParameters Action: " + e.getMessage());
            return result;
        }
    }

    // Methods for Rules DSL rule support

    public static void sendReboot(ThingActions actions) {
        ((MillActions) actions).sendReboot();
    }

    public static void setTimeZoneOffset(ThingActions actions, Integer offset) {
        ((MillActions) actions).setTimeZoneOffset(offset);
    }

    public static void setPIDParameters(
        ThingActions actions,
        Double kp,
        Double ki,
        Double kd,
        Double kdFilterN,
        Double windupLimitPct
    ) {
        ((MillActions) actions).setPIDParameters(kp, ki, kd, kdFilterN, windupLimitPct);
    }
}
