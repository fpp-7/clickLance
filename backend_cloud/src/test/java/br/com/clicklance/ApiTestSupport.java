package br.com.clicklance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestSupport {
    protected static final String ADMIN="admin-apenas-para-testes-0123456789012345";
    @LocalServerPort int port;
    protected final HttpClient http=HttpClient.newHttpClient();
    protected final JsonMapper json=JsonMapper.builder().build();
    protected record Reply(int status,JsonNode body) { }
    protected Reply call(String method,String path,Object body,Map<String,String> headers) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        headers.forEach(request::header);
        request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(),json.readTree(response.body()));
    }
    protected Reply admin(String method,String path,Object body) throws Exception { return call(method,path,body,Map.of("X-Admin-Token",ADMIN)); }
    protected JsonNode setupDevice() throws Exception {
        var empresa=admin("POST","/api/v1/empresas",Map.of("nome","Arena teste"));
        var quadra=admin("POST","/api/v1/quadras",Map.of("empresa_id",empresa.body().path("id").asText(),"nome","Quadra teste"));
        String device="disp_"+java.util.UUID.randomUUID().toString().substring(0,8);
        var result=admin("POST","/api/v1/dispositivos",Map.of("id",device,"quadra_id",quadra.body().path("id").asText()));
        org.assertj.core.api.Assertions.assertThat(result.status()).isEqualTo(201);
        return result.body();
    }
}
