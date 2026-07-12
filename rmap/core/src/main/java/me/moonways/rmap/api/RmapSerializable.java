package me.moonways.rmap.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Класс разрешён рефлексивному TLV-кодеку RMAP (спека §5.3). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RmapSerializable {
}
