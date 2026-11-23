package com.zelo.usuario.infra;

import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.domain.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByPerfilAndExcluidoEmIsNull(Perfil perfil);

    List<Usuario> findByPerfilAndExcluidoEmIsNull(Perfil perfil);

    long countByEnderecoId(UUID enderecoId);

    @Query(value = """
            select u from Usuario u left join u.endereco e
            where (:perfil is null or u.perfil = :perfil)
              and (:cidadeId is null or e.cidadeId = :cidadeId)
              and (:termo is null or lower(u.nome) like :termo or lower(u.email) like :termo)
              and (:incluirExcluidos = true or u.excluidoEm is null)
            order by u.nome
            """,
            countQuery = """
                    select count(u) from Usuario u left join u.endereco e
                    where (:perfil is null or u.perfil = :perfil)
                      and (:cidadeId is null or e.cidadeId = :cidadeId)
                      and (:termo is null or lower(u.nome) like :termo or lower(u.email) like :termo)
                      and (:incluirExcluidos = true or u.excluidoEm is null)
                    """)
    Page<Usuario> pesquisar(@Param("perfil") Perfil perfil, @Param("cidadeId") Integer cidadeId,
                            @Param("termo") String termo, @Param("incluirExcluidos") boolean incluirExcluidos,
                            Pageable pageable);
}
