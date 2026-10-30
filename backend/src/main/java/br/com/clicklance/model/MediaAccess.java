package br.com.clicklance.model;

import java.time.Instant;

public record MediaAccess(String url, Instant expira_em) {
    @Override public String toString() { return "MediaAccess[expira_em=" + expira_em + "]"; }
}
