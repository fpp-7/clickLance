package br.com.clicklance.service;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Limites de uma unica instancia; chaves efemeras hash, sem IP/token nos logs ou metricas. */
@Service
public class RequestLimitService {
    private record Window(long minute, int count) {}
    private final Map<String,Window> windows=new HashMap<>();
    private final Map<String,LongAdder> counts=new java.util.concurrent.ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean enabled;
    public RequestLimitService(Clock clock,@Value("${clicklance.limits.enabled:true}") boolean enabled) {
        this.clock=clock; this.enabled=enabled;
    }
    public String category(String path, String method) {
        if (method.equals("POST") && path.endsWith("/sessoes")) return "login";
        if (path.endsWith("/recuperacao") || path.endsWith("/redefinicao")) return "recovery";
        if (path.equals("/api/v1/jogadores") && method.equals("POST")) return "signup";
        if (path.matches("/api/v1/dispositivos/[^/]+/saude")) return "heartbeat";
        if (path.equals("/api/v1/replays")) return "ingest";
        if (path.startsWith("/api/v1/jogadores/") && path.endsWith("/acesso")) return "media";
        if (path.startsWith("/api/v1/jogadores/")) return "query";
        return "admin";
    }
    public synchronized boolean allow(String category, String remote, String credential) {
        if (!enabled || category.equals("admin")) return true;
        long minute=clock.instant().getEpochSecond()/60;
        windows.entrySet().removeIf(e -> e.getValue().minute()!=minute);
        int limit=switch(category) { case "login" -> 10; case "recovery" -> 3; case "signup" -> 5;
            case "heartbeat" -> 2; case "media" -> 30; default -> 120; };
        // Rede sempre limitada; nao confiar X-Forwarded-For. Instalações usam limite maior por rede.
        int networkLimit=Set.of("heartbeat","ingest").contains(category) ? 1200 : limit;
        String network=category+":network:"+hash(remote);
        String identity=category+":identity:"+hash(credential==null?remote:credential);
        if (!available(network,minute,networkLimit) || !available(identity,minute,limit)) return false;
        increment(network,minute); increment(identity,minute); return true;
    }
    private boolean available(String key,long minute,int limit) {
        var w=windows.get(key);
        return w==null ? windows.size()<10000 : w.minute()!=minute || w.count()<limit;
    }
    private void increment(String key,long minute) {
        var w=windows.get(key); windows.put(key,new Window(minute,w==null || w.minute()!=minute?1:w.count()+1));
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public void record(String category,int status,long elapsedNanos) {
        counts.computeIfAbsent(category+".requests",k->new LongAdder()).increment();
        counts.computeIfAbsent(category+".status_"+status,k->new LongAdder()).increment();
        counts.computeIfAbsent(category+".duration_ms",k->new LongAdder()).add(elapsedNanos/1000000);
    }
    public Map<String,Long> metrics() {
        Map<String,Long> result=new TreeMap<>(); counts.forEach((k,v)->result.put(k,v.sum())); return result;
    }
}
