package com.alibaba.csp.sentinel.dashboard.config.log;

import ch.qos.logback.core.rolling.TimeBasedFileNamingAndTriggeringPolicy;
import com.alibaba.csp.sentinel.dashboard.config.log.policy.ApplicationSizeAndTimeRollingPolicy;
import com.alibaba.csp.sentinel.dashboard.config.log.policy.DebugSizeAndTimeRollingPolicy;

import java.util.Optional;

public class LogRollingHelper {
    public LogRollingHelper() {
    }

    public static String getDebugLogFileName() {
        return (String)Optional.ofNullable(DebugSizeAndTimeRollingPolicy.policy).map(TimeBasedFileNamingAndTriggeringPolicy::getCurrentPeriodsFileNameWithoutCompressionSuffix).map(LogRollingHelper::absToRelativePath).orElse("");
    }

    public static String getApplicationLogFileName() {
        return (String)Optional.ofNullable(ApplicationSizeAndTimeRollingPolicy.policy).map(TimeBasedFileNamingAndTriggeringPolicy::getCurrentPeriodsFileNameWithoutCompressionSuffix).map(LogRollingHelper::absToRelativePath).orElse("");
    }

    private static String absToRelativePath(String absPath) {
        int index = absPath.lastIndexOf("/");
        return absPath.substring(index + 1);
    }
}
