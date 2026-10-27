package br.com.clicklance.library;

import br.com.clicklance.admin.AdminService;
import br.com.clicklance.api.ApiException;
import br.com.clicklance.catalog.CatalogService;
import br.com.clicklance.db.Rows;
import br.com.clicklance.db.Rows.Lance;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryService {
    public record Saved(Map<String,Object> link,boolean created) { }
    private final JdbcTemplate db; private final Clock clock; private final AdminService admin; private final CatalogService catalog;
    public LibraryService(JdbcTemplate db,Clock clock,AdminService admin,CatalogService catalog) { this.db=db; this.clock=clock; this.admin=admin; this.catalog=catalog; }
    @Transactional public Saved save(UUID user,UUID replay) {
        Lance lance=db.query("SELECT * FROM lances WHERE id=? FOR UPDATE",Rows.LANCE,replay).stream().findFirst().orElseThrow(ApiException::notFound);
        var existing=db.query("SELECT id,salvo_em FROM usuario_lances WHERE usuario_id=? AND lance_id=?",(r,n)->Map.<String,Object>of("id",r.getObject("id",UUID.class),"lance_id",replay,"salvo_em",r.getObject("salvo_em",OffsetDateTime.class).toInstant()),user,replay);
        if(!existing.isEmpty()) return new Saved(existing.getFirst(),false);
        admin.activeQuadra(lance.quadra_id());
        if(!"DISPONIVEL".equals(lance.status()) || !lance.expira_em().isAfter(clock.instant())) throw ApiException.invalid("Este lance já expirou e não pode mais ser salvo.");
        UUID id=UUID.randomUUID(); var now=clock.instant();
        db.update("INSERT INTO usuario_lances(id,usuario_id,lance_id,salvo_em) VALUES(?,?,?,?)",id,user,replay,Rows.sql(now));
        return new Saved(Map.of("id",id,"lance_id",replay,"salvo_em",now),true);
    }
    public Map<String,Object> list(UUID user,int page,int size) {
        long total=db.queryForObject("SELECT COUNT(*) FROM usuario_lances WHERE usuario_id=?",Long.class,user);
        var links=db.query("SELECT id,lance_id,salvo_em FROM usuario_lances WHERE usuario_id=? ORDER BY salvo_em DESC,id LIMIT ? OFFSET ?",
            (r,n)->new Object[]{r.getObject("id",UUID.class),r.getObject("lance_id",UUID.class),r.getObject("salvo_em",OffsetDateTime.class).toInstant()},user,size,(long)page*size);
        var result=new ArrayList<Map<String,Object>>();
        for(Object[] link:links) {
            var view=new LinkedHashMap<>(catalog.detail((UUID)link[1],user));
            view.put("vinculo_id",link[0]); view.put("lance_id",link[1]); view.put("salvo_em",link[2]); result.add(view);
        }
        return Map.of("pagina",page,"tamanho",size,"total_lances",total,"lances",result);
    }
}
