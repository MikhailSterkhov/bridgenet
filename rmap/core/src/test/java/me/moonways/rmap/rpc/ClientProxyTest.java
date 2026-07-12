package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapExcluded;
import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapRemoteException;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.api.RmapTimeoutException;
import me.moonways.rmap.api.RmapConnectionException;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.transport.RmapTransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientProxyTest {

    public interface Calc {
        int add(int a, int b);
        CompletableFuture<Integer> addAsync(int a, int b);
        Optional<String> find(String key);
        void touch();
        String explode(String msg);
        String slow(int millis);
        @RmapExcluded
        void localOnly(Object callback);
        // default-метод (§7.1): НЕ часть remote-контракта (contractMethods его исключает).
        default int defaultTwice(int a, int b) { return add(a, b) * 2; }
    }

    public static class CalcImpl implements Calc {
        public int add(int a, int b) { return a + b; }
        public CompletableFuture<Integer> addAsync(int a, int b) {
            return CompletableFuture.completedFuture(a + b);
        }
        public Optional<String> find(String key) {
            return "hit".equals(key) ? Optional.of("value") : Optional.empty();
        }
        public void touch() { }
        public String explode(String msg) { throw new IllegalArgumentException(msg); }
        public String slow(int millis) {
            try { Thread.sleep(millis); } catch (InterruptedException ignored) { }
            return "slept";
        }
        public void localOnly(Object callback) { }
    }

    private RmapServer server;
    private RmapClient client;

    private RmapConfig cfg() {
        return RmapConfig.builder().access(Access.privateKey("b2-key"))
                .appVersion("b2").clientName("proxy-test").build();
    }

    private Calc connect() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Services").export("Calc", Calc.class, new CalcImpl());
        server.start();
        client = net.newClient(cfg());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        return client.lookup("Services/Calc", Calc.class);
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    @Test
    void sync_async_optional_void_roundtrip() throws Exception {
        Calc calc = connect();
        assertThat(calc.add(2, 3)).isEqualTo(5);
        assertThat(calc.addAsync(20, 30).get(5, TimeUnit.SECONDS)).isEqualTo(50);
        assertThat(calc.find("hit")).contains("value");
        assertThat(calc.find("miss")).isEmpty();
        calc.touch(); // void: DONE(NULL), не бросает
    }

    @Test
    void remote_exception_carries_class_message_and_synthetic_stack() throws Exception {
        Calc calc = connect();
        assertThatThrownBy(() -> calc.explode("bang"))
                .isInstanceOf(RmapRemoteException.class)
                .hasMessageContaining("java.lang.IllegalArgumentException")
                .hasMessageContaining("bang");
        try {
            calc.explode("again");
        } catch (RmapRemoteException e) {
            boolean hasRemoteFrame = false;
            for (StackTraceElement el : e.getStackTrace()) {
                if (el.getClassName().contains("CalcImpl")) hasRemoteFrame = true;
            }
            assertThat(hasRemoteFrame).as("синтетический стек несёт remote-кадры").isTrue();
        }
    }

    @Test
    void deadline_fires_and_late_answer_is_dropped_silently() throws Exception {
        Calc calc = connect();
        Calc fast = client.withOptions(calc, RmapCallOptions.deadline(java.time.Duration.ofMillis(200)));
        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> fast.slow(2000))
                .isInstanceOf(RmapTimeoutException.class);
        assertThat(System.currentTimeMillis() - start).isLessThan(1500);
        // соединение живо после таймаута (поздний DONE отброшен молча)
        assertThat(calc.add(1, 1)).isEqualTo(2);
    }

    @Test
    void deadline_completes_even_when_cancel_send_throws() throws Exception {
        // Регрессия (CRITICAL): под outbound-backpressure отправка CANCEL из onDeadline бросает
        // RmapTransportException. Раньше CANCEL шёл ДО завершения future → бросок пропускал
        // completeLater, callId уже снят из pending → sync-вызывающий на cf.get() (без таймаута)
        // виснул НАВСЕГДА. После фикса: future завершается таймаутом ДО best-effort CANCEL, а
        // провал CANCEL проглатывается — hang невозможен. Провал send инъектируем детерминированно.
        Calc calc = connect();
        ClientSession session = client.liveSession();
        assertThat(session).isNotNull();
        AtomicBoolean cancelAttempted = new AtomicBoolean(false);
        session.setCancelSender(callId -> {
            cancelAttempted.set(true);
            throw new RmapTransportException("outbound limit exceeded"); // как RmapConnection.send под лимитом
        });

        Calc fast = client.withOptions(calc, RmapCallOptions.deadline(java.time.Duration.ofMillis(200)));
        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> fast.slow(2000))
                .isInstanceOf(RmapTimeoutException.class); // завершился, НЕ завис
        assertThat(System.currentTimeMillis() - start)
                .as("future завершён таймаутом задолго до server-sleep — не hang").isLessThan(1500);
        assertThat(cancelAttempted.get())
                .as("путь отправки CANCEL действительно исполнен и бросил").isTrue();
        // провал CANCEL проглочен, соединение живо: обычный вызов работает.
        assertThat(calc.add(1, 1)).isEqualTo(2);
    }

    @Test
    void excluded_method_is_local_uoe() throws Exception {
        Calc calc = connect();
        assertThatThrownBy(() -> calc.localOnly("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void default_method_is_handled_locally_not_over_wire() throws Exception {
        // Fix 3 (§7.1): default-метод интерфейса НЕ уходит в сеть (сервер ответил бы INVALID_SIGNATURE
        // → RmapRemoteException). Обрабатывается локально → UnsupportedOperationException (выбранный
        // вариант v1: нет надёжного на Java 8–24 пути локального исполнения default). Обычные методы живы.
        Calc calc = connect();
        assertThatThrownBy(() -> calc.defaultTwice(2, 3))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("default");
        assertThat(calc.add(2, 3)).isEqualTo(5); // обычный метод по-прежнему работает
    }

    @Test
    void pending_calls_fail_fast_on_disconnect() throws Exception {
        Calc calc = connect();
        CompletableFuture<Integer> hanging = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try { calc.slow(10_000); hanging.complete(0); }
            catch (RuntimeException e) { hanging.completeExceptionally(e); }
        });
        t.start();
        Thread.sleep(300);            // вызов ушёл на сервер
        server.stop();                 // разрыв
        assertThatThrownBy(() -> hanging.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(RmapConnectionException.class); // fast-fail, не 10с
        t.join(1000);
    }

    @Test
    void proxy_object_methods_are_local() throws Exception {
        Calc calc = connect();
        assertThat(calc.toString()).contains("Services/Calc");
        assertThat(calc.equals(calc)).isTrue();
        assertThat(calc.hashCode()).isEqualTo(calc.hashCode());
    }
}
