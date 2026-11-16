package com.distrischool.usuarios.usuario;

/** Estado mínimo usado internamente pelos serviços que validam uma sessão já emitida. */
public record EstadoSessaoResponse(boolean ativo, long versaoSessao) {
}
