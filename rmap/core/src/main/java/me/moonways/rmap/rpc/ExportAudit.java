package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapExportException;
import me.moonways.rmap.api.Snapshot;
import me.moonways.rmap.codec.ClassSchema;
import me.moonways.rmap.codec.CodecRegistry;
import me.moonways.rmap.codec.ExceptionData;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Export-time audit интерфейса (спека §8). Обходит КАЖДЫЙ не-{@code @RmapExcluded} метод, проверяет
 * кодируемость параметров и возврата (рекурсивно, по тем же полям, что {@link ClassSchema} кодека),
 * собирает manifest (whitelist классов для decode §5.1 + methodId-таблицу + digest §6). ВСЕ проблемы
 * агрегируются и бросаются одним {@link RmapExportException} — {@code server.start()} не стартует до
 * их устранения.
 *
 * <p>В этой задаче реализовано SERVER-поведение; CLIENT-режим (перегрузка с {@code Mode}) добавляется
 * задачей 4.
 */
public final class ExportAudit {

    private ExportAudit() {
    }

    /**
     * Сторона аудита. {@code SERVER} — экспорт: неизвестный интерфейсный тип в ЛЮБОЙ позиции (кроме
     * wrap-списка возврата) — ошибка. {@code CLIENT} — {@code lookup}: клиент не знает wrap-набор
     * сервера, поэтому неизвестный интерфейсный тип в позиции ВОЗВРАТА считается потенциальным
     * remote-ref (кодируемым, попадает в whitelist), а в позиции ПАРАМЕТРА — по-прежнему ошибка.
     */
    public enum Mode { SERVER, CLIENT }

    /** Аудит интерфейса под экспорт на сервере (§8). Бросает {@link RmapExportException}, если хотя бы
     *  один не-исключённый метод непригоден для v1. */
    public static InterfaceManifest audit(Class<?> iface, ExportOptions opts, CodecRegistry registry) {
        return audit(iface, opts, registry, Mode.SERVER);
    }

    /** Аудит с явным режимом (§8, §7.1). {@link Mode#SERVER} — серверное поведение (как перегрузка
     *  выше); {@link Mode#CLIENT} — клиентский аудит {@code lookup}: собирает whitelist+digest и
     *  валидирует параметры, но не отвергает неизвестный интерфейс в позиции возврата. */
    public static InterfaceManifest audit(Class<?> iface, ExportOptions opts, CodecRegistry registry, Mode mode) {
        return new Walker(iface, opts, registry, mode).run();
    }

    /** Рекурсивный обходчик графа типов интерфейса. Не потокобезопасен — экземпляр на один аудит. */
    private static final class Walker {

        private static final Map<TypeVariable<?>, Type> EMPTY_ENV = Collections.emptyMap();

        private final Class<?> iface;
        private final ExportOptions opts;
        private final CodecRegistry registry;
        private final Mode mode;

        private final List<String> problems = new java.util.ArrayList<>();
        private final Set<String> whitelist = new LinkedHashSet<>();
        private final Set<Class<?>> wrapped = new LinkedHashSet<>();
        private final Map<Long, Method> methodsById = new LinkedHashMap<>();
        /** Классы reflective-DTO на текущем пути спуска — разрывает циклы графа (Node{Node next;}). */
        private final Set<Class<?>> visiting = new HashSet<>();
        /** ref-интерфейсы (wrap-возврат / CLIENT-возврат) к жадному аудиту графа + вливанию whitelist (I4). */
        private final Set<Class<?>> refInterfaces = new LinkedHashSet<>();
        /** Общий на всё дерево ref-аудита набор уже обойдённых интерфейсов — разрывает циклы графа
         *  ref-интерфейсов (A возвращает ref B, B возвращает ref A) при рекурсивном аудите. */
        private final Set<Class<?>> auditedRefs;

        private String currentMethod = "";

        Walker(Class<?> iface, ExportOptions opts, CodecRegistry registry, Mode mode) {
            this(iface, opts, registry, mode, new HashSet<>());
        }

        Walker(Class<?> iface, ExportOptions opts, CodecRegistry registry, Mode mode,
               Set<Class<?>> auditedRefs) {
            this.iface = iface;
            this.opts = opts;
            this.registry = registry;
            this.mode = mode;
            this.auditedRefs = auditedRefs;
        }

        InterfaceManifest run() {
            initBuiltinWhitelist();

            for (Method method : MethodIds.contractMethods(iface)) {
                currentMethod = label(method);

                long id = MethodIds.methodId(method);
                Method prev = methodsById.get(id);
                if (prev != null && !prev.equals(method)) {
                    addProblem("methodId collision with " + label(prev));
                }
                methodsById.put(id, method);

                for (Type paramType : method.getGenericParameterTypes()) {
                    walkType(paramType, false, EMPTY_ENV);
                }
                Type returnType = method.getGenericReturnType();
                // I6: Optional<T>/CompletableFuture<T> кодируемы ТОЛЬКО как верхний тип возврата —
                // агент/прокси распаковывают ровно этот уровень по сигнатуре (§5.2/§7), рантайм их
                // как значение не кодирует. Снимаем один верхний слой ЗДЕСЬ и аудируем внутренний тип
                // в позиции возврата; глубже (вложенный Optional/CF, поле DTO, параметр, List<Optional>)
                // walkType отвергает их. CF<Optional<…>> → внутренний Optional попадёт вложенным → отказ.
                walkType(unwrapTopReturn(returnType), true, EMPTY_ENV);

                // Правило «@Snapshot требует wrapped-возврат» — серверная export-валидация: клиент
                // (lookup) wrap-набор сервера не знает и @Snapshot-возврат просто декодирует значением.
                if (mode == Mode.SERVER
                        && method.isAnnotationPresent(Snapshot.class) && !returnMentionsWrapped(returnType)) {
                    addProblem("@Snapshot on method without wrapped return type");
                }
            }

            // I4: жадно аудировать интерфейсы, выдаваемые рефом (wrapReturnAsRemote-возврат на сервере /
            // любой интерфейс-возврат на клиенте) — их собственный граф методов вводит DTO-типы
            // (параметры ref-вызовов, возвраты ref-методов), которые ОБЯЗАНЫ быть в decode-whitelist,
            // иначе легальный ref-вызов/ответ падал бы CODEC_ERROR. Их whitelist вливается в манифест
            // subject'а; непригодный wrapped-интерфейс → RmapExportException на export (а не hang в рантайме).
            auditWrappedGraphs();

            if (!problems.isEmpty()) {
                throw new RmapExportException(String.join("\n", problems));
            }
            return new InterfaceManifest(iface, MethodIds.interfaceDigest(iface),
                    Collections.unmodifiableMap(methodsById),
                    Collections.unmodifiableSet(whitelist),
                    Collections.unmodifiableSet(wrapped));
        }

        private void initBuiltinWhitelist() {
            whitelist.add(String.class.getName());
            whitelist.add(UUID.class.getName());
            for (Class<?> boxed : new Class<?>[]{Boolean.class, Byte.class, Short.class, Integer.class,
                    Long.class, Float.class, Double.class, Character.class}) {
                whitelist.add(boxed.getName());
            }
            whitelist.add(java.util.ArrayList.class.getName());
            whitelist.add(LinkedHashSet.class.getName());
            whitelist.add(LinkedHashMap.class.getName());
            whitelist.add(ExceptionData.class.getName());
        }

        // --- рекурсия по типам ---------------------------------------------------------------

        private void walkType(Type type, boolean returnPosition, Map<TypeVariable<?>, Type> env) {
            Type t = resolve(type, env);
            if (t instanceof WildcardType) {
                addProblem("wildcard generic type not encodable");
                return;
            }
            if (t instanceof TypeVariable) {
                addProblem("unresolvable type variable " + ((TypeVariable<?>) t).getName());
                return;
            }
            if (t instanceof GenericArrayType) {
                Type comp = ((GenericArrayType) t).getGenericComponentType();
                Class<?> compRaw = rawOf(resolve(comp, env));
                if (compRaw != null && compRaw != byte.class) {
                    addWhitelistRef(compRaw);
                }
                walkType(comp, returnPosition, env);
                return;
            }
            if (t instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) t;
                handleParameterized((Class<?>) pt.getRawType(), pt.getActualTypeArguments(),
                        returnPosition, env);
                return;
            }
            if (t instanceof Class) {
                handleClass((Class<?>) t, returnPosition, env);
                return;
            }
            addProblem("unsupported type " + t);
        }

        private void handleParameterized(Class<?> raw, Type[] args, boolean returnPosition,
                                         Map<TypeVariable<?>, Type> env) {
            if (raw == Optional.class || raw == CompletableFuture.class) {
                // I6: сюда Optional/CompletableFuture попадают только ВЛОЖЕННЫМИ (верхний уровень
                // возврата снят в run()): параметр, поле DTO, List<Optional<X>>, CF<Optional<X>>.
                // Рантайм их на этих позициях не распакует → закодировал бы значением → CODEC_ERROR.
                addProblem((raw == Optional.class ? "Optional" : "CompletableFuture")
                        + " encodable only as top-level return type: " + raw.getName());
                return;
            }
            if (isPlainContainer(raw) || raw == Map.class) {
                for (Type arg : args) {
                    walkType(arg, returnPosition, env);
                }
                return;
            }
            if (Collection.class.isAssignableFrom(raw) || Map.class.isAssignableFrom(raw)) {
                addProblem("custom collection subtype not allowed as declared type: " + raw.getName());
                return;
            }
            if (raw == Class.class) {
                addProblem("Class<?> not encodable in signature");
                return;
            }
            if (opts.getWrapReturnAsRemote().contains(raw)) {
                wrapOrReject(raw, returnPosition);
                return;
            }
            if (registry.findCodec(raw) != null) {
                addWhitelistRef(raw);
                return;
            }
            if (registry.isSerializable(raw)) {
                addWhitelistRef(raw);
                descendFields(raw, buildEnv(raw.getTypeParameters(), args, env), returnPosition);
                return;
            }
            addProblem("type not encodable: " + raw.getName());
        }

        private void handleClass(Class<?> cls, boolean returnPosition, Map<TypeVariable<?>, Type> env) {
            if (cls == void.class || cls == Void.class || cls.isPrimitive()) {
                return; // void / примитив / boxed-примитив — кодируемый лист
            }
            if (cls.isArray()) {
                Class<?> comp = cls.getComponentType();
                if (comp == byte.class) {
                    return; // byte[] → тег BYTES, без classRef
                }
                addWhitelistRef(comp);
                walkType(comp, returnPosition, env);
                return;
            }
            if (cls == Object.class) {
                addProblem("Object not encodable in signature");
                return;
            }
            if (cls == Class.class) {
                addProblem("Class<?> not encodable in signature");
                return;
            }
            if (opts.getWrapReturnAsRemote().contains(cls)) {
                wrapOrReject(cls, returnPosition);
                return;
            }
            if (registry.findCodec(cls) != null) {
                addWhitelistRef(cls);
                return;
            }
            if (isBuiltinLeaf(cls)) {
                addWhitelistRef(cls);
                return;
            }
            if (cls.isEnum()) {
                addWhitelistRef(cls);
                return;
            }
            if (cls == ExceptionData.class) {
                addWhitelistRef(cls);
                return;
            }
            if (Collection.class.isAssignableFrom(cls) || Map.class.isAssignableFrom(cls)) {
                if (cls == List.class || cls == Set.class || cls == Collection.class || cls == Map.class) {
                    addProblem("raw collection type without generic argument: " + cls.getName());
                } else {
                    addProblem("custom collection subtype not allowed as declared type: " + cls.getName());
                }
                return;
            }
            if (cls.isInterface()) {
                if (mode == Mode.CLIENT && returnPosition) {
                    // §7.1/I5: клиент не знает wrap-набор сервера — ЛЮБОЙ неизвестный интерфейс в
                    // позиции возврата считаем потенциальным remote-ref (кодируемым, в whitelist),
                    // включая одно-методный wrapReturnAsRemote(Player{String name()}). functional-
                    // проверка к возврату на клиенте НЕ применяется (ветка ref ДО functional), иначе
                    // client.lookup отверг бы легально экспортированный сервером wrapped-интерфейс.
                    addWhitelistRef(cls);
                    refInterfaces.add(cls); // I4: аудировать граф ref-интерфейса → DTO ref-методов в whitelist
                } else if (isFunctionalLike(cls)) {
                    // SERVER-режим / позиция ПАРАМЕТРА: функциональный интерфейс (callback) не кодируем.
                    addProblem("functional interface (callback) not encodable: " + cls.getName());
                } else {
                    addProblem("interface not encodable (not wrapped): " + cls.getName());
                }
                return;
            }
            if (registry.isSerializable(cls)) {
                addWhitelistRef(cls);
                descendFields(cls, EMPTY_ENV, returnPosition);
                return;
            }
            addProblem("type not encodable: " + cls.getName());
        }

        /** Спуск в поля reflective-класса по схеме {@link ClassSchema} (те же поля, что кодек:
         *  нестатические, не-transient, вся иерархия). Цикл графа разрывается путевым guard'ом. */
        private void descendFields(Class<?> cls, Map<TypeVariable<?>, Type> env, boolean returnPosition) {
            if (!visiting.add(cls)) {
                return; // класс уже на пути спуска — рекурсивный DTO, обход прекращаем
            }
            try {
                for (Field f : ClassSchema.of(cls)) {
                    walkType(f.getGenericType(), returnPosition, env);
                }
            } finally {
                visiting.remove(cls);
            }
        }

        /**
         * I4: аудирует граф методов каждого ref-интерфейса (собранного в {@link #refInterfaces}) и вливает
         * его decode-whitelist в текущий манифест. Ref-интерфейс аудируется теми же {@code opts}/{@code mode}
         * — его собственные wrap-возвраты тоже станут refs и подтянут свои DTO (рекурсивно через вложенный
         * {@link Walker}). Общий {@link #auditedRefs} разрывает циклы графа ref-интерфейсов. Непригодный
         * ref-интерфейс (напр. callback-параметр) → его проблемы попадают в общий агрегат → export падает.
         */
        private void auditWrappedGraphs() {
            for (Class<?> refIface : refInterfaces) {
                if (!refIface.isInterface() || !auditedRefs.add(refIface)) {
                    continue; // не интерфейс (проблема уже добавлена в wrapOrReject) либо уже аудирован
                }
                try {
                    InterfaceManifest m = new Walker(refIface, opts, registry, mode, auditedRefs).run();
                    whitelist.addAll(m.getDecodeWhitelist());
                } catch (RmapExportException ex) {
                    for (String line : ex.getMessage().split("\n")) {
                        problems.add("wrapped " + refIface.getSimpleName() + ": " + line);
                    }
                }
            }
        }

        // --- резолв type-variables ------------------------------------------------------------

        private static Type resolve(Type t, Map<TypeVariable<?>, Type> env) {
            int guard = 0;
            while (t instanceof TypeVariable && env.containsKey(t) && guard++ < 64) {
                t = env.get(t);
            }
            return t;
        }

        /** Свежее окружение type-variable→концретный-тип из позиции использования generic-класса:
         *  {@code Title<Component>} → параметр {@code T} читается как {@code Component}. */
        private static Map<TypeVariable<?>, Type> buildEnv(TypeVariable<?>[] params, Type[] args,
                                                           Map<TypeVariable<?>, Type> outer) {
            Map<TypeVariable<?>, Type> m = new HashMap<>();
            int n = Math.min(params.length, args.length);
            for (int i = 0; i < n; i++) {
                m.put(params[i], resolve(args[i], outer));
            }
            return m;
        }

        // --- предикаты и утилиты --------------------------------------------------------------

        private void wrapOrReject(Class<?> cls, boolean returnPosition) {
            if (returnPosition) {
                if (!cls.isInterface()) {
                    // I4: wrapReturnAsRemote должен быть интерфейсом (ref = JDK-прокси интерфейса).
                    addProblem("wrapReturnAsRemote type is not an interface: " + cls.getName());
                    return;
                }
                wrapped.add(cls);
                addWhitelistRef(cls);
                refInterfaces.add(cls); // I4: аудировать граф методов ref-интерфейса, влить его whitelist
            } else {
                addProblem("remote-ref type in parameter position: " + cls.getName());
            }
        }

        /** Снимает РОВНО один верхний слой {@code Optional<T>}/{@code CompletableFuture<T>} с типа
         *  возврата (I6): агент/прокси распаковывают только этот уровень. Прочие типы — как есть. */
        private static Type unwrapTopReturn(Type returnType) {
            if (returnType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) returnType;
                Type[] targs = pt.getActualTypeArguments();
                if (pt.getRawType() instanceof Class && targs.length == 1) {
                    Class<?> raw = (Class<?>) pt.getRawType();
                    if (raw == Optional.class || raw == CompletableFuture.class) {
                        return targs[0];
                    }
                }
            }
            return returnType;
        }

        private boolean returnMentionsWrapped(Type returnType) {
            Class<?> raw = rawOf(returnType);
            if (raw != null && opts.getWrapReturnAsRemote().contains(raw)) {
                return true;
            }
            if (returnType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) returnType;
                if (isPlainContainer((Class<?>) pt.getRawType())) {
                    for (Type arg : pt.getActualTypeArguments()) {
                        Class<?> argRaw = rawOf(arg);
                        if (argRaw != null && opts.getWrapReturnAsRemote().contains(argRaw)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static boolean isPlainContainer(Class<?> raw) {
            return raw == List.class || raw == Set.class || raw == Collection.class
                    || raw == Optional.class || raw == CompletableFuture.class;
        }

        private static boolean isBuiltinLeaf(Class<?> c) {
            return c == String.class || c == UUID.class
                    || c == Boolean.class || c == Byte.class || c == Short.class || c == Integer.class
                    || c == Long.class || c == Float.class || c == Double.class || c == Character.class;
        }

        private static boolean isFunctionalLike(Class<?> c) {
            if (c.isAnnotationPresent(FunctionalInterface.class)) {
                return true;
            }
            int abstractMethods = 0;
            for (Method m : c.getMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (m.isDefault()) continue;
                if (m.getDeclaringClass() == Object.class) continue;
                abstractMethods++;
            }
            return abstractMethods == 1;
        }

        private static Class<?> rawOf(Type t) {
            if (t instanceof Class) {
                return (Class<?>) t;
            }
            if (t instanceof ParameterizedType) {
                return (Class<?>) ((ParameterizedType) t).getRawType();
            }
            if (t instanceof GenericArrayType) {
                Class<?> comp = rawOf(((GenericArrayType) t).getGenericComponentType());
                return comp == null ? null : Array.newInstance(comp, 0).getClass();
            }
            return null; // TypeVariable, WildcardType
        }

        private void addWhitelistRef(Class<?> cls) {
            if (cls == null || cls.isPrimitive()) {
                return;
            }
            whitelist.add(cls.getName());
        }

        private void addProblem(String reason) {
            problems.add(currentMethod + " -> " + reason);
        }

        private static String label(Method m) {
            return m.getName() + MethodIds.jvmDescriptor(m);
        }
    }
}
