package in.eko.eloka.partner.identity.internal;

import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A person who has proved they own a mobile number. One per mobile. */
@Entity
@Table(name = "applicant")
class Applicant extends BaseEntity {

	@Column(name = "mobile_enc", nullable = false, columnDefinition = "VARBINARY(128)")
	private byte[] mobileEnc;

	@Column(name = "mobile_hash", nullable = false, unique = true, columnDefinition = "BINARY(32)")
	private byte[] mobileHash;

	@Column(name = "mobile_masked", nullable = false, length = 10)
	private String mobileMasked;

	protected Applicant() {
	}

	Applicant(byte[] mobileEnc, byte[] mobileHash, String mobileMasked) {
		this.mobileEnc = mobileEnc;
		this.mobileHash = mobileHash;
		this.mobileMasked = mobileMasked;
	}
}
