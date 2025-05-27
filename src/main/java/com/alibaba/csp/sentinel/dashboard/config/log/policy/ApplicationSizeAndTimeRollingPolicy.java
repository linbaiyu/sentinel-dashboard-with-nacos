package com.alibaba.csp.sentinel.dashboard.config.log.policy;

import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.TimeBasedFileNamingAndTriggeringPolicy;

public class ApplicationSizeAndTimeRollingPolicy<E> extends SizeAndTimeBasedRollingPolicy<E> {
    public static TimeBasedFileNamingAndTriggeringPolicy<?> policy;

    public ApplicationSizeAndTimeRollingPolicy() {
    }

    @Override
    public void start() {
        super.start();
        policy = super.getTimeBasedFileNamingAndTriggeringPolicy();
    }
}
