package me.moonways.bridgenet.jdbc.entity.descriptor;

import lombok.*;
import me.moonways.bridgenet.jdbc.core.compose.ParameterAddon;
import me.moonways.bridgenet.jdbc.entity.util.EntityPersistenceUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EntityParametersDescriptor {

    public static EntityParametersDescriptor fromPersistenceList(List<EntityPersistenceUtil.WrappedEntityParameter> parameters) {
        return new EntityParametersDescriptor(
                parameters.stream().map(EntityPersistenceUtil.WrappedEntityParameter::getUnit)
                        .collect(Collectors.toList()));
    }

    private List<ParameterUnit> parameterUnits = Collections.synchronizedList(new LinkedList<>());

    public EntityParametersDescriptor addUnit(ParameterUnit parameterUnit) {
        parameterUnits.add(parameterUnit);
        return this;
    }

    public List<ParameterUnit> getExternalUnits() {
        return parameterUnits.stream().filter(ParameterUnit::isExternal).collect(Collectors.toList());
    }

    public Optional<ParameterUnit> findIdUnit() {
        return parameterUnits.stream().filter(ParameterUnit::isAutoGenerated).findFirst();
    }

    public List<ParameterUnit> getParameterUnits() {
        List<ParameterUnit> parameterUnits = this.parameterUnits;
        parameterUnits.sort(Comparator.comparingInt(ParameterUnit::getOrder));
        return parameterUnits;
    }

    @Getter
    @Builder(toBuilder = true)
    @ToString
    @EqualsAndHashCode
    public static class ParameterUnit implements Comparable<ParameterUnit> {

        // @EntityParameter annotation data.
        private final int order;
        private final String id;

        private final ParameterAddon[] indexes;

        // entity annotation type flags.
        private final boolean isAutoGenerated;
        private final boolean isExternal;

        // field reflection data.
        @Setter
        private Class<?> type;
        @Setter
        private Object value;

        public Method findGetter(Class<?> source) {
            for (Method declaredMethod : source.getDeclaredMethods()) {
                if (EntityPersistenceUtil.isParameter(declaredMethod)
                        && Objects.equals(EntityPersistenceUtil.getParameterId(declaredMethod), id)
                        && Arrays.equals(EntityPersistenceUtil.getParameterIndexes(declaredMethod), indexes)) {
                    return declaredMethod;
                }
            }
            return null;
        }

        @Override
        public int compareTo(@NotNull EntityParametersDescriptor.ParameterUnit other) {
            return Integer.compare(other.order, this.order);
        }
    }
}
