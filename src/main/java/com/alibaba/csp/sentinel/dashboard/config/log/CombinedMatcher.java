package com.alibaba.csp.sentinel.dashboard.config.log;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 可组合的匹配器
 * <hr>
 * <pre>
 *     CombinedMatcher<{@link String}>; A, B, C = new ("A"), new ("B"), new ("C");
 *     A.match("A"); // true
 *     A.match("A", "B"); // true
 *     A.match("B"); // false
 *     A.or(B).match("B"); // true
 *     A.and(B).match("B"); // false
 *     A.and(B).match("A", "B"); // false
 *     </pre><hr><pre>
 *     CombinedMatchThen&lt;Object, BigDecimal&gt; matcher = A.or(B).onMatch(BigDecimal.ONE);
 *     if (matcher.match("A", "C")) {
 *         matcher.then(); // BigDecimal.ONE
 *     }
 * </pre>
 *
 */
public interface CombinedMatcher<T> {

    /**
     * 匹配
     *
     * @param arg 参数
     * @return boolean
     */
    boolean match(T arg);

    /**
     * 接受匹配的类型，用于{@link #safeMatch(Object)}
     *
     * @return {@link Class}<{@link T}>
     */
    Class<T> accept();

    /**
     * 用于描述匹配器的字符串
     */
    String desc();

    /**
     * 检查输入的一组标志是否可以匹配当前标志组
     * <pre>
     *     matcher1 = A.and(B);
     *     matcher1.match(A); // false
     *     matcher1.match(A, B); // true
     *
     *     matcher2 = A.or(B.and(C));
     *     matcher2.match(A); // true
     *     matcher2.match(B); // false
     *     matcher2.match(B, C); // true
     *
     *     matcher3 = A.and(B.or(C));
     *     matcher3.match(A); // false
     *     matcher3.match(B); // false
     *     matcher3.match(C); // false
     *     matcher3.match(A, B); // true
     *     matcher3.match(A, C); // true
     * </pre>
     *
     * @return boolean
     */
    default boolean match(T... args) {
        return args != null && (args.length == 1 ? safeMatch(args[0]) : Arrays.stream(args).anyMatch(this::safeMatch));
    }


    /**
     * 安全地匹配，如果参数的类型和{@link #accept()}不符，视为匹配失败
     *
     * @param arg 参数
     * @return boolean
     */
    @SuppressWarnings("unchecked")
    default boolean safeMatch(Object arg) {
        return accept().isInstance(arg) && match((T) arg);
    }

    /**
     * 安全地匹配，如果参数的类型和{@link #accept()}不符，视为匹配失败
     *
     * @param args 参数
     * @return boolean
     */
    default boolean safeMatch(Object... args) {
        return args != null && (args.length == 1 ? safeMatch(args[0]) : Arrays.stream(args).anyMatch(this::safeMatch));
    }

    /**
     * 得到一个表示同时满足多个匹配的组合匹配
     * <pre>
     *     D = A.and(B, C) // D mean (A and B and C)
     * </pre>
     *
     * @param matchers 要合并的匹配
     * @return 匹配合并后的组合匹配
     */
    default CombinedMatcher<Object> and(CombinedMatcher<?>... matchers) {
        return new CombinedAndMatcher(this, matchers);
    }

    /**
     * 得到一个表示满足多个匹配其一的组合匹配
     * <pre>
     *     D = A.or(B, C) // D mean (A or B or C)
     * </pre>
     *
     * @param matchers 要合并的匹配
     * @return 匹配合并后的组合匹配
     */
    default CombinedMatcher<Object> or(CombinedMatcher<?>... matchers) {
        return new CombinedOrMatcher(this, matchers);
    }

    /**
     * 得到一个表示不满足原匹配的匹配
     * <pre>
     *     C = A.not() // C mean (not A)
     * </pre>
     *
     * @return 组合匹配
     */
    default CombinedMatcher<T> not() {
        return new CombinedNotMatcher(this);
    }

    /**
     * 如果匹配到会怎样
     *
     * @param then 做一些事或提供一个值
     * @param <R> 匹配后提供的值
     * @return {@link CombinedMatchThen}<{@link T}, {@link R}>
     * @see CombinedMatchThen#match(Object[])
     * @see CombinedMatchThen#then()
     */
    default <R> CombinedMatchThen<T, R> onMatch(Supplier<R> then) {
        return new CombinedMatchThen<>(this, then);
    }

    /**
     * 如果匹配到会怎样
     *
     * @param then 提供一个值
     * @param <R> 匹配后提供的值
     * @return {@link R}
     * @see CombinedMatchThen#match(Object[])
     * @see CombinedMatchThen#then()
     */
    default <R> CombinedMatchThen<T, R> onMatch(R then) {
        return new CombinedMatchThen<>(this, then);
    }

    class MatcherBase implements CombinedMatcher<Object> {

        @Override
        public String desc() {
            return "default";
        }

        @Override
        @SuppressWarnings("unchecked")
        public CombinedMatcher<Object> and(CombinedMatcher<?>... matchers) {
            if (matchers != null && matchers.length > 0) {
                return matchers.length == 1 ? (CombinedMatcher<Object>) matchers[0] : CombinedAndMatcher.include(matchers);
            }
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public CombinedMatcher<Object> or(CombinedMatcher<?>... matchers) {
            if (matchers != null && matchers.length > 0) {
                return matchers.length == 1 ? (CombinedMatcher<Object>) matchers[0] : CombinedOrMatcher.include(matchers);
            }
            return this;
        }

        @Override
        public boolean match(Object arg) {
            return true;
        }

        @Override
        public Class<Object> accept() {
            return Object.class;
        }
    }

    /**
     * 用于储存匹配条件和对应的匹配奖励<p>
     * T 匹配的类型<p>
     * R 匹配后奖励的类型
     */
    class CombinedMatchThen<T, R> {

        protected final CombinedMatcher<T> matcher;
        protected final Supplier<R> then;
        protected final R thenValue;

        public CombinedMatchThen(CombinedMatcher<T> matcher, Supplier<R> then) {
            this.matcher = matcher;
            this.then = then;
            this.thenValue = null;
        }

        public CombinedMatchThen(CombinedMatcher<T> matcher, R thenValue) {
            this.matcher = matcher;
            this.then = null;
            this.thenValue = thenValue;
        }

        /**
         * 是否匹配
         *
         * @param args 参数
         * @return boolean
         */
        @SafeVarargs
        public final boolean match(T... args) {
            return matcher.match(args);
        }

        /**
         * 匹配之后怎样
         *
         * @return {@link R} 返回的对象的类型
         */
        public R then() {
            return thenValue != null ? thenValue : then != null ? then.get() : null;
        }

        @Override
        public String toString() {
            return matcher.desc() + " then " + thenValue;
        }

    }

    /**
     * "与"匹配器
     */
    class CombinedAndMatcher implements CombinedMatcher<Object> {

        private final CombinedMatcher<?>[] ands;

        public CombinedAndMatcher(CombinedMatcher<?>... matchers) {
            ands = matchers;
        }

        public CombinedAndMatcher(CombinedMatcher<?>[] matchers1, CombinedMatcher<?>... matchers2) {
            ands = Arrays.copyOf(matchers1, matchers1.length + matchers2.length);
            for (int i = 0; i < matchers2.length; i++) {
                ands[i + matchers1.length] = matchers2[i];
            }
        }

        public CombinedAndMatcher(CombinedMatcher<?> self, CombinedMatcher<?>... matchers) {
            ands = new CombinedMatcher[matchers.length + 1];
            ands[0] = self;
            for (int i = 0; i < matchers.length; i++) {
                ands[i + 1] = matchers[i];
            }
        }

        public static CombinedMatcher<Object> include(CombinedMatcher<?>... matchers) {
            return new CombinedAndMatcher(matchers);
        }

        @Override
        public CombinedMatcher<Object> and(CombinedMatcher<?>... matchers) {
            return new CombinedAndMatcher(ands, matchers);
        }

        @Override
        public boolean match(Object arg) {
            return safeMatch(arg);
        }

        @Override
        public boolean match(Object... args) {
            return safeMatch(args);
        }

        @Override
        public boolean safeMatch(Object arg) {
            return Arrays.stream(ands).allMatch(one -> one.safeMatch(arg));
        }

        @Override
        public boolean safeMatch(Object... args) {
            return Arrays.stream(ands).allMatch(one -> one.safeMatch(args));
        }

        @Override
        public Class<Object> accept() {
            return Object.class;
        }

        @Override
        public String desc() {
            return "(" + Arrays.stream(ands).map(CombinedMatcher::desc).collect(Collectors.joining(" & ")) + ")";
        }
    }


    /**
     * "或"匹配器
     */
    class CombinedOrMatcher implements CombinedMatcher<Object> {

        private final CombinedMatcher<?>[] ors;

        public CombinedOrMatcher(CombinedMatcher<?>... matchers) {
            ors = matchers;
        }
        public CombinedOrMatcher(CombinedMatcher<?>[] matchers1, CombinedMatcher<?>... matchers2) {
            ors = Arrays.copyOf(matchers1, matchers1.length + matchers2.length);
            for (int i = 0; i < matchers2.length; i++) {
                ors[i + matchers1.length] = matchers2[i];
            }
        }

        public CombinedOrMatcher(CombinedMatcher<?> self, CombinedMatcher<?>... matchers) {
            ors = new CombinedMatcher[matchers.length + 1];
            ors[0] = self;
            for (int i = 0; i < matchers.length; i++) {
                ors[i + 1] = matchers[i];
            }
        }

        public static CombinedMatcher<Object> include(CombinedMatcher<?>... matchers) {
            return new CombinedOrMatcher(matchers);
        }

        @Override
        public CombinedMatcher<Object> or(CombinedMatcher<?>... matchers) {
            return new CombinedOrMatcher(ors, matchers);
        }

        @Override
        public boolean match(Object arg) {
            return safeMatch(arg);
        }

        @Override
        public boolean match(Object... args) {
            return safeMatch(args);
        }

        @Override
        public boolean safeMatch(Object arg) {
            return Arrays.stream(ors).anyMatch(one -> one.safeMatch(arg));
        }

        @Override
        public boolean safeMatch(Object... args) {
            return Arrays.stream(ors).anyMatch(one -> one.safeMatch(args));
        }

        @Override
        public Class<Object> accept() {
            return Object.class;
        }

        @Override
        public String desc() {
            return "[" + Arrays.stream(ors).map(CombinedMatcher::desc).collect(Collectors.joining(" | ")) + "]";
        }
    }

    /**
     * "非"匹配器
     */
    class CombinedNotMatcher<T> implements CombinedMatcher<T> {

        private final CombinedMatcher<T> origin;

        public CombinedNotMatcher(CombinedMatcher<T> matcher) {
            origin = matcher;
        }

        @Override
        public CombinedMatcher<T> not() {
            return origin;
        }

        @Override
        public boolean match(T arg) {
            return !origin.match(arg);
        }

        @SafeVarargs
        @Override
        public final boolean match(T... args) {
            return !origin.match(args);
        }

        @Override
        public boolean safeMatch(Object arg) {
            return !origin.safeMatch(arg);
        }

        @Override
        public boolean safeMatch(Object... args) {
            return !origin.safeMatch(args);
        }

        @Override
        public Class<T> accept() {
            return origin.accept();
        }

        @Override
        public String desc() {
            return "!" + origin.desc();
        }
    }

    /**
     * 标签匹配器，使用字符串进行匹配，判断字符串是否相等
     * <li>可被枚举直接实现，匹配时需使用枚举字段名</li>
     * <pre>
     *     public enum Tag implements TagMatcher {
     *         A, B, C
     *     }
     *     A.match("A"); // true
     *     A.match(Tag.A); // false
     *     // 如果想直接使用枚举值匹配，使用{@link EnumMatcher}
     * </pre>
     * <li>也可以通过{@link TagMatcher#tag(String)}快速构造</li>
     * <pre>
     *     import static TagMatcher.*;
     *
     *     matcher = tag("A").and(tag("B"));
     *     matcher.match("A"); // false
     *     matcher.match("A", "B"); // true
     *     matcher.match("A", "B", "C"); // true
     * </pre>
     * <li>可通过重写{@link TagMatcher#match(String)}方法来修改匹配规则</li>
     * <pre>
     *     public PrefixMatcher implements TagMatcher {
     *
     *         private String prefix;
     *
     *         /@Override
     *         public String name() {
     *             return prefix;
     *         }
     *
     *         /@Override
     *         public boolean match(String arg) {
     *             return arg != null && arg.startWith(name());
     *         }
     *     }
     *     或者直接实现超类
     *     public PrefixMatcher implements {@link CombinedMatcher}<{@link String}> {
     *
     *         private String prefix;
     *
     *         /@Override
     *         public boolean match(String arg) {
     *             return arg != null && arg.startWith(prefix);
     *         }
     *
     *         /@Override
     *         public Class<{@link String}> accept() {
     *             return String.class;
     *         }
     *
     *     }
     * </pre>
     */
    @FunctionalInterface
    interface TagMatcher extends CombinedMatcher<String> {

        /**
         * 标签名
         *
         * @return {@link String}
         */
        String name();

        /**
         * 快速构造一个标签匹配器
         *
         * @param name 标签名
         * @return {@link TagMatcher}
         */
        static TagMatcher tag(String name) {
            return () -> name;
        }

        @Override
        default String desc() {
            return name();
        }

        @Override
        default boolean match(String arg) {
            return name().equals(arg);
        }

        @Override
        default Class<String> accept() {
            return String.class;
        }
    }
    @FunctionalInterface
    interface PrefixMatcher extends CombinedMatcher<String> {

        /**
         * 前缀
         *
         * @return {@link String}
         */
        String prefix();

        /**
         * 快速构造一个前缀匹配器
         *
         * @param prefix 前缀
         * @return {@link TagMatcher}
         */
        static TagMatcher prefix(String prefix) {
            return () -> prefix;
        }

        @Override
        default String desc() {
            return prefix();
        }

        @Override
        default boolean match(String arg) {
            String prefix = prefix();
            return prefix != null && prefix.startsWith((arg));
        }

        @Override
        default Class<String> accept() {
            return String.class;
        }
    }

    /**
     * 数字匹配器，用于匹配数字类型
     * <li>可以使用{@link #number(Number)}快速构造</li>
     * <pre>
     *     import static NumberMatcher.*;
     *
     *     matcher = number(1.0).or(number(3));
     *     matcher.match(2); // false
     *     matcher.match(1); // true
     *     matcher.match(1, 2); // true
     * </pre>
     * <li>可以自定义类实现，提供一个number方法即可</li>
     * <pre>
     *     class Level implements NumberMatcher {
     *
     *         private Number number;
     *
     *         public Level(Number number) {
     *             this.number = number;
     *         }
     *
     *         /@Override
     *         public Number number() {
     *             return number;
     *         }
     *     }
     *     // 匹配的等级列表中包含2或3，但是不能有5
     *     CombinedMatcher matcher = new Level(2.0).or(new Level(3.0)).and(new Level(5.0).not());
     *     matcher.match(2.0); // true
     *     matcher.match(3.0, 4.0); // true
     *     matcher.match(3.0, 5.0); // false
     * </pre>
     * <li>可重写{@link NumberMatcher#match(Number)}方法修改匹配逻辑</li>
     * <pre>
     *     class AgeLimit implements NumberMatcher {
     *
     *         private Number number;
     *
     *         public AgeLimit(Number number) {
     *             this.number = number;
     *         }
     *
     *         /@Override
     *         public Number number() {
     *             return number;
     *         }
     *
     *         /@Override
     *         public boolean match(Number arg) {
     *         // 修改匹配方法为：大于等于
     *             return arg.intValue() >= number.intValue();
     *         }
     *     }
     *     // 匹配大于等于12岁，小于18岁
     *     CombinedMatcher matcher = new AgeLimit(12.0).and(new AgeLimit(18.0).not());
     *     matcher.match(12); // false
     *     matcher.match(13); // true
     *     matcher.match(19); // false
     * </pre>
     */
    @FunctionalInterface
    interface NumberMatcher extends CombinedMatcher<Number> {

        /**
         * 用于匹配的数字
         *
         * @return {@link Number}
         */
        Number number();

        /**
         * 快速提供一个数字匹配器
         *
         * @param number 数字
         * @return {@link NumberMatcher}
         */
        static NumberMatcher number(Number number) {
            return () -> number;
        }

        @Override
        default String desc() {
            return String.valueOf(number());
        }

        @Override
        default boolean match(Number arg) {
            return number().doubleValue() == arg.doubleValue();
        }

        @Override
        default Class<Number> accept() {
            return Number.class;
        }
    }

    /**
     * 枚举匹配器
     * <pre>
     *     public enum Level implements EnumMatcher&lt;Level&gt; {
     *         A, B, C
     *     }
     *     // 同时满足A和B，或者满足C即可
     *     CombinedMatcher matcher = A.and(B).or(C); // [(A & B) | C]
     *     matcher.match(A); // false
     *     matcher.match(A, B); // true
     *     matcher.match(C); // true
     *     matcher.match(A, C); // true
     * </pre>
     */
    interface EnumMatcher<T extends Enum<T>> extends CombinedMatcher<T> {

        /**
         * 枚举成员名
         *
         * @return {@link String}
         */
        String name();

        /**
         * 得到枚举的类型
         *
         * @return {@link Class}<{@link T}>
         */
        Class<T> getDeclaringClass();

        @Override
        default boolean match(T arg) {
            return name().equals(arg.name());
        }

        @Override
        default Class<T> accept() {
            return getDeclaringClass();
        }

        @Override
        default String desc() {
            return name();
        }

    }

}
