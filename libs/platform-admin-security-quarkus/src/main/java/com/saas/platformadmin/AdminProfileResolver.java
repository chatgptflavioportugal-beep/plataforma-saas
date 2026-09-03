package com.saas.platformadmin;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta o perfil administrativo de um usuário. Cada serviço implementa isto sobre seu
 * próprio DAO (SQL nativo ou Panache) — a lib não assume nenhum ORM/estilo de persistência
 * específico.
 */
public interface AdminProfileResolver {

    Optional<AdminProfile> findProfile(UUID userId);
}
