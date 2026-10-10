package pub.module.mqevent;

import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 从 {@code XxxConsumer} 契约接口 / 方法 / 包路径自动推导 MQ 命名（destination、function 等）。
 * <p>
 * {@link MqChannel} / {@link MqSubscribe} 属性留空时启用；显式填写则优先使用（兼容历史契约）。
 */
public final class MqNamingSupport {

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final String CONSUMER_SUFFIX = "Consumer";

    private MqNamingSupport() {
    }

    /** 广播契约须位于 {@code pub.module.{module}.api.subscribe}；runner 冒烟契约在 {@code pub.module.{module}.subscribe}。 */
    public static String resolveModule(Class<?> type) {
        String name = type.getName();
        if (name.startsWith("pub.module.")) {
            String rest = name.substring("pub.module.".length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
                String module = rest.substring(0, dot);
                if (rest.contains(".api.") || rest.startsWith(module + ".subscribe")) {
                    return validateModuleKey(module);
                }
            }
        }
        throw new IllegalArgumentException("无法从类路径推断 MQ module: " + name);
    }

    /** Handler 所在业务模块（{@code pub.module.{module}.biz...}）。 */
    public static String resolveHandlerModule(String handlerClassName) {
        if (handlerClassName.startsWith("pub.module.")) {
            String rest = handlerClassName.substring("pub.module.".length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
                return validateModuleKey(rest.substring(0, dot));
            }
        }
        throw new IllegalArgumentException("无法从 Handler 类路径推断 module: " + handlerClassName);
    }

    /** Handler 订阅 function：{@code {prefix}{RootConsumerBase}}，group 与 module 同源。 */
    public static String resolveHandlerFunction(String handlerClassName, Class<?> rootContract) {
        String module = resolveHandlerModule(handlerClassName);
        String rootBase = stripConsumerSuffix(rootContract.getSimpleName());
        return functionPrefix(module) + rootBase;
    }

    /**
     * destination = {@code {module}.{subject-kebab}.{action}}，action 为 handler 方法 camel 拆分后的最后一个单词。
     * <p>例：{@code system} + {@code onUserLogin} → {@code system.user.login}
     */
    public static String resolveDestination(Class<?> rootContract, Method handlerMethod) {
        String module = resolveModule(rootContract);
        String suffix = methodToDestinationSuffix(handlerMethod.getName());
        return module + "." + suffix;
    }

    /**
     * producerFunction = {@code {module}{MethodBase}}（camelCase）。
     * <p>例：{@code system} + {@code onUserLogin} → {@code systemUserLogin}
     */
    public static String resolveProducerFunction(Class<?> rootContract, Method handlerMethod) {
        String module = resolveModule(rootContract);
        String methodBase = stripOnPrefix(handlerMethod.getName());
        return module + upperFirst(methodBase);
    }

    /**
     * group = 槽位接口所在包的 module 键；嵌套短名槽位（如 {@code Dating}）取简单类名小写。
     */
    public static String resolveGroup(Class<?> slotInterface) {
        if (!slotInterface.isInterface()) {
            throw new IllegalArgumentException("槽位须为接口: " + slotInterface.getName());
        }
        String simple = slotInterface.getSimpleName();
        if (!simple.endsWith(CONSUMER_SUFFIX) && !simple.contains(".")) {
            return validateModuleKey(simple.toLowerCase(Locale.ROOT));
        }
        return resolveModule(slotInterface);
    }

    /**
     * function = 槽位接口简单类名转 lowerCamel（去 Consumer 后缀），或 {@code {prefix}{RootBase}}。
     * <p>例：{@code ParentLedSystemUserLoginConsumer} → {@code parentLedSystemUserLogin}
     */
    public static String resolveFunction(Class<?> slotInterface, Class<?> rootContract) {
        String simple = slotInterface.getSimpleName();
        if (simple.endsWith(CONSUMER_SUFFIX)) {
            return lowerFirst(stripConsumerSuffix(simple));
        }
        String rootBase = stripConsumerSuffix(rootContract.getSimpleName());
        return functionPrefix(resolveGroup(slotInterface)) + rootBase;
    }

    public static String resolveDestination(MqChannel channel, Class<?> rootContract, Method handlerMethod) {
        if (channel != null && StringUtils.hasText(channel.destination())) {
            return channel.destination().trim();
        }
        return resolveDestination(rootContract, handlerMethod);
    }

    public static String resolveProducerFunction(MqChannel channel, Class<?> rootContract, Method handlerMethod) {
        if (channel != null && StringUtils.hasText(channel.producerFunction())) {
            return channel.producerFunction().trim();
        }
        return resolveProducerFunction(rootContract, handlerMethod);
    }

    public static String resolveGroup(MqSubscribe subscribe, Class<?> slotInterface) {
        if (subscribe != null && StringUtils.hasText(subscribe.group())) {
            return subscribe.group().trim();
        }
        return resolveGroup(slotInterface);
    }

    public static String resolveFunction(
            MqSubscribe subscribe, Class<?> slotInterface, Class<?> rootContract) {
        if (subscribe != null && StringUtils.hasText(subscribe.function())) {
            return subscribe.function().trim();
        }
        return resolveFunction(slotInterface, rootContract);
    }

    public static String methodToDestinationSuffix(String methodName) {
        String base = stripOnPrefix(methodName);
        List<String> words = splitCamelCase(base);
        if (words.isEmpty()) {
            throw new IllegalArgumentException("无法从方法名推导 destination: " + methodName);
        }
        if (words.size() == 1) {
            return words.get(0).toLowerCase(Locale.ROOT);
        }
        String action = words.get(words.size() - 1).toLowerCase(Locale.ROOT);
        String subject = String.join("-", words.subList(0, words.size() - 1).stream()
                .map(w -> w.toLowerCase(Locale.ROOT))
                .toList());
        return subject + "." + action;
    }

    private static String functionPrefix(String group) {
        if ("distribution".equals(group)) {
            return "dist";
        }
        if ("parentled".equals(group)) {
            return "parentLed";
        }
        return lowerFirst(group);
    }

    private static List<String> splitCamelCase(String value) {
        List<String> words = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return words;
        }
        for (String part : CAMEL_BOUNDARY.split(value)) {
            if (StringUtils.hasText(part)) {
                words.add(part);
            }
        }
        return words;
    }

    private static String stripOnPrefix(String methodName) {
        if (methodName != null && methodName.startsWith("on") && methodName.length() > 2) {
            return methodName.substring(2);
        }
        return methodName;
    }

    private static String stripConsumerSuffix(String simpleName) {
        if (simpleName.endsWith(CONSUMER_SUFFIX)) {
            return simpleName.substring(0, simpleName.length() - CONSUMER_SUFFIX.length());
        }
        return simpleName;
    }

    private static String lowerFirst(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.length() == 1) {
            return value.toLowerCase(Locale.ROOT);
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String upperFirst(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.length() == 1) {
            return value.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String validateModuleKey(String module) {
        if (!StringUtils.hasText(module)) {
            throw new IllegalArgumentException("module 不能为空");
        }
        String trimmed = module.trim();
        if (trimmed.contains(".") || trimmed.contains(" ") || trimmed.contains("/")) {
            throw new IllegalArgumentException("非法 module: " + module);
        }
        return trimmed;
    }
}
