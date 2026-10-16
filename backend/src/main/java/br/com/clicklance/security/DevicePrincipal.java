package br.com.clicklance.security;

/** Origem resolvida pelo banco apos validar credencial e estado dos tres niveis. */
public record DevicePrincipal(String deviceId, String courtId, String companyId, String bucket) {}
