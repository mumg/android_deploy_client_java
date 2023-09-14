package io.appservice.core.statemachine;

public interface StateProcessQueue {
    void push(int priority, Runnable task);
}
