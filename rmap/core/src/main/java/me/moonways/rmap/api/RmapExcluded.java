package me.moonways.rmap.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Метод исключён из remote-контракта: не входит в export-audit и interfaceDigest (спека §6, §8),
 *  а вызов на прокси → локальный {@code UnsupportedOperationException} (§7.1). Аннотация ставится
 *  на самом интерфейсе — её видят обе стороны, поэтому подмножество методов (и digest) совпадает. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RmapExcluded {
}
