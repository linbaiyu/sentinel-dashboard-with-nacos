package com.alibaba.csp.sentinel.dashboard.config.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ServiceLog {
    private ServiceLog() {
    }

    public static Manager manager() {
        return Manager.instance;
    }

    public static void reset() {
        MDC.clear();
    }

    public static ServiceLogBackup backup() {
        return new ServiceLogBackup(MDC.getCopyOfContextMap());
    }

    public static RecordChain record(ServiceLogField field, Object value) {
        String val = value == null ? "" : String.valueOf(value);
        MDC.put(field.getName(), val);
        return RecordChain.instance;
    }

    public static String getField(ServiceLogField field) {
        String value = MDC.get(field.getName());
        return value != null ? value : "";
    }

    public static Printer newPrinter(ServiceLogVar.ServiceLogVarWithValue... varWithValues) {
        Printer printer = new Printer();
        if (varWithValues != null) {
            Arrays.stream(varWithValues).forEach((e) -> {
                String var10000 = (String)printer.vars.put(e.getVar(), e.getValue());
            });
        }

        return printer;
    }

    public static class RecordChain {
        private static final RecordChain instance = new RecordChain();

        private RecordChain() {
        }

        public RecordChain record(ServiceLogField field, Object value) {
            ServiceLog.record(field, value);
            return this;
        }
    }

    public static class Manager {
        private static final Manager instance = new Manager();
        private Logger logger = LoggerFactory.getLogger(ServiceLog.class);
        private String delimiter = "$$";
        private ServiceLogVar[] logVarGroup = ServiceLogVar.ServiceLogVars.values();
        private BiFunction<Map<ServiceLogVar, String>, String, String> indexTipFun = (vars, index) -> {
            String traceID = ServiceLog.getField(ServiceLogField.ServiceLogFields.TRACE_ID);
            List<String> args = new ArrayList(vars.size());
            StringBuilder sb = new StringBuilder(" grep %s %s");
            args.add(traceID);
            args.add(index);
            String action = (String)vars.get(ServiceLogVar.ServiceLogVars.ACTION);
            if (action.length() > 0) {
                sb.append(" | grep %s");
                args.add(action);
            }

            String topic = (String)vars.get(ServiceLogVar.ServiceLogVars.TOPIC);
            if (topic.length() > 0) {
                sb.append(" | grep %s");
                args.add(topic);
            }

            return String.format(sb.append(" |less").toString(), args.toArray());
        };

        private Manager() {
        }

        public Manager setLogger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Manager setDelimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public Manager setLogVarGroup(ServiceLogVar... logVarGroup) {
            this.logVarGroup = logVarGroup;
            return this;
        }

        public Manager setIndexTipFun(BiFunction<Map<ServiceLogVar, String>, String, String> indexTipFun) {
            this.indexTipFun = indexTipFun;
            return this;
        }
    }

    public static class Printer {
        protected Logger logger;
        protected Map<ServiceLogVar, String> vars;
        protected BiFunction<Map<ServiceLogVar, String>, String, String> indexTipFun;
        private static final Pattern formatCountPattern = Pattern.compile("\\{}");

        public Printer() {
            this.logger = ServiceLog.manager().logger;
            this.vars = newVars();
            this.indexTipFun = ServiceLog.manager().indexTipFun;
        }

        protected static Map<ServiceLogVar, String> newVars() {
            return (Map)Arrays.stream(ServiceLog.manager().logVarGroup).sorted(Comparator.comparingInt(ServiceLogVar::getOrder)).collect(Collectors.toMap(Function.identity(), (o) -> {
                return "";
            }, (old, newVal) -> {
                return newVal;
            }, LinkedHashMap::new));
        }

        public Printer withLogger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Printer withVars(ServiceLogVar.ServiceLogVarWithValue... varWithValues) {
            Arrays.stream(varWithValues).forEach((e) -> {
                String var10000 = (String)this.vars.put(e.getVar(), e.getValue());
            });
            return this;
        }

        public Printer withIndexTipFun(BiFunction<Map<ServiceLogVar, String>, String, String> indexTipFun) {
            this.indexTipFun = indexTipFun;
            return this;
        }

        public void printInfo(String format, Object... args) {
            this.logger.info(this.prefix(format, Level.INFO), args);
        }

        public void printDebug(String format, Object... args) {
            this.logger.debug(this.prefix(format, Level.DEBUG), args);
        }

        public void printWarn(String format, Object... args) {
            this.logger.warn(this.prefix(format, Level.WARN), args);
        }

        public void printError(String format, Object... args) {
            this.logger.error(this.prefix(format, Level.ERROR), args);
        }

        public void printByLogWay(LogWay logWay, String easyInfoFormat, String infoFormat, String debugFormat, Supplier<?>... args) {
            this.printByLogWay(logWay, easyInfoFormat, infoFormat, debugFormat, (Object[])args);
        }

        public void printByLogWay(LogWay logWay, String easyInfoFormat, String infoFormat, String debugFormat, Object... args) {
            int easyInfoFormatCount = formatCount(easyInfoFormat);
            int infoFormatCount = formatCount(infoFormat);
            Object[] easyInfoArgs = Arrays.copyOfRange(args, 0, easyInfoFormatCount);
            Object[] infoArgs = Arrays.copyOfRange(args, easyInfoFormatCount, easyInfoFormatCount + infoFormatCount);
            Object[] debugArgs = Arrays.copyOfRange(args, easyInfoFormatCount + infoFormatCount, args.length);
            switch (logWay) {
                case SILENCE:
                default:
                    break;
                case ONLY_INFO:
                    this.printInfo(infoFormat, Arrays.stream(infoArgs).map(this::trySupplierGet).toArray());
                    break;
                case ONLY_DEBUG:
                    if (this.logger.isDebugEnabled()) {
                        this.printDebug(debugFormat, Arrays.stream(debugArgs).map(this::trySupplierGet).toArray());
                    }
                    break;
                case EASY_INFO_AND_DEBUG:
                    List<Object> argList = new ArrayList(args.length);
                    Arrays.stream(easyInfoArgs).map(this::trySupplierGet).forEach(argList::add);
                    Arrays.stream(debugArgs).map((arg) -> {
                        return this.logger.isDebugEnabled() ? this.trySupplierGet(arg) : null;
                    }).forEach(argList::add);
                    this.printIndexLog(easyInfoFormat, debugFormat, argList.toArray());
                    break;
                case INFO_AND_DEBUG:
                    this.printInfo(infoFormat, Arrays.stream(infoArgs).map(this::trySupplierGet).toArray());
                    if (this.logger.isDebugEnabled()) {
                        this.printDebug(debugFormat, Arrays.stream(debugArgs).map(this::trySupplierGet).toArray());
                    }
            }

        }

        private Object trySupplierGet(Object arg) {
            return arg instanceof Supplier ? ((Supplier)arg).get() : arg;
        }

        public void printIndexLog(String infoFormat, String debugFormat, Object... args) {
            int infoFormatCount = formatCount(infoFormat);
            Object[] infoArgs = Arrays.copyOfRange(args, 0, infoFormatCount);
            Object[] debugArgs = Arrays.copyOfRange(args, infoFormatCount, args.length);
            this.logger.info(this.prefix(infoFormat, Level.INFO) + this.indexTip(), infoArgs);
            if (this.logger.isDebugEnabled()) {
                this.logger.debug(this.prefix(debugFormat, Level.DEBUG), debugArgs);
            }

        }

        public void printIndexTip(String infoFormat, Object... args) {
            this.logger.info(this.prefix(infoFormat, Level.INFO) + this.indexTip(), args);
        }

        protected String prefix(String format, Level level) {
            StringBuilder sb = new StringBuilder();
            this.vars.forEach((k, v) -> {
                if (k.getLevels().contains(level)) {
                    sb.append(v).append(ServiceLog.manager().delimiter);
                }

            });
            sb.append(format);
            return sb.toString();
        }

        protected String indexTip() {
            return (String)this.indexTipFun.apply(this.vars, LogRollingHelper.getDebugLogFileName());
        }

        protected static int formatCount(String format) {
            Matcher m = formatCountPattern.matcher(format);

            int count;
            for(count = 0; m.find(); ++count) {
            }

            return count;
        }
    }

    public static class ServiceLogBackup {
        private final Map<String, String> contextMap;

        public ServiceLogBackup(Map<String, String> contextMap) {
            this.contextMap = contextMap;
        }

        public void recover() {
            Map<String, String> currentContextMap = MDC.getCopyOfContextMap();
            HashMap<String, String> result = new HashMap(this.contextMap);
            if (currentContextMap != null) {
                result.putAll(currentContextMap);
            }

            MDC.setContextMap(result);
        }
    }
}
