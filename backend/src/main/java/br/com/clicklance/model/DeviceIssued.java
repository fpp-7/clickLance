package br.com.clicklance.model;

/** Unica resposta que entrega o segredo, depois do commit do provisionamento. */
public record DeviceIssued(Device dispositivo, String device_token) {
    @Override public String toString() { return "DeviceIssued[dispositivo=" + dispositivo + ", device_token=<redacted>]"; }
}
