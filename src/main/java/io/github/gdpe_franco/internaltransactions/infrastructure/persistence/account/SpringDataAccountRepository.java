package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.account;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountRepository extends JpaRepository<AccountEntity, Long> {
}
