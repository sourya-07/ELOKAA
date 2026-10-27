package in.eko.eloka.partner.onboarding.internal;

import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import in.eko.eloka.partner.verification.EvidenceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * The applicant's home address. Street-level parts are encrypted; state and pincode stay readable
 * for reporting. From DigiLocker it counts as verified; typed by hand it waits for staff.
 */
@Entity
@Table(name = "partner_address")
class PartnerAddress extends BaseEntity {

	enum Source {
		DIGILOCKER,
		SELF_DECLARED
	}

	@Column(name = "application_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "line1_enc", nullable = false, columnDefinition = "VARBINARY(512)")
	private byte[] line1Enc;

	@Column(name = "line2_enc", columnDefinition = "VARBINARY(512)")
	private byte[] line2Enc;

	@Column(name = "city_enc", nullable = false, columnDefinition = "VARBINARY(256)")
	private byte[] cityEnc;

	@Column(length = 60)
	private String district;

	@Column(nullable = false, length = 60)
	private String state;

	@Column(nullable = false, length = 6)
	private String pincode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Source source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EvidenceStatus status;

	protected PartnerAddress() {
	}

	PartnerAddress(UUID applicationId) {
		this.applicationId = applicationId;
	}

	void set(byte[] line1, byte[] line2, byte[] city, String district, String state, String pincode, Source source) {
		this.line1Enc = line1;
		this.line2Enc = line2;
		this.cityEnc = city;
		this.district = district;
		this.state = state;
		this.pincode = pincode;
		this.source = source;
		this.status = source == Source.DIGILOCKER ? EvidenceStatus.VERIFIED : EvidenceStatus.MANUAL_REVIEW_REQUIRED;
	}

	void decide(boolean verified) {
		this.status = verified ? EvidenceStatus.VERIFIED : EvidenceStatus.REJECTED;
	}

	Source getSource() {
		return source;
	}

	EvidenceStatus getStatus() {
		return status;
	}

	String getState() {
		return state;
	}

	String getPincode() {
		return pincode;
	}

	byte[] getLine1Enc() {
		return line1Enc;
	}

	byte[] getLine2Enc() {
		return line2Enc;
	}

	byte[] getCityEnc() {
		return cityEnc;
	}

	String getDistrict() {
		return district;
	}
}
