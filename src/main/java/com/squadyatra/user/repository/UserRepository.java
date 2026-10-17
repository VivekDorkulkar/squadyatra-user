package com.squadyatra.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.squadyatra.user.entities.UserAccount;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.UserType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findBySupabaseUserId(UUID supabaseUserId);

    Optional<UserAccount> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.email = :email")
    Optional<UserAccount> findForUpdateByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.id = :id")
    Optional<UserAccount> findForUpdate(UUID id);

    @Query("select account from UserAccount account where (:status is null or account.status = :status) "
            + "and (:type is null or account.userType = :type)")
    Page<UserAccount> search(AccountStatus status, UserType type, Pageable pageable);
}