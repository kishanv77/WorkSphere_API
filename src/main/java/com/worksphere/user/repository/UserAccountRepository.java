package com.worksphere.user.repository;

import com.worksphere.user.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByIdAndTenant_Id(UUID userId, UUID tenantId);

    Page<UserAccount> findAllByTenant_Id(UUID tenantId, Pageable pageable);
}
