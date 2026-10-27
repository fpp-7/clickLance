package br.com.clicklance.catalog;

import br.com.clicklance.admin.AdminService;
import br.com.clicklance.api.ApiException;
import br.com.clicklance.db.Rows;
import br.com.clicklance.db.Rows.*;
import br.com.clicklance.replay.ReplayService;
import br.com.clicklance.storage.VideoStorage;
import br.com.clicklance.storage.StorageException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final JdbcTemplate db; private final AdminService admin; private final ReplayService replays;
    private final VideoStorage storage; private final Clock clock;
    public CatalogService(JdbcTemplate db,AdminService admin,ReplayService replays,VideoStorage storage,Clock clock) {
        this.db=db; this.admin=admin; this.replays=replays; this.storage=storage; this.clock=clock;
    }
    public List<Map<String,Object>> quadras(UUID company) {
        if(!admin.empresa(company).ativo()) throw ApiException.notFound();
        return db.query("SELECT * FROM quadras WHERE empresa_id=? AND ativo=TRUE ORDER BY nome,id",Rows.QUADRA,company).stream().map(q->{
            ZoneId zone=ZoneId.of(q.fuso_horario()); LocalDate day=LocalDate.now(clock.withZone(zone));
            long count=db.queryForObject("SELECT COUNT(*) FROM lances WHERE quadra_id=? AND status='DISPONIVEL' AND expira_em>? AND timestamp_evento>=? AND timestamp_evento<?",Long.class,
                q.id(),Rows.sql(clock.instant()),Rows.sql(day.atStartOfDay(zone).toInstant()),Rows.sql(day.plusDays(1).atStartOfDay(zone).toInstant()));
            return Map.<String,Object>of("id",q.id(),"nome",q.nome(),"ativo",true,"fuso_horario",q.fuso_horario(),"lances_disponiveis_hoje",count);
        }).toList();
    }
    public Map<String,Object> list(UUID quadraId,LocalDate day,LocalTime start,LocalTime end,int page,int size) {
        if(day.getYear()<1900 || day.getYear()>2100) throw ApiException.invalid("Data deve estar entre 1900 e 2100.");
        Quadra q=admin.activeQuadra(quadraId); ZoneId zone=ZoneId.of(q.fuso_horario());
        if((start==null)!=(end==null)) throw ApiException.invalid("Informe hora_inicio e hora_fim juntas.");
        ZonedDateTime from,to;
        if(start==null) { from=day.atStartOfDay(zone); to=day.plusDays(1).atStartOfDay(zone); }
        else {
            if(start.equals(end)) throw ApiException.invalid("Faixa horária deve ter início e fim diferentes.");
            from=day.atTime(start).atZone(zone); to=(end.isBefore(start)?day.plusDays(1):day).atTime(end).atZone(zone);
        }
        Object[] args={quadraId,Rows.sql(clock.instant()),Rows.sql(from.toInstant()),Rows.sql(to.toInstant())};
        String where=" FROM lances WHERE quadra_id=? AND status='DISPONIVEL' AND expira_em>? AND timestamp_evento>=? AND timestamp_evento<?";
        long count=db.queryForObject("SELECT COUNT(*)"+where,Long.class,args);
        List<Object> parameters=new ArrayList<>(Arrays.asList(args)); parameters.add(size); parameters.add((long)page*size);
        var clips=db.query("SELECT *"+where+" ORDER BY timestamp_evento,id LIMIT ? OFFSET ?",Rows.LANCE,parameters.toArray());
        var result=new LinkedHashMap<String,Object>();
        result.put("quadra_id",q.id()); result.put("quadra_nome",q.nome()); result.put("fuso_horario",q.fuso_horario());
        result.put("data",day); result.put("faixa_horaria",start==null?"dia completo":start+" - "+end);
        result.put("pagina",page); result.put("tamanho",size); result.put("total_lances",count);
        var views=new ArrayList<Map<String,Object>>();
        for(Lance l:clips) {
            try { views.add(view(l,q,false)); }
            catch(StorageException ex) { if(ex.reason()!=StorageException.Reason.EXPIRED) throw ex; }
        }
        result.put("lances",views); return result;
    }
    public Map<String,Object> detail(UUID id,UUID viewer) {
        Lance l=replays.lance(id); Quadra q=admin.quadra(l.quadra_id());
        boolean saved=viewer!=null && db.queryForObject("SELECT COUNT(*) FROM usuario_lances WHERE usuario_id=? AND lance_id=?",Integer.class,viewer,id)>0;
        boolean publiclyVisible="DISPONIVEL".equals(l.status()) && l.expira_em().isAfter(clock.instant()) && q.ativo() && admin.empresa(q.empresa_id()).ativo();
        if(!publiclyVisible && !(saved && Set.of("DISPONIVEL","EXPIRADO_SALVO").contains(l.status()))) throw ApiException.notFound();
        return view(l,q,!publiclyVisible);
    }
    public Map<String,Object> view(Lance l,Quadra q,boolean savedAccess) {
        var result=new LinkedHashMap<String,Object>();
        result.put("id",l.id()); result.put("quadra_id",q.id()); result.put("quadra_nome",q.nome());
        result.put("timestamp_evento",l.timestamp_evento());
        result.put("horario_formatado",l.timestamp_evento().atZone(ZoneId.of(q.fuso_horario())).format(DateTimeFormatter.ofPattern("HH:mm")));
        result.put("fuso_horario",q.fuso_horario()); result.put("status",l.status()); result.put("expira_em",l.expira_em());
        result.put("video_url",storage.playbackUrl(l.s3_key(),savedAccess?clock.instant().plusSeconds(3600):l.expira_em()));
        return result;
    }
}
