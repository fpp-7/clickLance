package br.com.clicklance.model;

/** Registro interno para autenticacao; nunca retornado por controllers. */
public record DeviceCredentials(Device device, String tokenHash, boolean courtActive, boolean companyActive) {
    @Override public String toString() { return "DeviceCredentials[device=" + device + "]"; }
}
