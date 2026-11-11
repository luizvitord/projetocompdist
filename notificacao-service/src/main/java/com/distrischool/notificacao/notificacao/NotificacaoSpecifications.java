package com.distrischool.notificacao.notificacao;

import org.springframework.data.jpa.domain.Specification;

final class NotificacaoSpecifications {

    private NotificacaoSpecifications() {
    }

    /** Notificações do par (perfil, referenciaId); a referência nula (ADMIN) vira {@code IS NULL}. */
    static Specification<Notificacao> doDestinatario(Destinatario destinatario) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("destinatarioPerfil"), destinatario.perfil()),
                destinatario.referenciaId() == null
                        ? cb.isNull(root.get("destinatarioReferenciaId"))
                        : cb.equal(root.get("destinatarioReferenciaId"), destinatario.referenciaId()));
    }

    static Specification<Notificacao> naoLida() {
        return (root, query, cb) -> cb.isNull(root.get("lidaEm"));
    }

    static Specification<Notificacao> comId(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }
}
