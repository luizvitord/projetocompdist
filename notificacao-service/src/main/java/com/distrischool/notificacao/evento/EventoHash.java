package com.distrischool.notificacao.evento;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Identidade de um evento. Os eventos não têm id, e o mesmo evento pode chegar mais de uma vez
 * (reenvio da outbox, rebalanceamento do grupo), inclusive em offsets diferentes. O que não muda
 * entre as entregas é o conteúdo: tópico, chave e os bytes do payload, que a outbox dos produtores
 * envia exatamente como gravou (e que incluem o {@code occurredAt} do evento).
 */
public final class EventoHash {

    private EventoHash() {
    }

    /**
     * SHA-256 de tópico + chave + payload bruto, em hexadecimal (64 caracteres). Um byte zero
     * separa as três partes, para que ("a", "bc") e ("ab", "c") não gerem o mesmo hash.
     */
    public static String calcular(String topico, String chave, byte[] payload) {
        MessageDigest sha256 = sha256();
        sha256.update(topico.getBytes(StandardCharsets.UTF_8));
        sha256.update((byte) 0);
        sha256.update((chave != null ? chave : "").getBytes(StandardCharsets.UTF_8));
        sha256.update((byte) 0);
        sha256.update(payload);
        return HexFormat.of().formatHex(sha256.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            // Toda JVM é obrigada a ter SHA-256
            throw new IllegalStateException(ex);
        }
    }
}
