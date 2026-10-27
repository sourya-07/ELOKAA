package in.eko.eloka.partner.audit.internal;

import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}

interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, Integer> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select h from AuditChainHead h where h.id = 1")
	AuditChainHead lockHead();
}
