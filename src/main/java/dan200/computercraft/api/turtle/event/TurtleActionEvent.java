/*
 * This file is part of the public ComputerCraft API - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2021. This API may be redistributed unmodified and in full only.
 * For help using the API, and posting your mods, visit the forums at computercraft.info.
 */

package dan200.computercraft.api.turtle.event;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleCommandResult;

/**
 * An event fired when a turtle is performing a known action.
 */
public class TurtleActionEvent extends TurtleEvent {
    private final TurtleAction action;
    private String failureMessage;
    private boolean cancelled = false;

    public TurtleActionEvent(@Nonnull ITurtleAccess turtle, @Nonnull TurtleAction action) {
        super(turtle);

        Objects.requireNonNull(action, "action cannot be null");
        this.action = action;
    }

    public TurtleAction getAction() {
        return this.action;
    }

    /**
     * Sets the cancellation state of this action.
     *
     * If {@code cancel} is {@code true}, this action will not be carried out.
     *
     * @param cancel The new canceled value.
     * @see TurtleCommandResult#failure()
     * @deprecated Use {@link #setCanceled(boolean, String)} instead.
     */
    @Deprecated
    public void setCanceled(boolean cancel) {
        this.setCanceled(cancel, null);
    }

    /**
     * Set the cancellation state of this action, setting a failure message if required.
     *
     * If {@code cancel} is {@code true}, this action will not be carried out.
     *
     * @param cancel The new canceled value.
     * @param failureMessage The message to return to the user explaining the failure.
     * @see TurtleCommandResult#failure(String)
     */
    public void setCanceled(boolean cancel, @Nullable String failureMessage) {
        this.cancelled = true;
        this.failureMessage = cancel ? failureMessage : null;
    }

    /**
     * Get the message with which this will fail.
     *
     * @return The failure message.
     * @see TurtleCommandResult#failure()
     * @see #setCanceled(boolean, String)
     */
    @Nullable
    public String getFailureMessage() {
        return this.failureMessage;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }
}
