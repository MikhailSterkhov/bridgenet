package me.moonways.bridgenet.jdbc.core.compose.impl.collection.element;

import lombok.var;
import me.moonways.bridgenet.jdbc.core.compose.CombinedStructs;
import me.moonways.bridgenet.jdbc.core.compose.MergeDirection;
import me.moonways.bridgenet.jdbc.core.compose.ParameterAddon;
import me.moonways.bridgenet.jdbc.core.compose.SubjectFunction;
import me.moonways.bridgenet.jdbc.core.compose.template.completed.CompletedPredicates;
import me.moonways.bridgenet.jdbc.core.compose.*;

import java.util.Collections;
import java.util.stream.Collectors;

public final class CollectionElementMapper {

    private static final String CHARACTERS_FORMAT = "'%s'";

    public WrappedName mapNamed(CombinedStructs.CombinedField field) {
        return WrappedName.builder()
                .value(field.getLabel())
                .build();
    }

    public WrappedField mapFull(CombinedStructs.CombinedField field) {
        return WrappedField.builder()
                .label(field.getLabel())
                .value(valueToString(field.getValue()))
                .build();
    }

    public WrappedOrder map(CombinedStructs.CombinedOrderedLabel order) {
        return WrappedOrder.builder()
                .label(order.getLabel())
                .direction(order.getDirection())
                .build();
    }

    public WrappedSubject map(SubjectFunction function, CombinedStructs.CombinedLabel label) {
        return WrappedSubject.builder()
                .function(function)
                .label(label.getLabel())
                .association(label.getCastTo())
                .build();
    }

    public WrappedJoiner map(MergeDirection left_or_right, MergeDirection inner_or_outer, CombinedStructs.CombinedMerge merge) {
        return WrappedJoiner.builder()
                .table(merge.getTable())
                .sourceLabel(merge.getLabelFrom())
                .targetLabel(merge.getLabelTo())
                .lr_direction(left_or_right)
                .io_direction(inner_or_outer)
                .build();
    }

    public WrappedCondition map(CompletedPredicates.CompletedPredicateNode node) {
        var field = node.field();

        return WrappedCondition.builder()
                .binderNext(node.binder())
                .matcher(node.matcher())
                .label(field.getLabel())
                .value(valueToString(field.getValue()))
                .build();
    }

    public WrappedSignatureParameter map(CombinedStructs.CombinedStyledParameter parameter) {
        var style = parameter.getStyle();

        var encoding = style.getEncoding();
        var defaultValue = style.getDefaultValue();

        var addons = (style.getAddons() != null ? style.getAddons() : Collections.<ParameterAddon>emptyList())
                .stream()
                .map(Enum::toString)
                .collect(Collectors.joining(" "));

        return WrappedSignatureParameter.builder()
                .addonsLabel(addons)
                .label(parameter.getName())
                .type(style.getType())
                .length(style.getLength())
                .encoding(encoding == null ? null : encoding.getCharacterStyle())
                .encodingCollate(encoding == null ? null : encoding.getCollate())
                .defaultValue(defaultValue == null || defaultValue.toString().isEmpty() ? null : valueToString(defaultValue))
                .build();
    }

    private String valueToString(Object value) {
        if (value == null) {
            return String.format(CHARACTERS_FORMAT, "null");
        }
        return isPrimitive(value.getClass()) ? value.toString()
                : String.format(CHARACTERS_FORMAT, value);
    }

    private boolean isPrimitive(Class<?> cls) {
        return cls.isPrimitive() || Number.class.isAssignableFrom(cls)
                || Boolean.class.isAssignableFrom(cls);
    }
}
