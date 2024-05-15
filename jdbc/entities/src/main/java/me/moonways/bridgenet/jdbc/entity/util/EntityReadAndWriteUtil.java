package me.moonways.bridgenet.jdbc.entity.util;

import lombok.experimental.UtilityClass;
import me.moonways.bridgenet.jdbc.core.DatabaseConnection;
import me.moonways.bridgenet.jdbc.core.ResponseRow;
import me.moonways.bridgenet.jdbc.core.compose.DatabaseComposer;
import me.moonways.bridgenet.jdbc.entity.DatabaseEntityException;
import me.moonways.bridgenet.jdbc.entity.EntityID;
import me.moonways.bridgenet.jdbc.entity.EntityRepository;
import me.moonways.bridgenet.jdbc.entity.ForceEntityRepository;
import me.moonways.bridgenet.jdbc.entity.descriptor.EntityDescriptor;
import me.moonways.bridgenet.jdbc.entity.descriptor.EntityParametersDescriptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@UtilityClass
public class EntityReadAndWriteUtil {

    public EntityDescriptor read(Object object) {
        List<EntityPersistenceUtil.WrappedEntityParameter> parameters = EntityPersistenceUtil.getParameters(object);
        return EntityDescriptor.builder()
                .id(readEntityID(parameters))
                .parameters(EntityParametersDescriptor.fromPersistenceList(parameters))
                .containerName(EntityPersistenceUtil.getEntityName(object))
                .rootClass(object.getClass())
                .build();
    }

    public EntityDescriptor read(Class<?> entityType) {
        List<EntityPersistenceUtil.WrappedEntityParameter> parameters = EntityPersistenceUtil.getParameters(entityType);
        return EntityDescriptor.builder()
                .id(EntityID.NOT_FOUND)
                .parameters(EntityParametersDescriptor.fromPersistenceList(parameters))
                .containerName(EntityPersistenceUtil.getEntityName(entityType))
                .rootClass(entityType)
                .build();
    }

    public EntityDescriptor readRow(ResponseRow responseRow, Class<?> entityClass) {
        List<EntityPersistenceUtil.WrappedEntityParameter> parameters = EntityPersistenceUtil.getParameters(entityClass);

        EntityDescriptor.EntityDescriptorBuilder entityDescriptorBuilder = EntityDescriptor.builder()
                .rootClass(entityClass)
                .containerName(EntityPersistenceUtil.getEntityName(entityClass));

        for (EntityPersistenceUtil.WrappedEntityParameter parameter : parameters) {
            EntityParametersDescriptor.ParameterUnit unit = parameter.getUnit()
                    .toBuilder()
                    .value(responseRow.field(parameter.getUnit().getId()).getAsObject())
                    .build();

            if (unit.isAutoGenerated()) {
                entityDescriptorBuilder.id(toEntityID(
                        new EntityPersistenceUtil.WrappedEntityParameter(unit, parameter.getInvocation())));
            }

            parameter.setUnit(unit);
        }

        return entityDescriptorBuilder
                .parameters(EntityParametersDescriptor.fromPersistenceList(parameters))
                .build();
    }

    private EntityID readEntityID(List<EntityPersistenceUtil.WrappedEntityParameter> parameters) {
        EntityPersistenceUtil.WrappedEntityParameter identifyParam = parameters.stream()
                .filter(parameter -> parameter.getUnit().isAutoGenerated())
                .findFirst()
                .orElse(null);

        return toEntityID(identifyParam);
    }

    private EntityID toEntityID(EntityPersistenceUtil.WrappedEntityParameter identifyParam) {
        return (identifyParam == null ? EntityID.NOT_FOUND :
                EntityID.fromId((Long) identifyParam.getUnit().getValue()));
    }

    public Object write(EntityDescriptor entity) {
        Object instance = JavaReflectionUtil.createInstance(entity.getRootClass());

        for (EntityParametersDescriptor.ParameterUnit parameterUnit : entity.getParameters().getParameterUnits()) {
            Optional<String> fieldNameOptional = EntityParameterNameUtil.fromGetter(parameterUnit.findGetter(entity.getRootClass()));

            fieldNameOptional.ifPresent(name ->
                    JavaReflectionUtil.setFieldValue(instance, name, parameterUnit.getValue()));
        }

        return instance;
    }
}
