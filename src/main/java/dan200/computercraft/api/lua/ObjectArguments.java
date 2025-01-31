/*
 * This file is part of the public ComputerCraft API - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2021. This API may be redistributed unmodified and in full only.
 * For help using the API, and posting your mods, visit the forums at computercraft.info.
 */

package dan200.computercraft.api.lua;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * An implementation of {@link IArguments} which wraps an array of {@link Object}.
 */
public final class ObjectArguments implements IArguments {
    private static final IArguments EMPTY = new ObjectArguments();
    private final List<Object> args;

    @Deprecated
    @SuppressWarnings ("unused")
    public ObjectArguments(IArguments arguments) {
        throw new IllegalStateException();
    }

    public ObjectArguments(Object... args) {
        this.args = Arrays.asList(args);
    }

    public ObjectArguments(List<Object> args) {
        this.args = Objects.requireNonNull(args);
    }

    @Override
    public IArguments drop(int count) {
        if (count < 0) {
            throw new IllegalStateException("count cannot be negative");
        }
        if (count == 0) {
            return this;
        }
        if (count >= this.args.size()) {
            return EMPTY;
        }

        return new ObjectArguments(this.args.subList(count, this.args.size()));
    }

    @Override
    public Object[] getAll() {
        return this.args.toArray();
    }

    @Override
    public int count() {
        return this.args.size();
    }

    @Nullable
    @Override
    public Object get(int index) {
        return index >= this.args.size() ? null : this.args.get(index);
    }
}
