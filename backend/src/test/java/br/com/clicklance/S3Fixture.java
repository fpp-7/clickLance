package br.com.clicklance;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** HTTP S3 substituido: exercita o SDK real, assinaturas/HEAD/GET, sem acessar AWS. */
final class S3Fixture implements AutoCloseable {
    record ObjectData(byte[] bytes, String metadata, String version, String etag) {}
    final Map<String,ObjectData> objects=new ConcurrentHashMap<>();
    final AtomicInteger heads=new AtomicInteger(), gets=new AtomicInteger(), mutations=new AtomicInteger();
    final HttpServer server;
    volatile int failureStatus;
    volatile boolean changeOnGet;
    S3Fixture() throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            try {
                String method=exchange.getRequestMethod();
                boolean head=method.equals("HEAD");
                if (head) heads.incrementAndGet(); else if (method.equals("GET")) gets.incrementAndGet();
                else { mutations.incrementAndGet(); exchange.sendResponseHeaders(405,-1); return; }
                var object=objects.get(exchange.getRequestURI().getPath());
                int status=failureStatus!=0?failureStatus:object==null?404:changeOnGet&&!head?412:200;
                if (status!=200) { exchange.sendResponseHeaders(status,-1); return; }
                exchange.getResponseHeaders().set("Content-Length",Integer.toString(object.bytes().length));
                exchange.getResponseHeaders().set("ETag",object.etag());
                exchange.getResponseHeaders().set("x-amz-meta-sha256",object.metadata());
                if (object.version()!=null) exchange.getResponseHeaders().set("x-amz-version-id",object.version());
                if (head) exchange.sendResponseHeaders(200,-1);
                else {
                    boolean pinned=object.version()!=null && !object.version().equals("null");
                    String query=exchange.getRequestURI().getRawQuery();
                    if ((pinned && (query==null || !query.contains("versionId="+object.version())))
                            || (!pinned && !object.etag().equals(exchange.getRequestHeaders().getFirst("If-Match")))) {
                        exchange.sendResponseHeaders(412,-1); return;
                    }
                    exchange.sendResponseHeaders(200,object.bytes().length);
                    exchange.getResponseBody().write(object.bytes());
                }
            } finally { exchange.close(); }
        });
        server.start();
    }
    String put(String bucket,String key,byte[] bytes) {
        String hash=hash(bytes);
        objects.put("/"+bucket+"/"+key,new ObjectData(bytes,hash,null,"\"fixture-etag\""));
        return hash;
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    S3Client client() {
        return S3Client.builder().region(Region.US_EAST_1).endpointOverride(URI.create("http://127.0.0.1:"+server.getAddress().getPort()))
                .forcePathStyle(true).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local-fixture","local-fixture-only")))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(java.time.Duration.ofSeconds(2)).socketTimeout(java.time.Duration.ofSeconds(2)))
                .overrideConfiguration(c -> c.apiCallTimeout(java.time.Duration.ofSeconds(5)).retryPolicy(r -> r.numRetries(0))).build();
    }
    @Override public void close() { server.stop(0); }
}
