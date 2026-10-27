package br.com.clicklance.admin;

import br.com.clicklance.api.ApiException;
import br.com.clicklance.db.Rows;
import br.com.clicklance.db.Rows.*;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final JdbcTemplate db; private final Clock clock; private final PasswordEncoder encoder;
    public AdminService(JdbcTemplate db,Clock clock,PasswordEncoder encoder) { this.db=db; this.clock=clock; this.encoder=encoder; }
    @Transactional public Empresa createEmpresa(String nome,Integer days) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO empresas(id,nome,dias_retencao_padrao,criado_em) VALUES(?,?,?,?)",id,nome.strip(),days==null?2:days,Rows.sql(clock.instant()));
        return empresa(id);
    }
    @Transactional public Quadra createQuadra(UUID empresa,String nome,Integer duration,Integer count,String zone) {
        if(!empresa(empresa).ativo()) throw ApiException.invalid("Empresa inativa.");
        String timezone=zone==null?"America/Sao_Paulo":zone;
        validateZone(timezone);
        int seconds=duration==null?60:duration,segments=count==null?2:count;
        validateWindow(seconds,segments);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO quadras(id,empresa_id,nome,duracao_segmento_segundos,segmentos_concatenar,fuso_horario,criado_em) VALUES(?,?,?,?,?,?,?)",id,empresa,nome.strip(),seconds,segments,timezone,Rows.sql(clock.instant()));
        return quadra(id);
    }
    @Transactional public Map<String,Object> createDevice(String id,UUID quadra,String description) {
        Quadra q=activeQuadra(quadra);
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("INSERT INTO dispositivos(id,quadra_id,token_acesso_hash,descricao,criado_em) VALUES(?,?,?,?,?)",id,q.id(),encoder.encode(token),description,Rows.sql(clock.instant()));
        return Map.of("id",id,"quadra_id",quadra,"descricao",description==null?"":description,"token_acesso",token,"ativo",true,"criado_em",clock.instant());
    }
    public Empresa empresa(UUID id) { return db.query("SELECT * FROM empresas WHERE id=?",Rows.EMPRESA,id).stream().findFirst().orElseThrow(ApiException::notFound); }
    public Quadra quadra(UUID id) { return db.query("SELECT * FROM quadras WHERE id=?",Rows.QUADRA,id).stream().findFirst().orElseThrow(ApiException::notFound); }
    public Quadra activeQuadra(UUID id) {
        Quadra q=quadra(id);
        if(!q.ativo() || !empresa(q.empresa_id()).ativo()) throw ApiException.notFound();
        return q;
    }
    public Device authenticateDevice(String id,String token) {
        Device d=db.query("SELECT * FROM dispositivos WHERE id=?",Rows.DEVICE,id).stream().findFirst().orElseThrow(ApiException::unauthorized);
        if(token==null || token.length()>256 || !encoder.matches(token,d.tokenHash()) || !d.ativo()) throw ApiException.unauthorized();
        try { activeQuadra(d.quadra_id()); } catch(ApiException ex) { throw ApiException.unauthorized(); }
        return d;
    }
    public Map<String,Object> deviceConfig(String id,String token) {
        Device d=authenticateDevice(id,token); Quadra q=activeQuadra(d.quadra_id());
        return Map.of("dispositivo_id",d.id(),"quadra_id",q.id(),"quadra_nome",q.nome(),"duracao_segmento_segundos",q.duracao_segmento_segundos(),"segmentos_concatenar",q.segmentos_concatenar(),"fuso_horario",q.fuso_horario());
    }
    @Transactional public Quadra configureQuadra(UUID id,Integer duration,Integer count,Boolean active) {
        Quadra q=quadra(id); int seconds=duration==null?q.duracao_segmento_segundos():duration, segments=count==null?q.segmentos_concatenar():count;
        validateWindow(seconds,segments);
        db.update("UPDATE quadras SET duracao_segmento_segundos=?,segmentos_concatenar=?,ativo=? WHERE id=?",seconds,segments,active==null?q.ativo():active,id);
        return quadra(id);
    }
    @Transactional public Map<String,Object> setDeviceActive(String id,boolean active) {
        if(db.update("UPDATE dispositivos SET ativo=? WHERE id=?",active,id)!=1) throw ApiException.notFound();
        return Map.of("id",id,"ativo",active);
    }
    @Transactional public Empresa setEmpresaActive(UUID id,boolean active) {
        if(db.update("UPDATE empresas SET ativo=? WHERE id=?",active,id)!=1) throw ApiException.notFound(); return empresa(id);
    }
    private static void validateZone(String zone) { try { ZoneId.of(zone); } catch(Exception ex) { throw ApiException.invalid("Fuso horário inválido."); } }
    private static void validateWindow(int seconds,int count) { if(seconds*count>600) throw ApiException.invalid("A janela de captura não pode exceder 600 segundos."); }
}
