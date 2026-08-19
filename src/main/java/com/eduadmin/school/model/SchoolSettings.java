package com.eduadmin.school.model;

import jakarta.persistence.*;

/**
 * Single-row table that holds school branding used on generated report cards:
 * school name, address line and logo image. Kept in the DB (LONGBLOB) so the
 * logo follows the app across servers, matching how note files are stored.
 */
@Entity
@Table(name = "school_settings")
public class SchoolSettings {

    /** Singleton row; we use id = 1 for the one settings record. */
    public static final Long ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private String schoolName;

    private String address;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "logo_bytes", nullable = true, columnDefinition = "LONGBLOB")
    private byte[] logoBytes;

    @Column(name = "logo_content_type")
    private String logoContentType;

    /** Principal's signature shown on generated report cards (stored as PNG
     *  in the DB, like the school logo). */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "principal_signature_bytes", nullable = true, columnDefinition = "LONGBLOB")
    private byte[] principalSignatureBytes;

    @Column(name = "principal_signature_content_type")
    private String principalSignatureContentType;

    public SchoolSettings() {}

    public static SchoolSettings defaults() {
        SchoolSettings s = new SchoolSettings();
        s.setId(ID);
        s.setSchoolName("EduAdmin School");
        s.setAddress("");
        return s;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public byte[] getLogoBytes() { return logoBytes; }
    public void setLogoBytes(byte[] logoBytes) { this.logoBytes = logoBytes; }

    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String logoContentType) { this.logoContentType = logoContentType; }

    public byte[] getPrincipalSignatureBytes() { return principalSignatureBytes; }
    public void setPrincipalSignatureBytes(byte[] principalSignatureBytes) { this.principalSignatureBytes = principalSignatureBytes; }

    public String getPrincipalSignatureContentType() { return principalSignatureContentType; }
    public void setPrincipalSignatureContentType(String principalSignatureContentType) { this.principalSignatureContentType = principalSignatureContentType; }
}