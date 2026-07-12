package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapExcluded;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * methodId и interfaceDigest (спека §6). methodId адресует метод в паре с subjectId;
 * digest ловит рассинхрон одного метода даже при равном appVersion (пересборка без бампа).
 * jvmDescriptor — как в class-файле (эрейзнутые типы): {@code (Ljava/util/UUID;)Ljava/util/Optional;}.
 */
public final class MethodIds {

    private MethodIds() {
    }

    /** Дескриптор метода в формате class-файла: примитивы одной буквой ({@code I}/{@code J}/{@code Z}/…),
     *  массивы префиксом {@code [}, ссылочные — {@code L<slash-fqn>;}, void — {@code V}. */
    public static String jvmDescriptor(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Class<?> p : method.getParameterTypes()) {
            appendDescriptor(p, sb);
        }
        sb.append(')');
        appendDescriptor(method.getReturnType(), sb);
        return sb.toString();
    }

    private static void appendDescriptor(Class<?> type, StringBuilder sb) {
        if (type.isArray()) {
            sb.append('[');
            appendDescriptor(type.getComponentType(), sb);
            return;
        }
        if (type.isPrimitive()) {
            sb.append(primitiveDescriptor(type));
            return;
        }
        sb.append('L').append(type.getName().replace('.', '/')).append(';');
    }

    private static char primitiveDescriptor(Class<?> type) {
        if (type == int.class) return 'I';
        if (type == long.class) return 'J';
        if (type == boolean.class) return 'Z';
        if (type == void.class) return 'V';
        if (type == byte.class) return 'B';
        if (type == short.class) return 'S';
        if (type == char.class) return 'C';
        if (type == float.class) return 'F';
        if (type == double.class) return 'D';
        throw new IllegalArgumentException("not a primitive: " + type);
    }

    /** Первые 8 байт big-endian {@code SHA-256(methodName + jvmDescriptor)} (§6). */
    public static long methodId(Method method) {
        return first8BigEndian(sha256(method.getName() + jvmDescriptor(method)));
    }

    /** Первые 8 байт {@code SHA-256} конкатенации ОТСОРТИРОВАННЫХ строк {@code "<name><descriptor>"}
     *  всех не-{@code @RmapExcluded} методов интерфейса (§6). Порядок объявления неважен;
     *  исключённые методы в digest НЕ входят. */
    public static long interfaceDigest(Class<?> iface) {
        List<String> signatures = new ArrayList<>();
        for (Method method : contractMethods(iface)) {
            signatures.add(method.getName() + jvmDescriptor(method));
        }
        Collections.sort(signatures);
        StringBuilder sb = new StringBuilder();
        for (String s : signatures) {
            sb.append(s);
        }
        return first8BigEndian(sha256(sb.toString()));
    }

    /** Не-исключённые методы remote-контракта: {@code getMethods()} минус static/default,
     *  минус {@code java.lang.Object}-методы, минус {@code @RmapExcluded}. */
    public static List<Method> contractMethods(Class<?> iface) {
        List<Method> out = new ArrayList<>();
        for (Method method : iface.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            if (method.isDefault()) continue;
            if (method.getDeclaringClass() == Object.class) continue;
            if (method.isAnnotationPresent(RmapExcluded.class)) continue;
            out.add(method);
        }
        return out;
    }

    private static long first8BigEndian(byte[] hash) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (hash[i] & 0xFFL);
        }
        return v;
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
