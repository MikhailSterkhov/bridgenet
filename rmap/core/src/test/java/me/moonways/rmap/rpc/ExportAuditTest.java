package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapExcluded;
import me.moonways.rmap.api.RmapExportException;
import me.moonways.rmap.api.RmapSerializable;
import me.moonways.rmap.api.Snapshot;
import me.moonways.rmap.codec.CodecRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    // ---- §8.2 (перечень): wrapped-тип допустим в generic-аргументе List/Set/Collection/Optional/CF,
    //      но НЕ в Map. Map остаётся контейнером для НЕ-wrapped типов (Map<String,Dto> проходит). ----

    interface MapWrappedValue { Map<String, Player> board(); }   // wrapped как Map-value возврата
    interface MapWrappedKey { Map<Player, String> byPlayer(); }  // wrapped как Map-key возврата
    interface MapPlainDto { Map<String, Dto> data(); }           // обычный Map — допустим

    @Test
    void wrapped_type_in_map_generic_argument_is_rejected_but_plain_map_allowed() {
        ExportOptions opts = ExportOptions.builder().wrapReturnAsRemote(Player.class).build();
        // wrapped-тип как значение Map — не в перечне §8.2 → ошибка аудита.
        assertThatThrownBy(() -> ExportAudit.audit(MapWrappedValue.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("Map generic argument");
        // wrapped-тип как ключ Map — тоже ошибка.
        assertThatThrownBy(() -> ExportAudit.audit(MapWrappedKey.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("Map generic argument");
        // обычный Map<String,Dto> в возврате остаётся допустимым (Map — контейнер НЕ-wrapped типов).
        assertThatCode(() -> ExportAudit.audit(MapPlainDto.class, opts, new CodecRegistry()))
                .doesNotThrowAnyException();
    }

    // ---- §8.7: @Snapshot требует wrapped-возврат (иначе снимок-семантика бессмысленна) ----------

    interface SnapshotWithoutWrapped { @Snapshot Dto snap(UUID id); }   // возврат НЕ wrapped
    interface SnapshotWithWrapped { @Snapshot Player snap(UUID id); }   // возврат wrapped

    @Test
    void snapshot_requires_wrapped_return_type() {
        ExportOptions opts = ExportOptions.builder().wrapReturnAsRemote(Player.class).build();
        // @Snapshot на методе без wrapped-возврата → ошибка export-аудита (SERVER-режим).
        assertThatThrownBy(() -> ExportAudit.audit(SnapshotWithoutWrapped.class, opts, new CodecRegistry()))
                .isInstanceOf(RmapExportException.class).hasMessageContaining("Snapshot");
        // @Snapshot на методе с wrapped-возвратом → проходит (снимок by-value wrap-типа, §5.3/§10).
        assertThatCode(() -> ExportAudit.audit(SnapshotWithWrapped.class, opts, new CodecRegistry()))
                .doesNotThrowAnyException();
    }

    // ---- §8.4: резолв type-variable из ParameterizedType-позиции; резолвленный тип → whitelist ----

    @RmapSerializable static class Comp { String text; }
    @RmapSerializable static class Title<T> { T value; }          // поле T резолвится позицией
    interface HasTitle { Title<Comp> title(); }                   // Title<Comp> → T читается как Comp

    @Test
    void type_variable_resolved_from_parameterized_position_and_whitelisted() {
        InterfaceManifest m = ExportAudit.audit(HasTitle.class,
                ExportOptions.defaults(), new CodecRegistry());
        // аудит проходит: поле T класса Title резолвится из Title<Comp> в конкретный Comp.
        // резолвленный тип попадает в decode-whitelist (иначе ответ с Comp внутри Title → CODEC_ERROR).
        assertThat(m.getDecodeWhitelist())
                .contains(Title.class.getName(), Comp.class.getName());
    }

    // ---- §8.6: детектор methodId-коллизий (первые 8 байт SHA-256(name+descriptor)) --------------

    @Test
    void method_ids_are_distinct_and_manifest_has_no_false_collision() {
        // §8.6: run() детектит коллизию methodId по ключу MethodIds.methodId(method); при равном
        // ключе двух РАЗНЫХ методов → RmapExportException. Естественную SHA-256-коллизию (как и два
        // различных Method-объекта с одинаковой сигнатурой) построить нельзя — Java отвергает clash
        // сигнатур на компиляции, а bridge-методы имеют РАЗНЫЙ эрейзнутый дескриптор. Поэтому
        // проверяем корректность детектора без фиктивной коллизии: (а) детектор ключуется на methodId,
        // который РАЗЛИЧЕН для различных методов; (б) валидный интерфейс не даёт ложной коллизии —
        // methodsById несёт РОВНО столько записей, сколько контрактных методов, и его ключи совпадают
        // с независимо посчитанными methodId (коллизия схлопнула бы записи / добавила бы проблему).
        InterfaceManifest m = auditGood();
        List<Method> contract = MethodIds.contractMethods(Good.class);
        assertThat(m.getMethodsById()).hasSize(contract.size());

        Set<Long> ids = new HashSet<>();
        for (Method method : contract) {
            assertThat(ids.add(MethodIds.methodId(method)))
                    .as("methodId уникален на метод: " + method).isTrue();
        }
        assertThat(m.getMethodsById().keySet()).containsExactlyInAnyOrderElementsOf(ids);
    }
}
