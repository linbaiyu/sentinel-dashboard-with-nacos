package com.alibaba.csp.sentinel.dashboard.config.log;

public enum LogWay {
    SILENCE,
    ONLY_INFO,
    ONLY_DEBUG,
    EASY_INFO_AND_DEBUG,
    INFO_AND_DEBUG;

    private LogWay() {
    }
}
