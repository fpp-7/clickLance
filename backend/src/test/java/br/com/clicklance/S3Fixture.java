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
    volatile boolean lifecycleOperations;
    volatile String versioningStatus;
    volatile int deleteFailure;
    final AtomicInteger deletes=new AtomicInteger(),lists=new AtomicInteger();
    S3Fixture() throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            try {
                String method=exchange.getRequestMethod();
                String query=exchange.getRequestURI().getRawQuery();
                if(lifecycleOperations && method.equals("GET") && query!=null && query.contains("versioning")) {
                    if(failureStatus!=0) { exchange.sendResponseHeaders(failureStatus,-1); return; }
                    String xml="<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"+(versioningStatus==null?"":"<Status>"+versioningStatus+"</Status>")+"</VersioningConfiguration>";
                    byte[] bytes=xml.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); return;
                }
                if(lifecycleOperations && method.equals("GET") && query!=null && query.contains("list-type=2")) {
                    lists.incrementAndGet();
                    if(failureStatus!=0) { exchange.sendResponseHeaders(failureStatus,-1); return; }
                    String prefix="";
                    for(String parameter:query.split("&")) if(parameter.startsWith("prefix=")) prefix=java.net.URLDecoder.decode(parameter.substring(7),StandardCharsets.UTF_8);
                    String bucket=exchange.getRequestURI().getPath().replace("/","");
                    StringBuilder xml=new StringBuilder("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><IsTruncated>false</IsTruncated>");
                    for(var entry:objects.entrySet()) if(entry.getKey().startsWith("/"+bucket+"/"+prefix)) {
                        String key=entry.getKey().substring(bucket.length()+2);
                        xml.append("<Contents><Key>").append(key).append("</Key><Size>").append(entry.getValue().bytes().length).append("</Size></Contents>");
                    }
                    byte[] bytes=xml.append("</ListBucketResult>").toString().getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); return;
                }
                if(lifecycleOperations && method.equals("DELETE")) {
                    deletes.incrementAndGet();
                    if(deleteFailure!=0) { exchange.sendResponseHeaders(deleteFailure,-1); return; }
                    var existing=objects.get(exchange.getRequestURI().getPath());
                    if(existing!=null && !existing.etag().equals(exchange.getRequestHeaders().getFirst("If-Match"))) { exchange.sendResponseHeaders(412,-1); return; }
                    objects.remove(exchange.getRequestURI().getPath()); exchange.sendResponseHeaders(204,-1); return;
                }
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
                    query=exchange.getRequestURI().getRawQuery();
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
