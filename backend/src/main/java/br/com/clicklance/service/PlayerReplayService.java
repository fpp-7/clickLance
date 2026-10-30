package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.*;
import br.com.clicklance.repository.PlayerReplayRepository;
import br.com.clicklance.security.PlayerPrincipal;
import br.com.clicklance.validation.Input;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerReplayService {
    private final PlayerReplayRepository replays;
    private final MediaService media;
    private final PlayerSessionService sessions;
    private final Clock clock;
    public PlayerReplayService(PlayerReplayRepository replays,MediaService media,PlayerSessionService sessions,Clock clock) {
        this.replays=replays; this.media=media; this.sessions=sessions; this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> arenas(PlayerPrincipal player,int page,int size) {
        authenticated(player); pagination(page,size);
        return page(replays.arenas(size,(long)page*size),page,size,replays.arenaCount());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> courts(PlayerPrincipal player,String company,int page,int size) {
        authenticated(player); pagination(page,size);
        if (!replays.activeCompany(company)) throw absent();
        return page(replays.courts(company,size,(long)page*size),page,size,replays.courtCount(company));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> availability(PlayerPrincipal player,String court,String value,int page,int size) {
        authenticated(player); pagination(page,size);
        ZoneId zone=zone(court); Instant now=clock.instant(); LocalDate today=now.atZone(zone).toLocalDate();
        LocalDate date=value==null?today:date(value);
        var slots=new ArrayList<Map<String,Object>>();
        for (int h=0;h<24;h++) {
            Instant start=hour(date,h,zone),end=hour(date,h+1,zone);
            long total=replays.courtReplayCount(court,start,end,now);
            if (total>0) slots.add(Map.of("hora_inicio",h,"hora_fim",h+1,"total",total));
        }
        return Map.of("quadra_id",court,"timezone",zone.getId(),"hoje",today.toString(),"data",date.toString(),
                "datas",replays.dates(court,zone.getId(),now,size,(long)page*size),"slots",slots,"page",page,"size",size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(PlayerPrincipal player,String court,String value,Integer start,Integer end,int page,int size) {
        authenticated(player); pagination(page,size); ZoneId zone=zone(court);
        LocalDate date=value==null?clock.instant().atZone(zone).toLocalDate():date(value);
        if ((start==null)!=(end==null)) Input.invalid("hora_inicio","INVALID_RANGE");
        if (start!=null) { Input.integer(start,"hora_inicio",0,23); Input.integer(end,"hora_fim",1,24); if(start>=end) Input.invalid("hora_fim","INVALID_RANGE"); }
        Instant from=hour(date,start==null?0:start,zone),to=hour(date,end==null?24:end,zone),now=clock.instant();
        return page(replays.byCourt(player.userId(),court,from,to,now,size,(long)page*size).stream().map(PlayerReplay::summary).toList(),page,size,replays.courtReplayCount(court,from,to,now));
    }
    @Transactional(readOnly=true)
    public ReplaySummary detail(PlayerPrincipal player,String event) {
        authenticated(player);
        return requireAccess(replays.find(player.userId(),event,false).orElseThrow(PlayerReplayService::absent),clock.instant()).summary();
    }
    @Transactional
    public MediaAccess access(PlayerPrincipal player,String event,Map<String,Object> body) {
        authenticated(player); Input.fields(body,Set.of("download")); boolean download=Input.bool(body,"download");
        sessions.requireCurrent(player);
        var replay=replays.find(player.userId(),event,true).orElseThrow(PlayerReplayService::absent);
        Instant now=clock.instant(); requireAccess(replay,now);
        return media.issue(replay,download,now);
    }
    public static PlayerReplay requireAccess(PlayerReplay replay,Instant now) {
        if (replay.blocked() || replay.purgeReserved() || replay.purged()
                || (!replay.saved() && (!replay.activeOrigin() || !replay.expiresAt().isAfter(now)))) throw absent();
        return replay;
    }
    static void authenticated(PlayerPrincipal player) { if(player==null) throw new ApiException(401,"UNAUTHENTICATED"); }
    static void pagination(int page,int size) { Input.integer(page,"page",0,Integer.MAX_VALUE); Input.integer(size,"size",1,100); }
    static Map<String,Object> page(Object items,int page,int size,long total) { return Map.of("items",items,"page",page,"size",size,"total",total); }
    private ZoneId zone(String court) { return ZoneId.of(replays.courtZone(court).orElseThrow(PlayerReplayService::absent)); }
    private static LocalDate date(String value) {
        try { if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("invalid",value,0); return LocalDate.parse(value); }
        catch(DateTimeParseException ignored) { Input.invalid("data","INVALID_FORMAT"); return null; }
    }
    private static Instant hour(LocalDate date,int hour,ZoneId zone) { return date.atStartOfDay().plusHours(hour).atZone(zone).toInstant(); }
    static ApiException absent() { return new ApiException(404,"RESOURCE_NOT_FOUND"); }
}
