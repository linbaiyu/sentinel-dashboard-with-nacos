package com.alibaba.csp.sentinel.dashboard.config.log;

import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public interface ServiceLogVar {
    int getOrder();

    Set<Level> getLevels();

    default ServiceLogVarWithValue with(Object value) {
        return new ServiceLogVarWithValue(this, String.valueOf(value));
    }

    public static class ServiceLogVarWithValue {
        private final ServiceLogVar var;
        private final String value;

        public ServiceLogVarWithValue(ServiceLogVar var, String value) {
            this.var = var;
            this.value = value;
        }

        public ServiceLogVar getVar() {
            return this.var;
        }

        public String getValue() {
            return this.value;
        }
    }

    public static enum ServiceLogVars implements ServiceLogVar {
        ACTION(1, new Level[]{Level.INFO, Level.DEBUG, Level.WARN, Level.ERROR}),
        TOPIC(2, new Level[]{Level.INFO, Level.DEBUG, Level.WARN, Level.ERROR}),
        COST(3, new Level[]{Level.INFO, Level.ERROR});

        private final int order;
        private final Set<Level> levels;

        private ServiceLogVars(int order, Level... levels) {
            this.order = order;
            this.levels = (Set)(levels == null ? Collections.emptySet() : new HashSet(Arrays.asList(levels)));
        }

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public Set<Level> getLevels() {
            return this.levels;
        }
    }
}
