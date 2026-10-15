package br.com.clicklance.model;

/** Somente dados administrativos publicos ao escopo autorizado. */
public record Company(String empresa_id, String nome, boolean active, int retencao_dias) {}
