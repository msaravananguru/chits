package sudar.chits.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sudar.chits.audit.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
