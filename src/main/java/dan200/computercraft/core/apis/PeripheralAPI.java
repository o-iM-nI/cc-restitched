/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2021. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */

package dan200.computercraft.core.apis;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IWorkMonitor;
import dan200.computercraft.api.peripheral.NotAttachedException;
import dan200.computercraft.core.asm.LuaMethod;
import dan200.computercraft.core.asm.NamedMethod;
import dan200.computercraft.core.asm.PeripheralMethod;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.core.tracking.TrackingField;

/**
 * CC's "native" peripheral API. This is wrapped within CraftOS to provide a version which works with modems.
 *
 * @cc.module peripheral
 * @hidden
 */
public class PeripheralAPI implements ILuaAPI, IAPIEnvironment.IPeripheralChangeListener {
    private final IAPIEnvironment environment;
    private final PeripheralWrapper[] peripherals = new PeripheralWrapper[6];
    private boolean running;
    public PeripheralAPI(IAPIEnvironment environment) {
        this.environment = environment;
        this.environment.setPeripheralChangeListener(this);
        this.running = false;
    }

    public static Map<String, PeripheralMethod> getMethods(IPeripheral peripheral) {
        String[] dynamicMethods = peripheral instanceof IDynamicPeripheral ? Objects.requireNonNull(((IDynamicPeripheral) peripheral).getMethodNames(),
                                                                                                    "Peripheral methods cannot be null") :
                                  LuaMethod.EMPTY_METHODS;

        List<NamedMethod<PeripheralMethod>> methods = PeripheralMethod.GENERATOR.getMethods(peripheral.getClass());

        Map<String, PeripheralMethod> methodMap = new HashMap<>(methods.size() + dynamicMethods.length);
        for (int i = 0; i < dynamicMethods.length; i++) {
            methodMap.put(dynamicMethods[i], PeripheralMethod.DYNAMIC.get(i));
        }
        for (NamedMethod<PeripheralMethod> method : methods) {
            methodMap.put(method.getName(), method.getMethod());
        }
        return methodMap;
    }

    // IPeripheralChangeListener

    @Override
    public void onPeripheralChanged(ComputerSide side, IPeripheral newPeripheral) {
        synchronized (this.peripherals) {
            int index = side.ordinal();
            if (this.peripherals[index] != null) {
                // Queue a detachment
                final PeripheralWrapper wrapper = this.peripherals[index];
                if (wrapper.isAttached()) {
                    wrapper.detach();
                }

                // Queue a detachment event
                this.environment.queueEvent("peripheral_detach", side.getName());
            }

            // Assign the new peripheral
            this.peripherals[index] = newPeripheral == null ? null : new PeripheralWrapper(newPeripheral, side.getName());

            if (this.peripherals[index] != null) {
                // Queue an attachment
                final PeripheralWrapper wrapper = this.peripherals[index];
                if (this.running && !wrapper.isAttached()) {
                    wrapper.attach();
                }

                // Queue an attachment event
                this.environment.queueEvent("peripheral", side.getName());
            }
        }
    }

    @Override
    public String[] getNames() {
        return new String[] {"peripheral"};
    }

    @Override
    public void startup() {
        synchronized (this.peripherals) {
            this.running = true;
            for (int i = 0; i < 6; i++) {
                PeripheralWrapper wrapper = this.peripherals[i];
                if (wrapper != null && !wrapper.isAttached()) {
                    wrapper.attach();
                }
            }
        }
    }

    @Override
    public void shutdown() {
        synchronized (this.peripherals) {
            this.running = false;
            for (int i = 0; i < 6; i++) {
                PeripheralWrapper wrapper = this.peripherals[i];
                if (wrapper != null && wrapper.isAttached()) {
                    wrapper.detach();
                }
            }
        }
    }

    @LuaFunction
    public final boolean isPresent(String sideName) {
        ComputerSide side = ComputerSide.valueOfInsensitive(sideName);
        if (side != null) {
            synchronized (this.peripherals) {
                PeripheralWrapper p = this.peripherals[side.ordinal()];
                if (p != null) {
                    return true;
                }
            }
        }
        return false;
    }

    @LuaFunction
    public final Object[] getType(String sideName) {
        ComputerSide side = ComputerSide.valueOfInsensitive(sideName);
        if (side == null) {
            return null;
        }

        synchronized (this.peripherals) {
            PeripheralWrapper p = this.peripherals[side.ordinal()];
            if (p != null) {
                return new Object[] {p.getType()};
            }
        }
        return null;
    }

    @LuaFunction
    public final Object[] getMethods(String sideName) {
        ComputerSide side = ComputerSide.valueOfInsensitive(sideName);
        if (side == null) {
            return null;
        }

        synchronized (this.peripherals) {
            PeripheralWrapper p = this.peripherals[side.ordinal()];
            if (p != null) {
                return new Object[] {p.getMethods()};
            }
        }
        return null;
    }

    @LuaFunction
    public final MethodResult call(ILuaContext context, IArguments args) throws LuaException {
        ComputerSide side = ComputerSide.valueOfInsensitive(args.getString(0));
        String methodName = args.getString(1);
        IArguments methodArgs = args.drop(2);

        if (side == null) {
            throw new LuaException("No peripheral attached");
        }

        PeripheralWrapper p;
        synchronized (this.peripherals) {
            p = this.peripherals[side.ordinal()];
        }
        if (p == null) {
            throw new LuaException("No peripheral attached");
        }

        try {
            return p.call(context, methodName, methodArgs)
                    .adjustError(1);
        } catch (LuaException e) {
            // We increase the error level by one in order to shift the error level to where peripheral.call was
            // invoked. It would be possible to do it in Lua code, but would add significantly more overhead.
            if (e.getLevel() > 0) {
                throw new FastLuaException(e.getMessage(), e.getLevel() + 1);
            }
            throw e;
        }
    }

    private class PeripheralWrapper extends ComputerAccess {
        private final String side;
        private final IPeripheral peripheral;

        private final String type;
        private final Map<String, PeripheralMethod> methodMap;
        private boolean attached;

        PeripheralWrapper(IPeripheral peripheral, String side) {
            super(PeripheralAPI.this.environment);
            this.side = side;
            this.peripheral = peripheral;
            this.attached = false;

            this.type = Objects.requireNonNull(peripheral.getType(), "Peripheral type cannot be null");

            this.methodMap = PeripheralAPI.getMethods(peripheral);
        }

        public String getType() {
            return this.type;
        }        public IPeripheral getPeripheral() {
            return this.peripheral;
        }

        public Collection<String> getMethods() {
            return this.methodMap.keySet();
        }

        public synchronized void attach() {
            this.attached = true;
            this.peripheral.attach(this);
        }

        public void detach() {
            // Call detach
            this.peripheral.detach(this);

            synchronized (this) {
                // Unmount everything the detach function forgot to do
                this.unmountAll();
            }

            this.attached = false;
        }        public synchronized boolean isAttached() {
            return this.attached;
        }

        public MethodResult call(ILuaContext context, String methodName, IArguments arguments) throws LuaException {
            PeripheralMethod method;
            synchronized (this) {
                method = this.methodMap.get(methodName);
            }

            if (method == null) {
                throw new LuaException("No such method " + methodName);
            }

            PeripheralAPI.this.environment.addTrackingChange(TrackingField.PERIPHERAL_OPS);
            return method.apply(this.peripheral, context, this, arguments);
        }

        // IComputerAccess implementation
        @Override
        public synchronized String mount(@Nonnull String desiredLoc, @Nonnull IMount mount, @Nonnull String driveName) {
            if (!this.attached) {
                throw new NotAttachedException();
            }
            return super.mount(desiredLoc, mount, driveName);
        }

        @Override
        public synchronized String mountWritable(@Nonnull String desiredLoc, @Nonnull IWritableMount mount, @Nonnull String driveName) {
            if (!this.attached) {
                throw new NotAttachedException();
            }
            return super.mountWritable(desiredLoc, mount, driveName);
        }

        @Override
        public synchronized void unmount(String location) {
            if (!this.attached) {
                throw new NotAttachedException();
            }
            super.unmount(location);
        }

        @Override
        public int getID() {
            if (!this.attached) {
                throw new NotAttachedException();
            }
            return super.getID();
        }

        @Override
        public void queueEvent(@Nonnull String event, Object... arguments) {
            if (!this.attached) {
                throw new NotAttachedException();
            }
            super.queueEvent(event, arguments);
        }

        @Nonnull
        @Override
        public IWorkMonitor getMainThreadMonitor() {
            if (!this.attached) {
                throw new NotAttachedException();
            }
            return super.getMainThreadMonitor();
        }



        @Nonnull
        @Override
        public String getAttachmentName() {
            if (!this.attached) {
                throw new NotAttachedException();
            }
            return this.side;
        }

        @Nonnull
        @Override
        public Map<String, IPeripheral> getAvailablePeripherals() {
            if (!this.attached) {
                throw new NotAttachedException();
            }

            Map<String, IPeripheral> peripherals = new HashMap<>();
            for (PeripheralWrapper wrapper : PeripheralAPI.this.peripherals) {
                if (wrapper != null && wrapper.isAttached()) {
                    peripherals.put(wrapper.getAttachmentName(), wrapper.getPeripheral());
                }
            }

            return Collections.unmodifiableMap(peripherals);
        }

        @Nullable
        @Override
        public IPeripheral getAvailablePeripheral(@Nonnull String name) {
            if (!this.attached) {
                throw new NotAttachedException();
            }

            for (PeripheralWrapper wrapper : PeripheralAPI.this.peripherals) {
                if (wrapper != null && wrapper.isAttached() && wrapper.getAttachmentName()
                                                                      .equals(name)) {
                    return wrapper.getPeripheral();
                }
            }
            return null;
        }


    }
}
