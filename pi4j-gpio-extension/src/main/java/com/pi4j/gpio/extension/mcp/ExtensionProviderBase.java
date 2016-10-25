package com.pi4j.gpio.extension.mcp;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * Abstract base implementation of {@link com.pi4j.io.gpio.GpioProvider}.
 *
 * @author Robert Savage (<a
 *         href="http://www.savagehomeautomation.com">http://www.savagehomeautomation.com</a>)
 */
@SuppressWarnings("unused")
public abstract class ExtensionProviderBase extends GpioProviderBase {

    private AtomicReference<GpioStateMonitor> mon;

    private GpioStateMonitor monitor;

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

    @Override
    public void removeAllListeners() {
        synchronized (listeners) {
            super.removeAllListeners();
            if (monitor != null) {
                monitor.shutdown();
            }
        }

    }

    protected void setMonitior(GpioStateMonitor monitor) {
        if (this.monitor != null) {
            this.monitor.shutdown();
        }
        this.monitor = monitor;
        if (!listeners.isEmpty()) {
            monitor.startMonitor();
        }

    }

    public boolean isMonitorRunning() {
        return monitor != null && monitor.future != null;
    }

    public enum RefreshType {
        RefreshDelay,
        RefreshRate
    }

    /**
     * This class/thread can be used to to actively monitor for GPIO interrupts
     *
     * @author Robert Savage
     *
     */
    protected abstract class GpioStateMonitor implements Runnable {

        private ScheduledFuture<?> future;
        private ScheduledExecutorService scheduledExecutorService;
        private int refresh;
        private RefreshType refreshType;

        protected GpioStateMonitor(ScheduledExecutorService scheduledExecutorService, int refresh,
                RefreshType refreshType) {
            this.scheduledExecutorService = scheduledExecutorService;
            if (scheduledExecutorService == null) {
                scheduledExecutorService = new DefaultExecutorServiceFactory().getScheduledExecutorService();
            }
            this.refresh = refresh;
            this.refreshType = refreshType;
        }

        protected GpioStateMonitor() {
            this(null, 50, RefreshType.RefreshDelay);

        }

        protected GpioStateMonitor(int refresh, RefreshType refreshType) {
            this(null, refresh, refreshType);

        }

        protected void shutdown() {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
        }

        protected void startMonitor() {
            shutdown();
            if (refreshType == RefreshType.RefreshRate) {
                future = scheduledExecutorService.scheduleAtFixedRate(this, refresh, refresh, TimeUnit.MILLISECONDS);
            } else {
                future = scheduledExecutorService.scheduleWithFixedDelay(this, refresh, refresh, TimeUnit.MILLISECONDS);
            }

        }

    }
}
