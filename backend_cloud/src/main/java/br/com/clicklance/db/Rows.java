package br.com.clicklance.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

public final class Rows {
    private Rows() { }
    public static Instant instant(ResultSet r,String col) throws SQLException { return r.getObject(col,OffsetDateTime.class).toInstant(); }
    public record Empresa(UUID id,String nome,int dias_retencao_padrao,boolean ativo,Instant criado_em) { }
    public record Quadra(UUID id,UUID empresa_id,String nome,String fuso_horario,int duracao_segmento_segundos,int segmentos_concatenar,boolean ativo,Instant criado_em) { }
    public record Device(String id,UUID quadra_id,String tokenHash,String descricao,boolean ativo,Instant criado_em) { }
    public record Lance(UUID id,UUID quadra_id,String dispositivo_id,UUID evento_id,Instant timestamp_evento,String s3_key,long tamanho_bytes,String status,Instant expira_em,Instant criado_em) { }
    public record User(UUID id,String nome,String email,String senhaHash,boolean ativo,Instant criado_em) { }
    public static final RowMapper<Empresa> EMPRESA=(r,n)->new Empresa(r.getObject("id",UUID.class),r.getString("nome"),r.getInt("dias_retencao_padrao"),r.getBoolean("ativo"),instant(r,"criado_em"));
    public static final RowMapper<Quadra> QUADRA=(r,n)->new Quadra(r.getObject("id",UUID.class),r.getObject("empresa_id",UUID.class),r.getString("nome"),r.getString("fuso_horario"),r.getInt("duracao_segmento_segundos"),r.getInt("segmentos_concatenar"),r.getBoolean("ativo"),instant(r,"criado_em"));
    public static final RowMapper<Device> DEVICE=(r,n)->new Device(r.getString("id"),r.getObject("quadra_id",UUID.class),r.getString("token_acesso_hash"),r.getString("descricao"),r.getBoolean("ativo"),instant(r,"criado_em"));
    public static final RowMapper<Lance> LANCE=(r,n)->new Lance(r.getObject("id",UUID.class),r.getObject("quadra_id",UUID.class),r.getString("dispositivo_id"),r.getObject("evento_id",UUID.class),instant(r,"timestamp_evento"),r.getString("s3_key"),r.getLong("tamanho_bytes"),r.getString("status"),instant(r,"expira_em"),instant(r,"criado_em"));
    public static final RowMapper<User> USER=(r,n)->new User(r.getObject("id",UUID.class),r.getString("nome"),r.getString("email"),r.getString("senha_hash"),r.getBoolean("ativo"),instant(r,"criado_em"));
    public static OffsetDateTime sql(Instant value) { return value.atOffset(java.time.ZoneOffset.UTC); }
}
