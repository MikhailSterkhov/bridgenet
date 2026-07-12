package me.moonways.rmap.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Возврат метода принудительно кодируется значением, даже если тип входит в
 *  {@code ExportOptions.wrapReturnAsRemote} (спека §5.3). Допустим только на методе с
 *  wrapped-возвратом (либо его generic-аргументом) — export-audit это проверяет (§8);
 *  рантайм-эффект (by-value вместо remote-ref) — в call-слое (задача 5). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Snapshot {
}
