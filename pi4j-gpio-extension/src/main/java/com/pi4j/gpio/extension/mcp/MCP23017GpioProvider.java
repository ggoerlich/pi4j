package com.pi4j.gpio.extension.mcp;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import com.pi4j.gpio.extension.base.ExtensionProviderBase;
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
 * FILENAME      :  MCP23017GpioProvider.java
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
 * This GPIO provider implements the MCP23017 I2C GPIO expansion board as native Pi4J GPIO pins.
 * More information about the board can be found here: *
 * http://ww1.microchip.com/downloads/en/DeviceDoc/21952b.pdf
 * http://learn.adafruit.com/mcp230xx-gpio-expander-on-the-raspberry-pi/overview
 * </p>
 *
 * <p>
 * The MCP23017 is connected via I2C connection to the Raspberry Pi and provides 16 GPIO pins that
 * can be used for either digital input or digital output pins.
 * </p>
 *
 * @author Robert Savage
 * @author Günter Goerlich
 *
 */
public class MCP23017GpioProvider extends ExtensionProviderBase {

    public static final String NAME = "com.pi4j.gpio.extension.mcp.MCP23017GpioProvider";
    public static final String DESCRIPTION = "MCP23017 GPIO Provider";
    public static final int DEFAULT_ADDRESS = 0x20;
    public static final int DEFAULT_INTERVAL_TIME = 50;
    public static final MonitorIntervalType DEFAULT_INTERVAL_TYPE = MonitorIntervalType.Delay;

    private static final int REGISTER_IODIR_A = 0x00;
    private static final int REGISTER_IODIR_B = 0x01;
    private static final int REGISTER_GPINTEN_A = 0x04;
    private static final int REGISTER_GPINTEN_B = 0x05;
    private static final int REGISTER_DEFVAL_A = 0x06;
    private static final int REGISTER_DEFVAL_B = 0x07;
    private static final int REGISTER_INTCON_A = 0x08;
    private static final int REGISTER_INTCON_B = 0x09;
    private static final int REGISTER_GPPU_A = 0x0C;
    private static final int REGISTER_GPPU_B = 0x0D;
    private static final int REGISTER_INTF_A = 0x0E;
    private static final int REGISTER_INTF_B = 0x0F;
    // private static final int REGISTER_INTCAP_A = 0x10;
    // private static final int REGISTER_INTCAP_B = 0x11;
    private static final int REGISTER_GPIO_A = 0x12;
    private static final int REGISTER_GPIO_B = 0x13;

    private static final int GPIO_A_OFFSET = 0;
    private static final int GPIO_B_OFFSET = 1000;

    private int currentStatesA = 0;
    private int currentStatesB = 0;
    private int currentDirectionA = 0;
    private int currentDirectionB = 0;
    private int currentPullupA = 0;
    private int currentPullupB = 0;

    private boolean i2cBusOwner = false;
    private final I2CBus bus;
    private final I2CDevice device;

    public MCP23017GpioProvider(int busNumber, int address) throws UnsupportedBusNumberException, IOException {
        // create I2C communications bus instance
        this(busNumber, address, DEFAULT_INTERVAL_TIME);
    }

    public MCP23017GpioProvider(int busNumber, int address, int pollingTime)
            throws IOException, UnsupportedBusNumberException {
        // create I2C communications bus instance
        this(I2CFactory.getInstance(busNumber), address, pollingTime);
    }

    public MCP23017GpioProvider(I2CBus bus, int address) throws IOException {
        this(bus, address, DEFAULT_INTERVAL_TIME);
    }

    public MCP23017GpioProvider(I2CBus bus, int address, int pollingTime) throws IOException {

        // set reference to I2C communications bus instance
        this.bus = bus;

        // create I2C device instance
        device = bus.getDevice(address);

        // read initial GPIO pin states
        currentStatesA = device.read(REGISTER_GPIO_A);
        currentStatesB = device.read(REGISTER_GPIO_B);

        // set all default pins directions
        device.write(REGISTER_IODIR_A, (byte) currentDirectionA);
        device.write(REGISTER_IODIR_B, (byte) currentDirectionB);

        // set all default pin interrupts
        device.write(REGISTER_GPINTEN_A, (byte) currentDirectionA);
        device.write(REGISTER_GPINTEN_B, (byte) currentDirectionB);

        // set all default pin interrupt default values
        device.write(REGISTER_DEFVAL_A, (byte) 0x00);
        device.write(REGISTER_DEFVAL_B, (byte) 0x00);

        // set all default pin interrupt comparison behaviors
        device.write(REGISTER_INTCON_A, (byte) 0x00);
        device.write(REGISTER_INTCON_B, (byte) 0x00);

        // set all default pin states
        device.write(REGISTER_GPIO_A, (byte) currentStatesA);
        device.write(REGISTER_GPIO_B, (byte) currentStatesB);

        // set all default pin pull up resistors
        device.write(REGISTER_GPPU_A, (byte) currentPullupA);
        device.write(REGISTER_GPPU_B, (byte) currentPullupB);

        // create and set the monitor
        enableMonitor(pollingTime, DEFAULT_INTERVAL_TYPE);

        i2cBusOwner = true;
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

        // determine A or B port based on pin address
        try {
            if (pin.getAddress() < GPIO_B_OFFSET) {
                setModeA(pin, mode);
            } else {
                setModeB(pin, mode);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void setModeA(Pin pin, PinMode mode) throws IOException {
        // determine register and pin address
        int pinAddress = pin.getAddress() - GPIO_A_OFFSET;

        // determine update direction value based on mode
        if (mode == PinMode.DIGITAL_INPUT) {
            currentDirectionA |= pinAddress;
        } else if (mode == PinMode.DIGITAL_OUTPUT) {
            currentDirectionA &= ~pinAddress;
        }

        // next update direction value
        device.write(REGISTER_IODIR_A, (byte) currentDirectionA);

        // enable interrupts; interrupt on any change from previous state
        device.write(REGISTER_GPINTEN_A, (byte) currentDirectionA);
    }

    private void setModeB(Pin pin, PinMode mode) throws IOException {
        // determine register and pin address
        int pinAddress = pin.getAddress() - GPIO_B_OFFSET;

        // determine update direction value based on mode
        if (mode == PinMode.DIGITAL_INPUT) {
            currentDirectionB |= pinAddress;
        } else if (mode == PinMode.DIGITAL_OUTPUT) {
            currentDirectionB &= ~pinAddress;
        }

        // next update direction (mode) value
        device.write(REGISTER_IODIR_B, (byte) currentDirectionB);

        // enable interrupts; interrupt on any change from previous state
        device.write(REGISTER_GPINTEN_B, (byte) currentDirectionB);
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
                // determine A or B port based on pin address
                if (pin.getAddress() < GPIO_B_OFFSET) {
                    setStateA(pin, state);
                } else {
                    setStateB(pin, state);
                }

            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void setStateA(Pin pin, PinState state) throws IOException {
        // determine pin address
        int pinAddress = pin.getAddress() - GPIO_A_OFFSET;

        // determine state value for pin bit
        if (state.isHigh()) {
            currentStatesA |= pinAddress;
        } else {
            currentStatesA &= ~pinAddress;
        }

        // update state value
        device.write(REGISTER_GPIO_A, (byte) currentStatesA);
    }

    private void setStateB(Pin pin, PinState state) throws IOException {
        // determine pin address
        int pinAddress = pin.getAddress() - GPIO_B_OFFSET;

        // determine state value for pin bit
        if (state.isHigh()) {
            currentStatesB |= pinAddress;
        } else {
            currentStatesB &= ~pinAddress;
        }

        // update state value
        device.write(REGISTER_GPIO_B, (byte) currentStatesB);
    }

    @Override
    public PinState getState(Pin pin) {

        // If no monitor is running and this is a input pin we must fetch the current value
        if (!isMonitorRunning() && getPinCache(pin).getMode() == PinMode.DIGITAL_INPUT) {
            try {

                // determine A or B port based on pin address
                if (pin.getAddress() < GPIO_B_OFFSET) {
                    currentStatesA = device.read(REGISTER_GPIO_A);
                    setState(pin,
                            (currentStatesA & (pin.getAddress() - GPIO_A_OFFSET)) != 0 ? PinState.HIGH : PinState.LOW); // get
                                                                                                                        // pin
                                                                                                                        // state
                } else {
                    currentStatesB = device.read(REGISTER_GPIO_B);
                    setState(pin,
                            (currentStatesB & (pin.getAddress() - GPIO_A_OFFSET)) != 0 ? PinState.HIGH : PinState.LOW); // get
                                                                                                                        // pin
                                                                                                                        // state
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        // return pin state
        return getState(pin);
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
            // determine A or B port based on pin address
            if (pin.getAddress() < GPIO_B_OFFSET) {
                setPullResistanceA(pin, resistance);
            } else {
                setPullResistanceB(pin, resistance);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        // cache resistance
        getPinCache(pin).setResistance(resistance);
    }

    private void setPullResistanceA(Pin pin, PinPullResistance resistance) throws IOException {
        // determine pin address
        int pinAddress = pin.getAddress() - GPIO_A_OFFSET;

        // determine pull up value for pin bit
        if (resistance == PinPullResistance.PULL_UP) {
            currentPullupA |= pinAddress;
        } else {
            currentPullupA &= ~pinAddress;
        }

        // next update pull up resistor value
        device.write(REGISTER_GPPU_A, (byte) currentPullupA);
    }

    private void setPullResistanceB(Pin pin, PinPullResistance resistance) throws IOException {
        // determine pin address
        int pinAddress = pin.getAddress() - GPIO_B_OFFSET;

        // determine pull up value for pin bit
        if (resistance == PinPullResistance.PULL_UP) {
            currentPullupB |= pinAddress;
        } else {
            currentPullupB &= ~pinAddress;
        }

        // next update pull up resistor value
        device.write(REGISTER_GPPU_B, (byte) currentPullupB);
    }

    @Override
    public PinPullResistance getPullResistance(Pin pin) {
        return super.getPullResistance(pin);
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
     * This class is used to to actively monitor for GPIO interrupts
     *
     * @author Robert Savage
     * @author Günter Goerlich
     *
     */
    private class StateMonitor extends ExtensionMonitor {
        private I2CDevice device;

        public StateMonitor(I2CDevice device, ScheduledExecutorService executorService, int interval,
                MonitorIntervalType intervalType) {
            super(executorService, interval, intervalType);
            this.device = device;
        }

        @Override
        public void run() {

            try {
                if (currentDirectionA > 0) {
                    // process interrupts for port A
                    int pinInterruptA = device.read(REGISTER_INTF_A);

                    // validate that there is at least one interrupt active on port A
                    if (pinInterruptA > 0) {
                        // read the current pin states on port A
                        int pinInterruptState = device.read(REGISTER_GPIO_A);

                        // loop over the available pins on port A
                        for (Pin pin : MCP23017Pin.ALL_A_PINS) {
                            PinState newState = (pinInterruptState & (pin.getAddress() - GPIO_A_OFFSET)) != 0
                                    ? PinState.HIGH : PinState.LOW;

                            // the setState method must call the listeners
                            setState(pin, newState);
                            // }
                        }
                    }
                }
                if (currentDirectionB > 0) {
                    // process interrupts for port B
                    int pinInterruptB = device.read(REGISTER_INTF_B);

                    // validate that there is at least one interrupt active on port B
                    if (pinInterruptB > 0) {
                        // read the current pin states on port B
                        int pinInterruptState = device.read(REGISTER_GPIO_B);

                        // loop over the available pins on port B
                        for (Pin pin : MCP23017Pin.ALL_A_PINS) {
                            PinState newState = (pinInterruptState & (pin.getAddress() - GPIO_B_OFFSET)) != 0
                                    ? PinState.HIGH : PinState.LOW;

                            // the setState method must call the listeners
                            setState(pin, newState);
                            // }
                        }
                    }
                }

            } catch (IOException e) {

                e.printStackTrace();
            }

        }
    }

    @Override
    protected ExtensionMonitor createMonitor(ScheduledExecutorService scheduledExecutorService, int interval,
            MonitorIntervalType intervalType) {

        return new StateMonitor(device, scheduledExecutorService, interval, intervalType);
    }

}
