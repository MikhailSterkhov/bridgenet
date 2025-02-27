package me.moonways.bridgenet.api.inject.bean.factory.type;

import me.moonways.bridgenet.api.inject.bean.BeanException;
import me.moonways.bridgenet.api.inject.bean.factory.BeanFactory;
import me.moonways.bridgenet.api.util.reflection.ReflectionUtils;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeFactory implements BeanFactory {

    private static final Unsafe UNSAFE;
    static {
        try {
            Field unsafeInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
            ReflectionUtils.grantAccess(unsafeInstanceField);

            UNSAFE = ((Unsafe) unsafeInstanceField.get(null));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public <T> T create(Class<T> cls) {
        try {
            //noinspection unchecked
            return (T) UNSAFE.allocateInstance(cls);
        } catch (InstantiationException exception) {
            throw new BeanException(exception);
        }
    }
}
