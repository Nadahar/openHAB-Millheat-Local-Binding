package org.openhab.binding.milllan.internal;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.milllan.internal.exception.MillException;
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

    // Methods for Rules DSL rule support

    public static void sendReboot(ThingActions actions) {
        ((MillActions) actions).sendReboot();
    }
}
