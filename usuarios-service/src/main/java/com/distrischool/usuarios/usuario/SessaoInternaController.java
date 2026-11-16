package com.distrischool.usuarios.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contrato interno, não publicado pelo gateway, para conferir sessões emitidas anteriormente. */
@RestController
@RequestMapping("/interno/sessoes")
@RequiredArgsConstructor
public class SessaoInternaController {

    private final UsuarioService usuarioService;

    @GetMapping("/{id}")
    public EstadoSessaoResponse buscar(@PathVariable Long id) {
        return usuarioService.estadoSessao(id);
    }
}
