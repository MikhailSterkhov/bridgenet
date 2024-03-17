package me.moonways.bridgenet.jdbc.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import me.moonways.bridgenet.jdbc.dao.DaoEntityException;
import me.moonways.bridgenet.jdbc.core.DatabaseConnection;
import me.moonways.bridgenet.jdbc.core.ResponseRow;
import me.moonways.bridgenet.jdbc.core.ResponseStream;
import me.moonways.bridgenet.jdbc.core.compose.DatabaseComposer;
import me.moonways.bridgenet.jdbc.dao.EntityDao;
import me.moonways.bridgenet.jdbc.dao.TypedEntityDao;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@UtilityClass
public class EntityTransmitter {

    private static final Comparator<Element> ELEMENT_COMPARATOR = Comparator.comparingInt(Element::getOrder);
    public static final String AUTO_GENERATED_LABEL_NAME = "id";

    private static Unsafe unsafe;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);

            unsafe = (Unsafe) theUnsafe.get(null);
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public String transmitName(@NotNull String name) {
        NameTransmitter transmitter = new NameTransmitter(name);
        transmitter.makeSplitAsLowerCase();

        return transmitter.getOutput();
    }

    public Entity transmitEntity(@NotNull Object entity) {
        ObjectToEntityTransmitter transmitter = new ObjectToEntityTransmitter(entity);
        transmitter.fillEntity();

        return transmitter.getOutput();
    }

    public Entity transmitEntityWithoutValues(@NotNull Class<?> entityType) {
        ObjectToEntityTemplateTransmitter transmitter = new ObjectToEntityTemplateTransmitter(entityType);

        transmitter.prepareEntity();
        transmitter.fillEntity();

        transmitter.assemble();

        return transmitter.getOutput();
    }

    public <T> T transmitObject(@NotNull Entity entity, @NotNull Class<T> cls) {
        EntityToObjectTransmitter<T> transmitter = new EntityToObjectTransmitter<>(entity, cls);
        transmitter.assemble();

        return transmitter.getOutput();
    }

    public <T> List<T> transmitObjectsList(@NotNull ResponseStream stream, @NotNull Class<T> cls, @NotNull DatabaseComposer composer, @NotNull DatabaseConnection connection) {
        ResponseStreamToEntityTransmitter transmitter = new ResponseStreamToEntityTransmitter(transmitEntityWithoutValues(cls), stream, composer, connection);
        transmitter.addEntities();

        List<Entity> entityList = transmitter.getOutput();

        return entityList.stream().map(entity -> EntityTransmitter.transmitObject(entity, cls))
                .collect(Collectors.toList());
    }

    private boolean isNotTransportable(Class<?> type) {
        //String packageName = type.getPackage().getName();
        //if (packageName.startsWith("java.")) {
        //    return false;
        //}

        return !ExternalElement.class.equals(type)
                && !isNumber(type)
                && !CharSequence.class.isAssignableFrom(type)
                && !Boolean.class.equals(type)
                && !boolean.class.equals(type)
                && !UUID.class.equals(type);
    }

    private boolean isNumber(Class<?> type) {
        if (Number.class.isAssignableFrom(type)) {
            return true;
        }
        return int.class.equals(type)
                || long.class.equals(type)
                || float.class.equals(type)
                || double.class.equals(type)
                || short.class.equals(type)
                || byte.class.equals(type);
    }

    @AllArgsConstructor
    private static class NameTransmitter {

        private static final char[] UPPERCASE_RANGE = new char[]{'A', 'Z'};
        private static final String WORDS_SPLITTER = "_";

        @Getter
        private String output;

        public void makeSplitAsLowerCase() {
            int[] total = IntStream.rangeClosed(UPPERCASE_RANGE[0], UPPERCASE_RANGE[1])
                    .filter(x -> output.chars().anyMatch(y -> y == x))
                    .distinct()
                    .toArray();

            for (int uppercaseIndex : total) {
                char uppercase = (char) uppercaseIndex;
                char lowerCase = Character.toLowerCase(uppercase);

                String uppercaseString = Character.toString(uppercase);

                if (output.trim().startsWith(uppercaseString)) {
                    output = output.replaceFirst(uppercaseString, Character.toString(lowerCase));
                } else {
                    output = output.replace(uppercaseString, WORDS_SPLITTER + lowerCase);
                }
            }
        }
    }

    @RequiredArgsConstructor
    private static class ObjectToEntityTransmitter {

        private final Object input;

        @Getter
        private Entity output;

        @SneakyThrows
        public void fillEntity() {
            List<Element> elementList = new ArrayList<>();

            Class<?> inputClass = input.getClass();
            Entity entity = EntityTransmitter.transmitEntityWithoutValues(inputClass);

            for (Element element : entity.getElements()) {
                String fieldName = element.getFieldName();

                Field field = inputClass.getDeclaredField(fieldName);

                elementList.add(element.toBuilder()
                        .value(getElementValue(fieldName, field))
                        .build());
            }

            elementList.sort(ELEMENT_COMPARATOR);

            output = Entity.builder()
                    .name(entity.getName())
                    .type(entity.getType())
                    .elements(elementList)
                    .build();
        }

        private Object getElementValue(String name, Field field) {
            try {
                field.setAccessible(true);
                return getAsInternalValue(field.get(input));
            } catch (Exception exception) {
                throw new DaoEntityException("entity field '" + name + "' value getting", exception);
            }
        }

        private Object getAsInternalValue(Object source) {
            if (source == null) {
                return null;
            }
            if (EntityTransmitter.isNotTransportable(source.getClass())) {

                Entity internal = EntityTransmitter.transmitEntity(source);
                Element key = internal.getKey();

                if (key == null) {
                    throw new NullPointerException();
                }

                Object value = key.getValue();
                if (EntityTransmitter.isNotTransportable(value.getClass())) {
                    return getAsInternalValue(value);
                }

                return ExternalElement.builder()
                        .element(key)
                        .foreignEntity(internal)
                        .build();
            }

            return source;
        }
    }

    @RequiredArgsConstructor
    private static class ObjectToEntityTemplateTransmitter {

        private final Class<?> input;

        private Entity.EntityBuilder entityBuilder = Entity.builder();

        @Getter
        private Entity output;

        public void prepareEntity() {
            if (!input.isAnnotationPresent(EntityAccessible.class) && !input.isAnnotationPresent(EntityAutoPersistence.class)) {
                throw new DaoEntityException(input + " entity is not marked @EntityAccessible annotation");
            }
        }

        public void assemble() {
            output = entityBuilder.build();
            entityBuilder = null;
        }

        public void fillEntity() {
            boolean hasAutoPrepareFlag = input.isAnnotationPresent(EntityAutoPersistence.class);

            String entityName = input.getDeclaredAnnotation(EntityAccessible.class).name();
            List<Element> elementList = new ArrayList<>();

            int customOrder = 0;
            for (Field field : input.getDeclaredFields()) {
                Element element = toElement(field);

                if (hasAutoPrepareFlag || element == null) {
                    element = toElementAuto(field);
                }

                if (element != null) {
                    if (element.getOrder() < 0)
                        element = element.toBuilder().order(customOrder++).build();

                    elementList.add(element);
                }
            }

            elementList.sort(ELEMENT_COMPARATOR);

            entityBuilder.type(input)
                    .name(entityName)
                    .elements(elementList);
        }

        private Element toElementAuto(Field field) {
            if ((field.getModifiers() & Modifier.TRANSIENT) == Modifier.TRANSIENT) {
                return null;
            }

            String name = field.getName();

            int mask = 0;
            if ((field.getModifiers() & Modifier.FINAL) == Modifier.FINAL) {
                mask |= Element.FINALIZED;
            }

            if (name.equalsIgnoreCase(AUTO_GENERATED_LABEL_NAME) && EntityTransmitter.isNumber(field.getType())) {
                mask |= Element.AUTO_GENERATED | Element.PRIMARY;
            }

            return Element.builder()
                    .order(-1)
                    .mask(mask)
                    .shortName(EntityTransmitter.transmitName(name))
                    .fieldName(name)
                    .type(field.getType())
                    .build();
        }

        private Element toElement(Field field) {
            boolean isAutogenerated = field.isAnnotationPresent(EntityAutoGenerated.class);
            EntityElement elementAnnotation = field.getDeclaredAnnotation(EntityElement.class);

            if (elementAnnotation == null && !isAutogenerated) {
                return null;
            }

            String name = field.getName();

            if (isAutogenerated) {
                return Element.builder()
                        .order(-1)
                        .value(0)
                        .shortName(AUTO_GENERATED_LABEL_NAME)
                        .fieldName(name)
                        .mask(Element.AUTO_GENERATED | Element.PRIMARY)
                        .type(field.getType())
                        .build();
            }

            int mask = 0;
            if (field.isAnnotationPresent(EntityUnique.class)) {
                mask |= Element.UNIQUE;
            }
            if ((field.getModifiers() & Modifier.FINAL) == Modifier.FINAL) {
                mask |= Element.FINALIZED;
            }

            String id = elementAnnotation.id();
            return Element.builder()
                    .mask(mask)
                    .fieldName(name)
                    .order(elementAnnotation.order())
                    .shortName(id.isEmpty() ? EntityTransmitter.transmitName(name) : id)
                    .defaultValue(elementAnnotation.defaultValue())
                    .type(field.getType())
                    .build();
        }
    }

    @RequiredArgsConstructor
    private static final class EntityToObjectTransmitter<T> {

        private final Entity entity;
        private final Class<T> type;

        @Getter
        private T output;

        public void assemble() {
            T obj = allocateInstance();

            for (Element element : entity.getElements()) {
                setElementToObject(element, obj);
            }

            this.output = obj;
        }

        @SuppressWarnings("unchecked")
        @SneakyThrows
        private T allocateInstance() {
            return (T) unsafe.allocateInstance(type);
        }

        @SneakyThrows
        private void setElementToObject(Element element, Object obj) {
            Field declaredField = type.getDeclaredField(element.getFieldName());
            declaredField.setAccessible(true);

            Object value = element.getValue();

            if (value instanceof Entity) {
                Entity externalEntity = (Entity) value;
                value = EntityTransmitter.transmitObject(externalEntity, externalEntity.getType());
            }

            declaredField.set(obj, value);
        }
    }

    @RequiredArgsConstructor
    private static final class ResponseStreamToEntityTransmitter {

        private final Entity templateEntity;
        private final ResponseStream responseStream;
        private final DatabaseComposer composer;
        private final DatabaseConnection connection;

        @Getter
        private List<Entity> output;

        public void addEntities() {
            output = new ArrayList<>();

            for (ResponseRow row : responseStream) {
                Entity entity = createEntity(row);
                output.add(entity);
            }
        }

        private <T> EntityDao<T> createExternalDao(Class<T> externalType) {
            return new TypedEntityDao<>(composer, connection, externalType);
        }

        private Entity createEntity(ResponseRow row) {
            Entity clone = templateEntity.clone();
            List<Element> elementList = new ArrayList<>();

            for (Element element : clone.getElements()) {
                Object valueObject = getElementObject(element,
                        row.field(element.getShortName()).getAsObject());

                elementList.add(element.toBuilder()
                        .value(valueObject)
                        .build());
            }

            elementList.sort(ELEMENT_COMPARATOR);
            return clone.toBuilder().elements(elementList).build();
        }

        private Object getElementObject(Element element, Object valueObject) {
            Class<?> valueType = valueObject.getClass();

            if (!element.getType().isPrimitive() && !element.getType().equals(valueType)) {
                if (UUID.class.equals(element.getType())) {
                    return UUID.fromString(valueObject.toString());
                }

                valueObject = findExternalEntity(valueObject, element);
            }

            return valueObject;
        }

        private Entity findExternalEntity(Object dbValue, Element element) {
            Long entityId = Long.valueOf(dbValue.toString());
            Optional<Object> monoById = (Optional<Object>) createExternalDao(element.getType()).findMonoById(entityId);

            return toExternalEntity(monoById.orElse(null));
        }

        private Entity toExternalEntity(Object object) {
            if (object == null) {
                return null;
            }
            return EntityTransmitter.transmitEntity(object);
        }
    }
}
