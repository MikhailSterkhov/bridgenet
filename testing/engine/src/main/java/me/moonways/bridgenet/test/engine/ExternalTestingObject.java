package me.moonways.bridgenet.test.engine;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;
import me.moonways.bridgenet.api.util.reflection.ReflectionUtils;
import me.moonways.bridgenet.test.engine.persistance.PersistenceAcceptType;
import me.moonways.bridgenet.test.engine.persistance.TestExternal;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.TestClass;

@Getter
@ToString
public class ExternalTestingObject extends TestingObject {

    private final PersistenceAcceptType persistenceAcceptType;
    private final FrameworkField frameworkField;

    public ExternalTestingObject(FrameworkField frameworkField) {
        super(new TestClass(frameworkField.getType()));

        this.persistenceAcceptType = frameworkField.getAnnotation(TestExternal.class).acceptType();
        this.frameworkField = frameworkField;
    }

    @SneakyThrows
    public void setInstanceAt(Object source) {
        ReflectionUtils.setField(source, frameworkField.getName(), getInstance());
    }
}