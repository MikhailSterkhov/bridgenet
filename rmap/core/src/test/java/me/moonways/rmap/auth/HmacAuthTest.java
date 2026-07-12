package me.moonways.rmap.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HmacAuthTest {

    private HmacAuth.AuthInput input(byte[] challenge, byte[] nonce) {
        return new HmacAuth.AuthInput(challenge, nonce, 1, 1, "bridgenet-1.3", "spigot-01");
    }

    @Test
    void random_bytes_are_32_and_vary() {
        byte[] a = HmacAuth.randomBytes32();
        byte[] b = HmacAuth.randomBytes32();
        assertThat(a).hasSize(32);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void client_and_server_mac_differ_for_same_input() {
        byte[] key = "secret".getBytes(StandardCharsets.UTF_8);
        HmacAuth.AuthInput in = input(new byte[32], new byte[32]);
        assertThat(HmacAuth.clientMac(key, in)).isNotEqualTo(HmacAuth.serverMac(key, in));
    }

    @Test
    void mac_is_deterministic_for_same_key_and_input() {
        byte[] key = "k".getBytes(StandardCharsets.UTF_8);
        HmacAuth.AuthInput in = input(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}); // длины не важны для детерминизма
        assertThat(HmacAuth.clientMac(key, in)).isEqualTo(HmacAuth.clientMac(key, in));
        assertThat(HmacAuth.clientMac(key, in)).hasSize(32); // HMAC-SHA256
    }

    @Test
    void wrong_key_yields_different_mac() {
        HmacAuth.AuthInput in = input(new byte[32], new byte[32]);
        byte[] good = HmacAuth.clientMac("right".getBytes(StandardCharsets.UTF_8), in);
        byte[] bad = HmacAuth.clientMac("wrong".getBytes(StandardCharsets.UTF_8), in);
        assertThat(good).isNotEqualTo(bad);
    }

    @Test
    void field_boundary_is_canonicalized() {
        // ("ab","c") и ("a","bc") дают РАЗНЫЕ MAC (длинно-префиксная канонизация)
        byte[] key = "k".getBytes(StandardCharsets.UTF_8);
        byte[] m1 = HmacAuth.clientMac(key, new HmacAuth.AuthInput(new byte[32], new byte[32], 1, 1, "ab", "c"));
        byte[] m2 = HmacAuth.clientMac(key, new HmacAuth.AuthInput(new byte[32], new byte[32], 1, 1, "a", "bc"));
        assertThat(m1).isNotEqualTo(m2);
    }

    @Test
    void constant_time_equals_works() {
        assertThat(HmacAuth.constantTimeEquals(new byte[]{1, 2}, new byte[]{1, 2})).isTrue();
        assertThat(HmacAuth.constantTimeEquals(new byte[]{1, 2}, new byte[]{1, 3})).isFalse();
        assertThat(HmacAuth.constantTimeEquals(new byte[]{1}, new byte[]{1, 2})).isFalse();
    }
}
