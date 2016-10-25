package com.pi4j.gpio.extension.base;

/*-
 * #%L
 * **********************************************************************
 * ORGANIZATION  :  Pi4J
 * PROJECT       :  Pi4J :: GPIO Extension
 * FILENAME      :  ExtensionProviderBase.java
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.pi4j.concurrent.DefaultExecutorServiceFactory;
import com.pi4j.io.gpio.GpioProviderBase;
import com.pi4j.io.gpio.GpioProviderPinCache;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.event.PinListener;
import com.pi4j.io.gpio.exception.InvalidPinException;
import com.pi4j.io.gpio.exception.InvalidPinModeException;

/**
 * <p>
 * Extension of {@link com.pi4j.io.gpio.GpioProviderBase} to prepare a monitor class for all the extension provider
 * classes}.
 * </p>
 * <p>
 * The monitor use the specified ScheduledExecutorService or the ScheduledExecutorService from the
 * {@link com.pi4j.concurrent.DefaultExecutorServiceFactory}
 * </p>
 *
 *
 * @author Günter Goerlich
 */
@SuppressWarnings("unused")
public abstract class ExtensionProviderBase extends GpioProviderBase implements ExtensionProvider {

    private ExtensionMonitor monitor;
    private int interval = DEFAULT_MONITOR_INTERVAL;
    private MonitorIntervalType intervalType = DEFAULT_MONITOR_INTERVAL_TYPE;

    public ExtensionProviderBase() {
        super();
    }

    @Override
    public void setState(Pin pin, PinState state) {
        if (!hasPin(pin)) {
            throw new InvalidPinException(pin);
        }

        GpioProviderPinCache pinCache = getPinCache(pin);

        // only permit invocation on pins set to DIGITAL_OUTPUT or DIGITAL_INPUT modes
        if (pinCache.getMode() != PinMode.DIGITAL_OUTPUT && pinCache.getMode() != PinMode.DIGITAL_INPUT) {
            throw new InvalidPinModeException(pin, "Invalid pin mode on pin [" + pin.getName()
                    + "]; cannot setState() when pin mode is [" + pinCache.getMode().getName() + "]");
        }

        if (!state.equals(pinCache.getState())) {
            // cache pin state
            pinCache.setState(state);
            // dispatch the event
            dispatchPinDigitalStateChangeEvent(pin, state);

        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.io.gpio.GpioProvider#setValue(Pin, double)
     */
    @Override
    public void setValue(Pin pin, double value) {
        if (!hasPin(pin)) {
            throw new InvalidPinException(pin);
        }

        GpioProviderPinCache pinCache = getPinCache(pin);

        // only permit invocation on pins set to DIGITAL_OUTPUT or DIGITAL_INPUT modes
        if (pinCache.getMode() != PinMode.ANALOG_OUTPUT && pinCache.getMode() != PinMode.ANALOG_INPUT) {
            throw new InvalidPinModeException(pin, "Invalid pin mode on pin [" + pin.getName()
                    + "]; cannot setState() when pin mode is [" + pinCache.getMode().getName() + "]");
        }

        // for digital analog pins, we will echo the event feedback
        dispatchPinAnalogValueChangeEvent(pin, value);

        // cache pin analog value
        getPinCache(pin).setAnalogValue(value);
    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.io.gpio.GpioProvider#getValue(Pin)
     */
    @Override
    public double getValue(Pin pin) {
        if (!hasPin(pin)) {
            throw new InvalidPinException(pin);
        }

        GpioProviderPinCache pinCache = getPinCache(pin);

        // only permit invocation on pins set to ANALOG_OUTPUT or ANALOG_INPUT modes
        if (pinCache.getMode() != PinMode.ANALOG_INPUT && pinCache.getMode() != PinMode.ANALOG_OUTPUT) {
            throw new InvalidPinModeException(pin, "Invalid pin mode on pin [" + pin.getName()
                    + "]; cannot setState() when pin mode is [" + pinCache.getMode().getName() + "]");
        }

        // return cached pin analog value
        return getPinCache(pin).getAnalogValue();
    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.io.gpio.GpioProvider#addListener(Pin pin, PinListener listener)
     */
    @Override
    public void addListener(Pin pin, PinListener listener) {
        synchronized (listeners) {
            super.addListener(pin, listener);

            // start the monitor if set.
            if (monitor != null) {
                if (!isMonitorRunning()) {
                    monitor.startMonitor();
                }

            }
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.io.gpio.GpioProvider#removeListener(Pin, PinListener)
     */
    @Override
    public void removeListener(Pin pin, PinListener listener) {
        synchronized (listeners) {
            super.removeListener(pin, listener);

            // we need no monitor running if we have no listener
            if (listeners.isEmpty() && isMonitorRunning()) {
                monitor.shutdown();
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.io.gpio.GpioProvider#removeAllListeners()
     */
    @Override
    public void removeAllListeners() {
        synchronized (listeners) {
            super.removeAllListeners();
            if (monitor != null) {
                monitor.shutdown();
            }
        }

    }

    private void setMonitior(ExtensionMonitor monitor) {
        if (this.monitor != null) {
            this.monitor.shutdown();
        }
        this.monitor = monitor;
        if (monitor != null && !listeners.isEmpty()) {
            monitor.startMonitor();
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#enableMonitor(ScheduledExecutorService,int, RefreshType)
     */
    @Override
    public void enableMonitor(ScheduledExecutorService scheduledExecutorService, int interval,
            MonitorIntervalType intervalType) {
        setMonitior(createMonitor(scheduledExecutorService, interval, intervalType));

    }

    /*
     * (non-Javadoc)
     * /*
     * (non-Javadoc)
     *
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#enableMonitor(int, RefreshType)
     */
    @Override
    public void enableMonitor(int interval, MonitorIntervalType intervalType) {
        setMonitior(createMonitor(null, interval, intervalType));

    }

    /**
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#enableMonitor()
     */
    @Override
    public void enableMonitor() {
        if (monitor == null) {
            setMonitior(createMonitor(null, interval, intervalType));
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#disableMonitor()
     */
    @Override
    public void disableMonitor() {
        setMonitior(null);
    }

    protected abstract ExtensionMonitor createMonitor(ScheduledExecutorService scheduledExecutorService, int interval,
            MonitorIntervalType intervalType);

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#isMonitorRunning()
     */
    @Override
    public boolean isMonitorRunning() {
        return monitor != null && monitor.future != null;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#getMonitorEnabled()
     */
    @Override
    public boolean getMonitorEnabled() {
        return monitor != null;
    }
    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#getMonitorInterval()
     */

    @Override
    public int getMonitorInterval() {
        return monitor != null ? monitor.interval : interval;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#setMonitorInterval(int newInterval)
     */

    @Override
    public void setMonitorInterval(int newInterval) {

        // enforce a minimum interval threshold.
        if (newInterval < MIN_MONITOR_INTERVAL) {
            newInterval = DEFAULT_MONITOR_INTERVAL;
        }

        this.interval = newInterval;
        if (monitor != null) {
            monitor.setMonitorInterval(newInterval);
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#getMonitorIntervalType()
     */

    @Override
    public MonitorIntervalType getMonitorIntervalType() {

        return monitor != null ? monitor.intervalType : intervalType;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.pi4j.gpio.extension.base.ExtensionProvider#setMonitorIntervalType(MonitorIntervaType newType)
     */
    @Override
    public void setMonitorIntervalType(MonitorIntervalType newType) {
        intervalType = newType;
        if (monitor != null) {
            monitor.setMonitorIntervalType(newType);
        }
    }

    /**
     * Set the background monitoring thread's enabled state.
     *
     * @param enabled monitoring enabled or disabled state
     */
    @Override
    @Deprecated
    public void setMonitorEnabled(boolean enabled) {
        if (enabled) {
            enableMonitor();
        } else {
            disableMonitor();
        }
    }

    /**
     * This class/thread can be used to to actively monitor for GPIO interrupts
     *
     * @author Günter Goerlich
     *
     */
    protected abstract class ExtensionMonitor implements Runnable {

        private ScheduledFuture<?> future;
        private ScheduledExecutorService scheduledExecutorService;
        private int interval;
        private MonitorIntervalType intervalType;

        /**
         * Constructor with the settings for the input pin polling scheduler
         *
         * @param scheduledExecutorService the scheduler service to use for polling the input pins. If NULL the pi4j
         *            default service is used default service
         * @param refresh time in ms to schedule the polling thread
         * @param refreshType define the scheduling type
         *            <p>
         *            <li>{@link #Delay} The delay value defines the waiting time from thread finish to thread
         *            start</li>
         *            <li>{@link #Rate} The thread is scheduled with a fixed rate</li>
         *            </p>
         *
         */

        protected ExtensionMonitor(ScheduledExecutorService scheduledExecutorService, int interval,
                MonitorIntervalType intervalType) {
            this.scheduledExecutorService = scheduledExecutorService;
            if (scheduledExecutorService == null) {
                scheduledExecutorService = new DefaultExecutorServiceFactory().getScheduledExecutorService();
            }
            this.interval = interval;
            this.intervalType = intervalType;
        }

        /**
         * Constructor with the settings for refresh using the default pin polling scheduler
         *
         * @param refresh time in ms to schedule the polling thread
         * @param refreshType define the scheduling type
         *            <p>
         *            <li>{@link #Delay} The delay value defines the waiting time from thread finish to thread
         *            start</li>
         *            <li>{@link #Rate} The thread is scheduled with a fixed rate</li>
         *            </p>
         */

        protected ExtensionMonitor(int interval, MonitorIntervalType intervalType) {
            this(null, interval, intervalType);

        }

        protected void shutdown() {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
        }

        protected void startMonitor() {
            shutdown();
            if (intervalType == MonitorIntervalType.Rate) {
                future = scheduledExecutorService.scheduleAtFixedRate(this, interval, interval, TimeUnit.MILLISECONDS);
            } else {
                future = scheduledExecutorService.scheduleWithFixedDelay(this, interval, interval,
                        TimeUnit.MILLISECONDS);
            }

        }

        public int setMonitorInterval(int newRate) {
            int old = interval;
            if (newRate != interval) {
                old = interval;
                interval = newRate;
                if (future != null) {
                    startMonitor();
                }
            }
            return old;
        }

        public MonitorIntervalType setMonitorIntervalType(MonitorIntervalType newType) {
            MonitorIntervalType old = newType;
            if (newType != intervalType) {
                old = intervalType;
                intervalType = newType;
                if (future != null) {
                    startMonitor();
                }
            }
            return old;
        }

    }
}
