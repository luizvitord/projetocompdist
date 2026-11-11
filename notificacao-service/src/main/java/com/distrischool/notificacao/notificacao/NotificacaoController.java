package com.distrischool.notificacao.notificacao;

import com.distrischool.notificacao.seguranca.DestinatarioJwt;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificações do usuário logado. O destinatário vem sempre das claims do JWT ({@code perfil} e
 * {@code referenciaId}), nunca de parâmetros: ninguém consulta nem altera notificações de outro.
 */
@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    /** Mais recentes primeiro; o id desempata notificações criadas no mesmo instante. */
    private static final Sort MAIS_RECENTES = Sort.by(Sort.Order.desc("criadaEm"), Sort.Order.desc("id"));

    private final NotificacaoService notificacaoService;

    @GetMapping
    public PagedModel<NotificacaoResponse> buscar(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean somenteNaoLidas,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), MAIS_RECENTES);
        return new PagedModel<>(notificacaoService.buscar(DestinatarioJwt.de(jwt), somenteNaoLidas, pagina));
    }

    @GetMapping("/nao-lidas/contagem")
    public ContagemResponse contarNaoLidas(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return new ContagemResponse(notificacaoService.contarNaoLidas(DestinatarioJwt.de(jwt)));
    }

    @PatchMapping("/{id}/lida")
    public NotificacaoResponse marcarComoLida(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                              @PathVariable Long id) {
        return notificacaoService.marcarComoLida(DestinatarioJwt.de(jwt), id);
    }

    @PostMapping("/lidas")
    public MarcadasResponse marcarTodasComoLidas(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return new MarcadasResponse(notificacaoService.marcarTodasComoLidas(DestinatarioJwt.de(jwt)));
    }
}
