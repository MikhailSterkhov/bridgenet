package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapExcluded;
import me.moonways.rmap.api.RmapExportException;
import me.moonways.rmap.api.RmapSerializable;
import me.moonways.rmap.codec.CodecRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportAuditTest {

    @RmapSerializable
    static class Dto {
        UUID id;
        List<String> tags;
    }

    interface Player { String name(); }         // wrapped-интерфейс
    interface Good {
        Dto load(UUID id);
        Optional<Dto> find(String key);
        CompletableFuture<List<String>> tags();
        void fire(int code);
        Player player(UUID id);                  // wrap-возврат
        List<Player> players();                  // wrap в generic-аргументе возврата
    }

    private InterfaceManifest auditGood() {
        return ExportAudit.audit(Good.class,
                ExportOptions.builder().wrapReturnAsRemote(Player.class).build(),
                new CodecRegistry());
    }

    @Test
    void good_interface_passes_and_manifest_complete() {
        InterfaceManifest m = auditGood();
        assertThat(m.getMethodsById()).hasSize(6);
        assertThat(m.getDigest()).isEqualTo(MethodIds.interfaceDigest(Good.class));
        assertThat(m.getDecodeWhitelist())
                .contains(Dto.class.getName(), "java.lang.String", "java.util.UUID");
        assertThat(m.getWrappedInterfaces()).containsExactly(Player.class);
    }

    interface BadObject { void send(Object anything); }
    interface BadWildcard { List<?> list(); }
    interface BadCallback { void on(Runnable r); }
    interface BadParamRef { void take(Player p); }
    interface BadTypeVar { <T> T get(Class<T> c); }
    interface BadCustomColl extends List<String> { }
    interface BadCollDecl { BadCustomColl items(); }

    @Test
    void forbidden_signatures_rejected_with_full_list() {
        ExportOptions opts = ExportOptions.builder().wrapReturnAsRemote(Player.class).build();
        assertThatThrownBy(() -> ExportAudit.audit(BadObject.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("send");
        assertThatThrownBy(() -> ExportAudit.audit(BadWildcard.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class);
        assertThatThrownBy(() -> ExportAudit.audit(BadCallback.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class);
        assertThatThrownBy(() -> ExportAudit.audit(BadParamRef.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("parameter");
        assertThatThrownBy(() -> ExportAudit.audit(BadTypeVar.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("type variable");
        assertThatThrownBy(() -> ExportAudit.audit(BadCollDecl.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("collection");
    }

    interface Rescued {
        Dto load(UUID id);
        @RmapExcluded
        void send(Object anything); // непригодный метод исключён → интерфейс проходит
    }

    @Test
    void excluded_method_rescues_interface() {
        assertThatCode(() -> ExportAudit.audit(Rescued.class, ExportOptions.defaults(), new CodecRegistry()))
                .doesNotThrowAnyException();
    }

    interface TwoBad { void a(Object x); List<?> b(); }

    @Test
    void audit_aggregates_all_problems() {
        assertThatThrownBy(() -> ExportAudit.audit(TwoBad.class, ExportOptions.defaults(), new CodecRegistry()))
                .isInstanceOf(RmapExportException.class)
                .hasMessageContaining("a").hasMessageContaining("b");
    }
}
