package com.zelo.localidade.infra;

import com.zelo.localidade.domain.Estado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstadoRepository extends JpaRepository<Estado, Integer> {

    List<Estado> findAllByOrderByNomeAsc();
}
