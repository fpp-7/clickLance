package br.com.clicklance.model;

public record Court(String quadra_id, String empresa_id, String nome, boolean active,
                    String timezone, CaptureProfile perfil_captura) {}
