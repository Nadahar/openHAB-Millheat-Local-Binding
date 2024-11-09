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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link MillBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Nadahar - Initial contribution
 */
@NonNullByDefault
public class MillBindingConstants {

    public static final String BINDING_ID = "milllan";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_PANEL_HEATER = new ThingTypeUID(BINDING_ID, "panel-heater");

    // List of all Channel ids
    public static final String AMBIENT_TEMPERATURE = "ambient-temperature";
    public static final String RAW_AMBIENT_TEMPERATURE = "raw-ambient-temperature";
    public static final String CURRENT_POWER = "current-power";
    public static final String CONTROL_SIGNAL = "control-signal";

    // Configuration parameter constants
    public static final String CONFIG_PARAM_HOSTNAME = "hostname";
    public static final String CONFIG_PARAM_REFRESH_INTERVAL = "refreshInterval";

    private MillBindingConstants() {
        // Not to be instantiated
    }
}
