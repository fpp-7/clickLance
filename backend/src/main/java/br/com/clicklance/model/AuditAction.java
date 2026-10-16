package br.com.clicklance.model;

public enum AuditAction {
    CONFIG_PUBLISH("CONFIGURATION", 201),
    COMPANY_CREATE("COMPANY", 201), COMPANY_LIST("COMPANY", 200), COMPANY_READ("COMPANY", 200),
    COURT_CREATE("COURT", 201), COURT_LIST("COURT", 200), COURT_READ("COURT", 200),
    DEVICE_CREATE("DEVICE", 201), DEVICE_LIST("DEVICE", 200), DEVICE_READ("DEVICE", 200),
    ADMIN_LOGIN("ADMIN_SESSION", 201), ADMIN_LOGOUT("ADMIN_SESSION", 204), ADMIN_SESSION_READ("ADMIN_SESSION", 200),
    ADMIN_BOOTSTRAP("ADMIN_ACCOUNT", null), COMPANY_ADMIN_PROVISION("ADMIN_ACCOUNT", null),
    AUDIT_LIST("AUDIT", 200), AUDIT_READ("AUDIT", 200), ADMIN_REQUEST("ADMINISTRATION", null);

    private final String resourceType;
    private final Integer successStatus;
    AuditAction(String resourceType, Integer successStatus) { this.resourceType = resourceType; this.successStatus = successStatus; }
    public String resourceType() { return resourceType; }
    public Integer successStatus() { return successStatus; }
}
