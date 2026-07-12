package me.moonways.rmap.rpc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MethodIdsTest {

    interface Sample {
        Optional<String> find(UUID id);
        void fire(int code, String[] tags);
        long count();
    }

    private Method m(String name) throws Exception {
        for (Method mm : Sample.class.getMethods()) if (mm.getName().equals(name)) return mm;
        throw new AssertionError(name);
    }

    @Test
    void jvm_descriptor_matches_classfile_format() throws Exception {
        assertThat(MethodIds.jvmDescriptor(m("find")))
                .isEqualTo("(Ljava/util/UUID;)Ljava/util/Optional;");
        assertThat(MethodIds.jvmDescriptor(m("fire")))
                .isEqualTo("(I[Ljava/lang/String;)V");
        assertThat(MethodIds.jvmDescriptor(m("count"))).isEqualTo("()J");
    }

    @Test
    void method_id_deterministic_and_distinct() throws Exception {
        assertThat(MethodIds.methodId(m("find"))).isEqualTo(MethodIds.methodId(m("find")));
        assertThat(MethodIds.methodId(m("find"))).isNotEqualTo(MethodIds.methodId(m("fire")));
    }

    interface SameShapeA { void x(int v); void y(String s); }
    interface SameShapeB { void y(String s); void x(int v); } // тот же набор, другой порядок объявления

    @Test
    void digest_order_independent_and_sensitive_to_set() {
        assertThat(MethodIds.interfaceDigest(SameShapeA.class))
                .isEqualTo(MethodIds.interfaceDigest(SameShapeB.class));
        assertThat(MethodIds.interfaceDigest(SameShapeA.class))
                .isNotEqualTo(MethodIds.interfaceDigest(Sample.class));
    }

    interface WithExcluded {
        void kept(int v);
        @me.moonways.rmap.api.RmapExcluded
        void dropped(Object callbackish);
    }
    interface OnlyKept { void kept(int v); }

    @Test
    void excluded_methods_do_not_affect_digest() {
        assertThat(MethodIds.interfaceDigest(WithExcluded.class))
                .isEqualTo(MethodIds.interfaceDigest(OnlyKept.class));
    }
}
