package com.pi4j.gpio.extension.base;

/*-
 * #%L
 * **********************************************************************
 * ORGANIZATION  :  Pi4J
 * PROJECT       :  Pi4J :: GPIO Extension
 * FILENAME      :  ExtensionProvider.java
 *
 * This file is part of the Pi4J project. More information about
 * this project can be found here:  http://www.pi4j.com/
 * **********************************************************************
 * %%
 * Copyright (C) 2012 - 2016 Pi4J
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.util.concurrent.ScheduledExecutorService;

import com.pi4j.io.gpio.GpioProvider;

public interface ExtensionProvider extends GpioProvider {

    // minimum allowed background monitoring interval in milliseconds
    int MIN_MONITOR_INTERVAL = 50;

    // default background monitoring interval in milliseconds
    int DEFAULT_MONITOR_INTERVAL = 250;

    MonitorIntervalType DEFAULT_MONITOR_INTERVAL_TYPE = MonitorIntervalType.Delay;

    /**
     * Defines how the schedule use the delay setting
     * <p>
     * <li>{@link #Delay} The delay value defines the waiting time from thread finish to thread start</li>
     * <li>{@link #Rate} The thread is scheduled with a fixed rate</li>
     * </p>
     *
     * @author Günter Goerlich
     *
     *
     */
    public enum MonitorIntervalType {
        /**
         * The delay value defines the waiting time from thread finish to thread start
         */
        Delay,

        /**
         * The thread is scheduled with a fixed rate
         */
        Rate
    }

    /**
     * Get the background monitoring thread's enabled state.
     *
     * @return monitoring enabled or disabled state
     */
    boolean getMonitorEnabled();

    /**
     * Set the background monitoring thread's enabled state.
     *
     * @param enabled monitoring enabled or disabled state
     * @deprecated use the monitorEnable monitorDisable methods
     */
    @Deprecated
    void setMonitorEnabled(boolean enabled);

    /**
     * Get the background monitoring thread's rate of data acquisition. (in milliseconds)
     *
     * The default interval is 250 milliseconds.
     *
     * @return monitoring interval in milliseconds
     */

    int getMonitorInterval();

    /**
     * Change the background monitoring thread's rate of data acquisition. (in milliseconds)
     *
     * The default interval is 250 milliseconds.
     *
     * @param monitorInterval
     */
    void setMonitorInterval(int monitorInterval);

    /**
     * Change the background monitoring thread's rate type
     *
     * The default interval type is Delay.
     *
     * @param monitorIntervalType
     */
    void setMonitorIntervalType(MonitorIntervalType monitorIntervalType);

    /**
     * Get the background monitoring thread's rate type of data acquisition.
     *
     * The default type is Delay.
     *
     *
     * @return monitoring interval type
     */
    MonitorIntervalType getMonitorIntervalType();

    /**
     * Enable the polling for all input pin which have connected listener
     *
     * @param scheduledExecutorService the scheduler executor service to use for polling
     * @param interval interval in ms between two polling runs
     * @param intervalType type how the interval setting is used
     */
    void enableMonitor(ScheduledExecutorService scheduledExecutorService, int interval,
            MonitorIntervalType intervalType);

    /**
     * Enable the polling for all input pin which have connected listener. The default scheduler service of Pi4J is used
     * to schedule the polling.
     *
     * @param interval interval in ms between two polling runs
     * @param intervalType type how the interval setting is used
     */

    void enableMonitor(int interval, MonitorIntervalType intervalType);

    /**
     * Enable the polling for all input pin which have connected listener. The default scheduler service of Pi4J is used
     * to schedule the polling. The default polling intterval is used
     *
     */
    void enableMonitor();

    /**
     * Disable any polling
     */
    void disableMonitor();

    /**
     * Check if monitor is started. This should be true if the monitor is enabled and there are one or more listener
     * registered for input pins
     *
     * @return state of the monitor
     */
    boolean isMonitorRunning();

}
