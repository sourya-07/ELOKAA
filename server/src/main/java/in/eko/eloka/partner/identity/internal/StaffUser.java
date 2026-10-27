package in.eko.eloka.partner.identity.internal;

import java.util.EnumSet;
import java.util.Set;
import in.eko.eloka.partner.identity.StaffView;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import in.eko.eloka.partner.shared.security.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * An Eko staff member who reviews applications. Identified by their Google email, stored
 * encrypted with a blind index for lookups and a masked copy for display.
 */
@Entity
@Table(name = "staff_user")
class StaffUser extends BaseEntity {

	@Column(name = "email_enc", nullable = false, columnDefinition = "VARBINARY(512)")
	private byte[] emailEnc;

	@Column(name = "email_hash", nullable = false, unique = true, columnDefinition = "BINARY(32)")
	private byte[] emailHash;

	@Column(name = "email_masked", nullable = false, length = 254)
	private String emailMasked;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	/** Only for local/test OTP sign-in. */
	@Column(name = "mobile_hash", unique = true, columnDefinition = "BINARY(32)")
	private byte[] mobileHash;

	@Column(nullable = false)
	private boolean active;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "staff_role_grant", joinColumns = @JoinColumn(name = "staff_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Set<Role> roles = EnumSet.noneOf(Role.class);

	protected StaffUser() {
	}

	StaffUser(byte[] emailEnc, byte[] emailHash, String emailMasked, String displayName) {
		this.emailEnc = emailEnc;
		this.emailHash = emailHash;
		this.emailMasked = emailMasked;
		this.displayName = displayName;
		this.active = true;
	}

	/** Only staff roles can be granted; an applicant role here would be a bug. */
	void update(String newDisplayName, byte[] newMobileHash, Set<Role> newRoles, boolean isActive) {
		if (newRoles.contains(Role.PARTNER_APPLICANT)) {
			throw new IllegalArgumentException("Staff cannot have the applicant role");
		}
		if (newDisplayName != null && !newDisplayName.isBlank()) {
			this.displayName = newDisplayName;
		}
		if (newMobileHash != null) {
			this.mobileHash = newMobileHash;
		}
		if (!newRoles.isEmpty()) {
			this.roles.clear();
			this.roles.addAll(newRoles);
		}
		this.active = isActive;
	}

	boolean isActive() {
		return active;
	}

	Set<Role> getRoles() {
		return Set.copyOf(roles);
	}

	StaffView view() {
		return new StaffView(getId(), emailMasked, displayName, getRoles(), active);
	}
}
