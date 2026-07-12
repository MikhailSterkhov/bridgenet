package me.moonways.rmap.rpc;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Set;

/** Опции экспорта интерфейса (спека §8). {@code wrapReturnAsRemote} — интерфейсы, кодируемые
 *  как remote-ref ТОЛЬКО в позиции возврата; {@code serialDispatch} — строго последовательная
 *  диспетчеризация вызовов одного subject (задел call-слоя). */
@Value
@Builder
public class ExportOptions {

    @Singular("wrapReturnAsRemote")
    Set<Class<?>> wrapReturnAsRemote;

    boolean serialDispatch;

    public static ExportOptions defaults() {
        return ExportOptions.builder().build();
    }
}
