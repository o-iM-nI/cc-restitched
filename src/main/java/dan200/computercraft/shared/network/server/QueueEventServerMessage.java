/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2021. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */

package dan200.computercraft.shared.network.server;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dan200.computercraft.shared.computer.core.IContainerComputer;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.util.NBTUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;

/**
 * Queue an event on a {@link ServerComputer}.
 *
 * @see dan200.computercraft.shared.computer.core.ClientComputer#queueEvent(String)
 * @see ServerComputer#queueEvent(String)
 */
public class QueueEventServerMessage extends ComputerServerMessage {
    private String event;
    private Object[] args;

    public QueueEventServerMessage(int instanceId, @Nonnull String event, @Nullable Object[] args) {
        super(instanceId);
        this.event = event;
        this.args = args;
    }

    public QueueEventServerMessage() {
    }

    @Override
    public void toBytes(@Nonnull PacketByteBuf buf) {
        super.toBytes(buf);
        buf.writeString(this.event);
        buf.writeCompoundTag(this.args == null ? null : NBTUtil.encodeObjects(this.args));
    }

    @Override
    public void fromBytes(@Nonnull PacketByteBuf buf) {
        super.fromBytes(buf);
        this.event = buf.readString(Short.MAX_VALUE);

        CompoundTag args = buf.readCompoundTag();
        this.args = args == null ? null : NBTUtil.decodeObjects(args);
    }

    @Override
    protected void handle(@Nonnull ServerComputer computer, @Nonnull IContainerComputer container) {
        computer.queueEvent(this.event, this.args);
    }
}
