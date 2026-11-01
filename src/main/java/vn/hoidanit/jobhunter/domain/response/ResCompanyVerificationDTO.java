package vn.hoidanit.jobhunter.domain.response;

import java.util.List;

/** What an admin (or the company's own employers) sees when a company's legitimacy is checked. */
public record ResCompanyVerificationDTO(String taxCode, String phone, String website, boolean hasLicense,
        List<Contact> contacts, List<Check> checks, int score) {

    /** One employer account of the company; `domainMatch` is null when there is no website to compare with. */
    public record Contact(String name, String email, boolean emailVerified, boolean freeMail, Boolean domainMatch) {
    }

    /** `required` checks must pass before an admin can approve; the others are advice. */
    public record Check(String key, String label, boolean ok, boolean required, String note) {
    }
}
