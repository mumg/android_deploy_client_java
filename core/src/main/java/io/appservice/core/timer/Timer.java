package io.appservice.core.timer;

public interface Timer {
    void start(Long timestamp, Runnable runnable);
    void stop(Long timestamp);
    void dump ();
}
