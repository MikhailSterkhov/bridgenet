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

    // ---- I6: Optional/CompletableFuture кодируемы ТОЛЬКО как верхний тип возврата -------------

    interface BadOptionalParam { void take(Optional<String> maybe); }
    interface BadCfParam { void take(CompletableFuture<String> f); }
    @RmapSerializable static class CfFieldDto { CompletableFuture<String> pending; }
    interface BadCfField { CfFieldDto load(); }
    @RmapSerializable static class OptFieldDto { Optional<String> maybe; }
    interface BadOptField { OptFieldDto load(); }
    interface BadListOptional { List<Optional<String>> items(); }
    interface BadNestedCfOptional { CompletableFuture<Optional<String>> weird(); }
    interface GoodOptionalReturn { Optional<String> find(String key); }
    interface GoodCfReturn { CompletableFuture<String> fetch(); }

    @Test
    void optional_and_cf_only_allowed_as_top_level_return() {
        ExportOptions opts = ExportOptions.defaults();
        // параметр
        assertThatThrownBy(() -> ExportAudit.audit(BadOptionalParam.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("Optional");
        assertThatThrownBy(() -> ExportAudit.audit(BadCfParam.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("CompletableFuture");
        // поле DTO (§5.2: в полях запрещены)
        assertThatThrownBy(() -> ExportAudit.audit(BadCfField.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("CompletableFuture");
        assertThatThrownBy(() -> ExportAudit.audit(BadOptField.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("Optional");
        // вложение: List<Optional<X>> и CF<Optional<X>> — оба отказ
        assertThatThrownBy(() -> ExportAudit.audit(BadListOptional.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("Optional");
        assertThatThrownBy(() -> ExportAudit.audit(BadNestedCfOptional.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("Optional");
        // верхний тип возврата — ок
        assertThatCode(() -> ExportAudit.audit(GoodOptionalReturn.class, opts, new CodecRegistry()))
                .doesNotThrowAnyException();
        assertThatCode(() -> ExportAudit.audit(GoodCfReturn.class, opts, new CodecRegistry()))
                .doesNotThrowAnyException();
    }

    // ---- I5: Mode.CLIENT принимает одно-методный интерфейс в ВОЗВРАТЕ, но не в параметре -------

    interface OneMethodIface { String name(); }              // одно-методный (isFunctionalLike)
    interface ReturnsOneMethod { OneMethodIface who(); }
    interface ParamOneMethod { void set(OneMethodIface w); }

    @Test
    void client_accepts_single_method_interface_in_return_but_not_in_param() {
        ExportOptions opts = ExportOptions.defaults();
        CodecRegistry reg = new CodecRegistry();
        // CLIENT + позиция возврата: одно-методный интерфейс — потенциальный remote-ref (проходит).
        assertThatCode(() -> ExportAudit.audit(ReturnsOneMethod.class, opts, reg, ExportAudit.Mode.CLIENT))
                .doesNotThrowAnyException();
        // CLIENT + позиция параметра: тот же интерфейс — ошибка (functional callback).
        assertThatThrownBy(() -> ExportAudit.audit(ParamOneMethod.class, opts, reg, ExportAudit.Mode.CLIENT))
                .isInstanceOf(RmapExportException.class);
        // SERVER + возврат без wrapReturnAsRemote: functional-интерфейс — ошибка (прежнее поведение).
        assertThatThrownBy(() -> ExportAudit.audit(ReturnsOneMethod.class, opts, reg, ExportAudit.Mode.SERVER))
                .isInstanceOf(RmapExportException.class);
    }

    // ---- I4: граф wrapped-интерфейса аудируется и вливается в whitelist; непригодный → отказ ----

    @RmapSerializable static class RefOnlyDto { String data; }
    interface WrappedWithDto {                 // ref-интерфейс: RefOnlyDto НЕ виден subject-сигнатурам
        int use(RefOnlyDto dto);
        RefOnlyDto make();
    }
    interface SubjectExportingWrapped {
        WrappedWithDto get(String name);       // wrap-возврат
        int ping();                            // subject-сигнатуры RefOnlyDto НЕ упоминают
    }

    @Test
    void wrapped_interface_graph_is_merged_into_subject_whitelist() {
        InterfaceManifest m = ExportAudit.audit(SubjectExportingWrapped.class,
                ExportOptions.builder().wrapReturnAsRemote(WrappedWithDto.class).build(), new CodecRegistry());
        // DTO из графа wrapped-интерфейса (параметр use / возврат make) обязан быть в whitelist
        // subject'а, хотя subject-сигнатуры его не содержат — иначе ref-вызов/ответ → CODEC_ERROR.
        assertThat(m.getDecodeWhitelist()).contains(RefOnlyDto.class.getName());
        assertThat(m.getWrappedInterfaces()).containsExactly(WrappedWithDto.class);
    }

    interface BadWrapped { void on(Runnable callback); }   // callback-параметр → непригоден
    interface SubjectBadWrapped { BadWrapped get(); }

    @Test
    void unusable_wrapped_interface_fails_export() {
        assertThatThrownBy(() -> ExportAudit.audit(SubjectBadWrapped.class,
                ExportOptions.builder().wrapReturnAsRemote(BadWrapped.class).build(), new CodecRegistry()))
                .isInstanceOf(RmapExportException.class)
                .hasMessageContaining("wrapped");
    }
}
