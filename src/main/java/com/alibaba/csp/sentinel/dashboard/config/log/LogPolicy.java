package com.alibaba.csp.sentinel.dashboard.config.log;

import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static jdk.nashorn.internal.objects.NativeFunction.bind;

/**
 * 日志策略，根据日志状态判断日志输出方式
 */
public abstract class LogPolicy {

    private final String policyName;

    private List<CombinedMatcher.CombinedMatchThen<Object, LogWay>> policies = new ArrayList<>();

    public LogPolicy(String policyName) {
        this.policyName = policyName;
    }

    protected LogWay defaultLogWay() {
        return LogWay.EASY_INFO_AND_DEBUG;
    }

    /**
     * 配置日志策略
     * <p>后配置的匹配器优先级更高</p>
     * <pre>
     *     addPolicy(REQUEST, LogWay.ONLY_INFO));
     *     addPolicy(REQUEST.and(METHOD_A), LogWay.ONLY_DEBUG));
     *     addPolicy(REQUEST.and(METHOD_B.or(METHOD_C)), LogWay.INFO_AND_DEBUG));
     * </pre>
     *
     * @param matcher 匹配器
     * @param logWay 输出方式
     * @return {@link LogPolicy}
     */
    public <T extends LogPolicy> T addPolicy(CombinedMatcher<?> matcher, LogWay logWay) {
        policies.add(0, (CombinedMatcher.CombinedMatchThen<Object, LogWay>) matcher.onMatch(logWay));
        return (T) this;
    }

    public <T extends LogPolicy> T bindProperties(Supplier<List<? extends LogPolicyProperties>> propertiesSupplier) {
        bind(propertiesSupplier);
        return (T) this;
    }

    public <T extends LogPolicy> T bindProperties(Supplier<List<? extends LogPolicyProperties>> propertiesSupplier, long intervalSecond) {
        bind(propertiesSupplier, Math.toIntExact(intervalSecond));
        return (T) this;
    }

    public void updatePolicies(List<CombinedMatcher.CombinedMatchThen<Object, LogWay>> policies) {
        this.policies = policies;
    }

    protected void onUpdate(List<? extends LogPolicyProperties> data, boolean isUpdateByBind) {
        if (isUpdateByBind) {
            initPoliciesByProperties(data);
        } else {
            updatePoliciesByProperties(data);
        }
    }

    public void initPoliciesByProperties(List<? extends LogPolicyProperties> logPolicyPropertiesList) {
        if (logPolicyPropertiesList != null && !logPolicyPropertiesList.isEmpty()) {
            updatePolicies(logPolicyPropertiesList.stream().map(LogPolicyProperties::toMatchThen).collect(Collectors.toList()));
        }
        ServiceLog.Printer printer = ServiceLog.newPrinter(ServiceLogVar.ServiceLogVars.ACTION.with("NACOS"), ServiceLogVar.ServiceLogVars.TOPIC.with(policyName));
        printer.printInfo("init by {}, result -> {}", JSON.toJSONString(logPolicyPropertiesList), printPolicies());
    }

    public void updatePoliciesByProperties(List<? extends LogPolicyProperties> logPolicyPropertiesList) {
        String before = printPolicies();
        if (logPolicyPropertiesList != null && !logPolicyPropertiesList.isEmpty()) {
            updatePolicies(logPolicyPropertiesList.stream().map(LogPolicyProperties::toMatchThen).collect(Collectors.toList()));
        }
        ServiceLog.Printer printer = ServiceLog.newPrinter(ServiceLogVar.ServiceLogVars.ACTION.with("NACOS"), ServiceLogVar.ServiceLogVars.TOPIC.with(policyName));
        printer.printInfo("update by {}, before {}, result -> {}", JSON.toJSONString(logPolicyPropertiesList), before, printPolicies());
    }

    /**
     * 获取日志的输出方式
     *
     * @param args 参数，用于根据已配置好的策略来匹配对应的输出方式
     * @return {@link LogWay}
     */
    public LogWay getLogWay(Object... args) {
        return policies.stream().filter(matcher -> matcher.match(args)).findFirst().map(CombinedMatcher.CombinedMatchThen::then).orElseGet(this::defaultLogWay);
    }

    public String printPolicies() {
        return policies.stream().map(CombinedMatcher.CombinedMatchThen::toString).collect(Collectors.joining(", "));
    }

    public interface LogPolicyProperties {
        CombinedMatcher.CombinedMatchThen<Object, LogWay> toMatchThen();
    }

    public interface LogPolicyPropertiesBinder<T extends LogPolicy> {

        void bindPolicy(T policy);
    }
}