package com.alibaba.csp.sentinel.dashboard.config.log;

public interface ServiceLogField {
    String getName();

    public static enum ServiceLogFields implements ServiceLogField {
        TRACE_ID("traceID"),
        PATH("path"),
        GROUP_ID("groupID"),
        SHOP_ID("shopID"),
        USER("user"),
        ORDER_KEY("orderKey"),
        EXTENSION("extension");

        private final String name;

        private ServiceLogFields(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }
}
