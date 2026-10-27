package in.eko.eloka.partner.onboarding.internal;

import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** The number on a voter ID (EPIC) or driving licence, encrypted, with a masked copy. */
@Entity
@Table(name = "partner_id_number")
class IdNumber extends BaseEntity {

	enum Type {
		VOTER_ID,
		DRIVING_LICENCE
	}

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "id_type", nullable = false, length = 20)
	private Type type;

	@Column(name = "number_enc", nullable = false, columnDefinition = "VARBINARY(128)")
	private byte[] numberEnc;

	@Column(name = "number_masked", nullable = false, length = 20)
	private String numberMasked;

	protected IdNumber() {
	}

	IdNumber(UUID applicationId, Type type) {
		this.applicationId = applicationId;
		this.type = type;
	}

	void set(byte[] encrypted, String masked) {
		this.numberEnc = encrypted;
		this.numberMasked = masked;
	}

	Type getType() {
		return type;
	}

	String getNumberMasked() {
		return numberMasked;
	}
}
