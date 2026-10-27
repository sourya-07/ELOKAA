package in.eko.eloka.partner.audit.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Single row holding the end of the hash chain. Locked while appending, so seq is gap-free. */
@Entity
@Table(name = "audit_chain_head")
class AuditChainHead {

	@Id
	private int id;

	@Column(name = "last_seq", nullable = false)
	private long lastSeq;

	@Column(name = "last_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] lastHash;

	protected AuditChainHead() {
	}

	long lastSeq() {
		return lastSeq;
	}

	byte[] lastHash() {
		return lastHash;
	}

	void advance(long seq, byte[] hash) {
		this.lastSeq = seq;
		this.lastHash = hash;
	}
}
