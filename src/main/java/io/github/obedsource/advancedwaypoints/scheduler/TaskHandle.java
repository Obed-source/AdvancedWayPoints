package io.github.obedsource.advancedwaypoints.scheduler;

/** A repeating task that can be cancelled. */
@FunctionalInterface
public interface TaskHandle {

    void cancel();
}
