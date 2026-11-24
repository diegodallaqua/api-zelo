package com.zelo.compartilhado.seguranca;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/** Autenticação baseada no JWT da Zelo, com {@link UsuarioAutenticado} como principal. */
public class ZeloAuthenticationToken extends AbstractAuthenticationToken {

    private final UsuarioAutenticado usuario;
    private final transient Jwt jwt;

    public ZeloAuthenticationToken(UsuarioAutenticado usuario, Jwt jwt,
                                   Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.usuario = usuario;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public UsuarioAutenticado getPrincipal() {
        return usuario;
    }

    @Override
    public String getName() {
        return usuario.id().toString();
    }
}
