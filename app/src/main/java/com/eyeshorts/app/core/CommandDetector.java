package com.eyeshorts.app.core;

public interface CommandDetector {
    GazeTrigger.Update update(GazeSample sample, boolean allowed);
    void reset();
}
