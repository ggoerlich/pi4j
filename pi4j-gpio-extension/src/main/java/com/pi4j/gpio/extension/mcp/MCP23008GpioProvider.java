package com.pi4j.gpio.extension.mcp;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import com.pi4j.io.gpio.GpioProvider;
import com.pi4j.io.gpio.GpioProviderBase;
import com.pi4j.io.gpio.GpioProviderPinCache;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.exception.InvalidPinException;
import com.pi4j.io.gpio.exception.UnsupportedPinPullResistanceException;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

/*
 * #%L
 * **********************************************************************
 * ORGANIZATION  :  Pi4J
 * PROJECT       :  Pi4J :: GPIO Extension
 * FILENAME      :  MCP23008GpioProvider.java
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

/**
 * <p>
 * This GPIO provider implements the MCP23008 I2C GPIO expansion board as native Pi4J GPIO pins.
 * More information about the board can be found here: *
 * http://ww1.microchip.com/downloads/en/DeviceDoc/21919e.pdf
 * http://learn.adafruit.com/mcp230xx-gpio-expander-on-the-raspberry-pi/overview
 * </p>
 *
 * <p>
 * The MCP23008 is connected via I2C connection to the Raspberry Pi and provides
 * 8 GPIO pins that can be used for either digital input or digital output pins.
 * </p>
 *
 * @author Robert Savage
 * @author Guenter Goerlich
 *
 */

public class MCP23008GpioProvider extends GpioProviderBase implements GpioProvider {

    public static final String NAME = "com.pi4j.gpio.extension.mcp.MCP23008GpioProvider";
    public static final String DESCRIPTION = "MCP23008 GPIO Provider";

    public static final int REGISTER_IODIR = 0x00;
    private static final int REGISTER_GPINTEN = 0x02;
    private static final int REGISTER_DEFVAL = 0x03;
    private static final int REGISTER_INTCON = 0x04;
    private static final int REGISTER_GPPU = 0x06;
    private static final int REGISTER_INTF = 0x07;
    // private static final int REGISTER_INTCAP = 0x08;
    public static final int REGISTER_GPIO = 0x09;

    private int currentStates = 0;
    private int currentDirection = 0;
    private int currentPullup = 0;

    private boolean i2cBusOwner = false;
    private I2CBus bus;
    private I2CDevice device;
    private GpioStateMonitor monitor = null;

    public MCP23008GpioProvider(int busNumber, int address) throws UnsupportedBusNumberException, IOException {
        this(busNumber, address, null, 50, RefreshType.RefreshDelay);
    }

    /**
     * @param busNumber Number of the I2C bus
     * @param address 7 Bit device address
     * @param executorService a instance of the a executor service to use
     * @param refresh time in ms to refresh listener.
     * @param refreshType define the type the the refresh time is used
     * @throws UnsupportedBusNumberException
     * @throws IOException
     */

    public MCP23008GpioProvider(int busNumber, int address, ScheduledExecutorService executorService, int refresh,
            RefreshType refreshType) throws UnsupportedBusNumberException, IOException {

        // create I2C communications bus instanceScheduledExecutorService
        this(I2CFactory.getInstance(busNumber), address, executorService, refresh, refreshType);
        i2cBusOwner = true;

    }

    /**
     * @param busNumber I2C bus instance to use
     * @param address 7 Bit device address
     * @param executorService a instance of the a executor service to use
     * @param refresh time in ms to refresh listener
     * @param refreshType define the type the the refresh time is used
     * @throws UnsupportedBusNumberException
     * @throws IOException
     */
    public MCP23008GpioProvider(I2CBus bus, int address, ScheduledExecutorService executorService, int refreshDelay,
            RefreshType refreshType) throws IOException {
        super();

        // set reference to I2C communications bus instance
        this.bus = bus;

        // create I2C device instance
        device = bus.getDevice(address);

        // read initial GPIO pin states
        currentStates = device.read(REGISTER_GPIO);

        // set all default pins directions
        device.write(REGISTER_IODIR, (byte) currentDirection);

        // set all default pin interrupts
        device.write(REGISTER_GPINTEN, (byte) currentDirection);

        // set all default pin interrupt default values
        device.write(REGISTER_DEFVAL, (byte) 0x00);

        // set all default pin interrupt comparison behaviors
        device.write(REGISTER_INTCON, (byte) 0x00);

        // set all default pin states
        device.write(REGISTER_GPIO, (byte) currentStates);

        // set all default pin pull up resistors
        device.write(REGISTER_GPPU, (byte) currentPullup);
        setMonitior(new StateMonitor(device));
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void export(Pin pin, PinMode mode) {
        // make sure to set the pin mode
        super.export(pin, mode);
        setMode(pin, mode);
    }

    @Override
    public void unexport(Pin pin) {
        super.unexport(pin);
        setMode(pin, PinMode.DIGITAL_OUTPUT);
    }

    @Override
    public void setMode(Pin pin, PinMode mode) {
        super.setMode(pin, mode);

        try {
            // determine register and pin address
            int pinAddress = pin.getAddress();

            // determine update direction value based on mode
            if (mode == PinMode.DIGITAL_INPUT) {
                currentDirection |= pinAddress;
            } else if (mode == PinMode.DIGITAL_OUTPUT) {
                currentDirection &= ~pinAddress;
            }

            // next update direction value
            device.write(REGISTER_IODIR, (byte) currentDirection);

            // enable interrupts; interrupt on any change from previous state
            device.write(REGISTER_GPINTEN, (byte) currentDirection);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    @Override
    public PinMode getMode(Pin pin) {
        return super.getMode(pin);
    }

    @Override
    public void setState(Pin pin, PinState state) {
        super.setState(pin, state);

        GpioProviderPinCache pinCache = getPinCache(pin);
        if (pinCache.getMode() == PinMode.DIGITAL_OUTPUT) {
            try {
                // determine pin address
                int pinAddress = pin.getAddress();

                // determine state value for pin bit
                if (state.isHigh()) {
                    currentStates |= pinAddress;
                } else {
                    currentStates &= ~pinAddress;
                }

                // update state value
                device.write(REGISTER_GPIO, (byte) currentStates);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public PinState getState(Pin pin) {
        // If no monitor is running and this is a input pin we must fetch the current value
        if (!isMonitorRunning() && getPinCache(pin).getMode() == PinMode.DIGITAL_INPUT) {
            // read GPIO pin states
            try {
                int state = device.read(REGISTER_GPIO);
                // determine pin address
                int pinAddress = pin.getAddress();

                // determine pin state
                PinState result = (state & pinAddress) == pinAddress ? PinState.HIGH : PinState.LOW;
                setState(pin, result);
            } catch (IOException e) {
                throw new RuntimeException("Error fetch Value from I2C device", e);
            }

        }
        return super.getState(pin);

    }

    @Override
    public void setPullResistance(Pin pin, PinPullResistance resistance) {
        // validate
        if (hasPin(pin) == false) {
            throw new InvalidPinException(pin);
        }
        // validate
        if (!pin.getSupportedPinPullResistance().contains(resistance)) {
            throw new UnsupportedPinPullResistanceException(pin, resistance);
        }
        try {
            // determine pin address
            int pinAddress = pin.getAddress();

            // determine pull up value for pin bit
            if (resistance == PinPullResistance.PULL_UP) {
                currentPullup |= pinAddress;
            } else {
                currentPullup &= ~pinAddress;
            }

            // next update pull up resistor value
            device.write(REGISTER_GPPU, (byte) currentPullup);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        // cache resistance
        getPinCache(pin).setResistance(resistance);
    }

    @Override
    public void shutdown() {

        // prevent reentrant invocation
        if (isShutdown()) {
            return;
        }

        // perform shutdown login in base
        super.shutdown();

        try {

            // if we are the owner of the I2C bus, then close it
            if (i2cBusOwner) {
                // close the I2C bus communication
                bus.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This class/thread is used to to actively monitor for GPIO interrupts
     *
     * @author Robert Savage
     *
     */
    private class StateMonitor extends GpioStateMonitor {
        private I2CDevice device;

        public StateMonitor(I2CDevice device) {
            super();
            this.device = device;
        }

        @Override
        public void run() {

            int pinInterrupt;
            try {
                pinInterrupt = device.read(REGISTER_INTF);

                // validate that there is at least one interrupt active
                if (pinInterrupt > 0) {
                    // read the current pin states
                    int state = device.read(REGISTER_GPIO);

                    // loop over the available pins
                    for (Pin pin : MCP23008Pin.ALL) {
                        // is there an interrupt flag on this pin?
                        // if ((pinInterrupt & pin.getAddress()) > 0) {
                        // System.out.println("INTERRUPT ON PIN [" + pin.getName() + "]");
                        PinState newState = (state & pin.getAddress()) != 0 ? PinState.HIGH : PinState.LOW;

                        setState(pin, newState);

                    }
                }
            } catch (IOException e) {

                e.printStackTrace();
            }

        }
    }
}
