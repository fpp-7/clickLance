package br.com.clicklance;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.*;
import br.com.clicklance.repository.ReplayRepository;
import br.com.clicklance.security.PlayerPrincipal;
import br.com.clicklance.service.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AdminTestSupport.TimeConfiguration.class,PlayerLibraryIT.Signing.class})
class PlayerLibraryIT extends AdminTestSupport {
    @TestConfiguration(proxyBeanMethods=false)
    static class Signing {
        @Bean @Primary S3Presigner testPresigner() {
            return S3Presigner.builder().region(Region.US_EAST_1).endpointOverride(URI.create("http://127.0.0.1:9"))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local-signing-fixture","local-fixture-only"))).build();
        }
    }
    record Origin(String company,String court,String device) {}
    record Player(UUID id,String token,PlayerPrincipal principal) {}
    final List<Origin> origins=new ArrayList<>();
    final List<String> events=new ArrayList<>();
    @Autowired ReplayRepository replayRepository;
    @Autowired PlayerSessionService playerSessions;
    @Autowired LibraryService library;
    @Autowired PlayerAccountService playerAccounts;
    @Autowired PlayerReplayService playerReplays;
    @Autowired PlatformTransactionManager transactions;

    @AfterEach void cleanOwnMedia() {
        for(var event:events) {
            jdbc.update("DELETE FROM library_entry WHERE event_id=?",event);
            jdbc.update("DELETE FROM replay_lifecycle WHERE event_id=?",event);
            jdbc.update("DELETE FROM replay WHERE evento_id=?",event);
        }
        for(var origin:origins) {
            jdbc.update("DELETE FROM dispositivo WHERE id=?",origin.device());
            jdbc.update("DELETE FROM quadra WHERE id=?",origin.court());
        }
        for(var user:users) {
            jdbc.update("DELETE FROM player_password_reset WHERE user_id=?",user);
            jdbc.update("DELETE FROM player_session WHERE user_id=?",user);
        }
    }
    Player player() {
        UUID id=user(false); jdbc.update("UPDATE app_user SET player_account=true WHERE id=?",id);
        String token=(String)playerSessions.login(email(id),PASSWORD).get("access_token");
        return new Player(id,token,playerSessions.authenticate(token));
    }
    Origin origin(String zone) {
        var origin=new Origin(UUID.randomUUID().toString(),UUID.randomUUID().toString(),eventId());
        origins.add(origin); companies.add(origin.company());
        jdbc.update("INSERT INTO empresa(id,nome,active) VALUES (?,'Arena sintetica',true)",origin.company());
        jdbc.update("INSERT INTO quadra(id,empresa_id,nome,active,timezone,replay_before_seconds,replay_after_seconds,segment_duration_seconds,keyframe_interval) VALUES (?,?,'Quadra sintetica',true,?,120,0,60,30)",origin.court(),origin.company(),zone);
        jdbc.update("INSERT INTO dispositivo(id,quadra_id,active,bucket,token_hash) VALUES (?,?,true,'library-private-fixture',?)",origin.device(),origin.court(),S3Fixture.hash(origin.device().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return origin;
    }
    String replay(Origin origin,Instant at) {
        String id=eventId(); events.add(id);
        Instant expires=at.plusSeconds(2*86400);
        assertThat(replayRepository.insert(new Replay(new ReplayInput(id,origin.device(),origin.court(),at,"library-private-fixture","replays/"+origin.device()+"/"+id+".mp4","a".repeat(64)),origin.company(),CLOCK.instant(),2,expires,!expires.isAfter(CLOCK.instant()),new VerifiedObject("fixture-version","\"fixture-etag\"",10),null))).isTrue();
        return id;
    }
    static String eventId() { return UUID.randomUUID().toString().replace("-",""); }
    static void setTime(String instant) { CLOCK.advance(Duration.between(CLOCK.instant(),Instant.parse(instant)).getSeconds()); }
    static String eventPath(String event) { return "/api/v1/jogadores/lances/"+event; }
    static String libraryPath(String event) { return "/api/v1/jogadores/biblioteca/"+event; }
    @SuppressWarnings("unchecked") List<Map<String,Object>> items(java.net.http.HttpResponse<String> response) { return (List<Map<String,Object>>)body(response).get("items"); }

    @Test void onlyPlayerSessionDiscoversArenasAndPublicResponsesExcludeOperationalReferences() throws Exception {
        var o=origin("America/Sao_Paulo"); var p=player(); var empty=origin("UTC");
        assertThat(request("GET","/api/v1/jogadores/arenas",null,null).statusCode()).isEqualTo(401);
        assertThat(request("GET","/api/v1/jogadores/arenas",token(user(true)),null).statusCode()).isEqualTo(401);
        var arenas=request("GET","/api/v1/jogadores/arenas",p.token(),null);
        assertThat(arenas.statusCode()).isEqualTo(200);
        assertThat(items(arenas)).anyMatch(v->v.get("empresa_id").equals(o.company())).anyMatch(v->v.get("empresa_id").equals(empty.company()));
        var courts=request("GET","/api/v1/jogadores/arenas/"+o.company()+"/quadras",p.token(),null);
        assertThat(items(courts)).hasSize(1); assertThat(courts.body()).doesNotContain("bucket","token","perfil_captura","RTSP");
        jdbc.update("UPDATE empresa SET active=false WHERE id=?",o.company());
        assertThat(request("GET","/api/v1/jogadores/arenas/"+o.company()+"/quadras",p.token(),null).statusCode()).isEqualTo(404);
        assertThat(items(request("GET","/api/v1/jogadores/arenas",p.token(),null))).noneMatch(v->v.get("empresa_id").equals(o.company()));
    }

    @Test void localDaysHourlyHalfOpenBoundsAndStablePaginationAgreeWithAvailability() throws Exception {
        setTime("2026-10-31T05:00:00Z"); var o=origin("America/Sao_Paulo"); var p=player();
        replay(o,Instant.parse("2026-10-30T02:59:59Z"));
        String first=replay(o,Instant.parse("2026-10-30T03:00:00Z"));
        String second=replay(o,Instant.parse("2026-10-30T03:00:00Z"));
        replay(o,Instant.parse("2026-10-30T04:00:00Z"));
        String end=replay(o,Instant.parse("2026-10-31T02:59:59Z"));
        replay(o,Instant.parse("2026-10-31T03:00:00Z"));
        String base="/api/v1/jogadores/quadras/"+o.court();
        var listing=request("GET",base+"/lances?data=2026-10-30",p.token(),null);
        assertThat(((Number)body(listing).get("total")).intValue()).isEqualTo(4);
        var a=items(request("GET",base+"/lances?data=2026-10-30&hora_inicio=0&hora_fim=1&size=1&page=0",p.token(),null));
        var b=items(request("GET",base+"/lances?data=2026-10-30&hora_inicio=0&hora_fim=1&size=1&page=1",p.token(),null));
        assertThat(List.of(a.getFirst().get("evento_id"),b.getFirst().get("evento_id"))).containsExactlyInAnyOrder(first,second);
        var last=items(request("GET",base+"/lances?data=2026-10-30&hora_inicio=23&hora_fim=24",p.token(),null));
        assertThat(last).hasSize(1); assertThat(last.getFirst()).containsEntry("evento_id",end);
        var availability=request("GET",base+"/disponibilidade?data=2026-10-30",p.token(),null);
        assertThat(body(availability)).containsEntry("hoje","2026-10-31");
        @SuppressWarnings("unchecked") var slots=(List<Map<String,Object>>)body(availability).get("slots");
        assertThat(slots.stream().mapToLong(s->((Number)s.get("total")).longValue()).sum()).isEqualTo(4);
        for(String query:List.of("?hora_inicio=1","?hora_inicio=2&hora_fim=1","?hora_inicio=0&hora_fim=25","?data=2026-02-30","?size=101"))
            assertThat(request("GET",base+"/lances"+query,p.token(),null).statusCode()).as(query).isEqualTo(422);
        assertThat(listing.body()).doesNotContain("object_key","bucket","fixture-version","url");
    }

    @Test void dstFallBackHourCountsBothOffsetsExactlyOnce() throws Exception {
        setTime("2026-11-01T12:00:00Z"); var o=origin("America/New_York"); var p=player();
        String one=replay(o,Instant.parse("2026-11-01T05:30:00Z")),two=replay(o,Instant.parse("2026-11-01T06:30:00Z"));
        var response=request("GET","/api/v1/jogadores/quadras/"+o.court()+"/lances?data=2026-11-01&hora_inicio=1&hora_fim=2",p.token(),null);
        assertThat(items(response)).extracting(row->row.get("evento_id")).containsExactlyInAnyOrder(one,two);
    }

    @Test void publicExpirationIsExactAndSavedOwnerRetainsPrivateAccessWhileOtherPlayerDoesNot() throws Exception {
        var o=origin("UTC"); var a=player(); var b=player(); String id=replay(o,CLOCK.instant().minusSeconds(2*86400-60));
        assertThat(request("PUT",libraryPath(id),a.token(),null).statusCode()).isEqualTo(201);
        assertThat(request("GET",eventPath(id),b.token(),null).statusCode()).isEqualTo(200);
        CLOCK.advance(60);
        assertThat(request("GET",eventPath(id),b.token(),null).statusCode()).isEqualTo(404);
        assertThat(request("POST",eventPath(id)+"/acesso",b.token(),Map.of("download",false)).statusCode()).isEqualTo(404);
        assertThat(request("PUT",libraryPath(id),b.token(),null).statusCode()).isEqualTo(404);
        assertThat(request("GET",eventPath(id),a.token(),null).statusCode()).isEqualTo(200);
        assertThat(request("PUT",libraryPath(id),a.token(),null).statusCode()).isEqualTo(200);
        var privateMedia=request("POST",eventPath(id)+"/acesso",a.token(),Map.of("download",true));
        assertThat(privateMedia.statusCode()).isEqualTo(200); assertThat(privateMedia.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat((String)body(privateMedia).get("url")).contains("X-Amz-Expires=300","versionId=fixture-version","attachment");
        assertThat(items(request("GET","/api/v1/jogadores/biblioteca",b.token(),null))).isEmpty();
        assertThat(items(request("GET","/api/v1/jogadores/biblioteca",a.token(),null))).hasSize(1);
    }

    @Test void signedPublicUrlIsBoundedAndBlockingOrPurgeIntentStopsEveryNewAccess() throws Exception {
        var o=origin("UTC"); var a=player(); String id=replay(o,CLOCK.instant().minusSeconds(2*86400-10));
        var media=request("POST",eventPath(id)+"/acesso",a.token(),Map.of("download",false));
        assertThat(media.statusCode()).isEqualTo(200); assertThat((String)body(media).get("url")).contains("X-Amz-Expires=","inline");
        assertThat(Instant.parse((String)body(media).get("expira_em"))).isBefore(CLOCK.instant().plusSeconds(10));
        library.save(a.principal(),id);
        jdbc.update("INSERT INTO replay_lifecycle(event_id,blocked_at,blocked_by,block_reason) VALUES (?,?,?,'PRIVACY')",id,CLOCK.instant().atOffset(ZoneOffset.UTC),UUID.randomUUID());
        assertThat(request("GET",eventPath(id),a.token(),null).statusCode()).isEqualTo(404);
        assertThat(request("POST",eventPath(id)+"/acesso",a.token(),Map.of("download",false)).statusCode()).isEqualTo(404);
        assertThat(items(request("GET","/api/v1/jogadores/biblioteca",a.token(),null))).isEmpty();
        var available=request("GET","/api/v1/jogadores/quadras/"+o.court()+"/disponibilidade",a.token(),null);
        assertThat((List<?>)body(available).get("slots")).isEmpty();
        jdbc.update("UPDATE replay_lifecycle SET blocked_at=NULL,blocked_by=NULL,block_reason=NULL,purge_reserved_at=? WHERE event_id=?",CLOCK.instant().atOffset(ZoneOffset.UTC),id);
        assertThat(request("GET",eventPath(id),a.token(),null).statusCode()).isEqualTo(404);
    }

    @Test void inactiveOriginLeavesSavedLibraryRightsButDisappearsFromDiscoveryAndPublicDetail() throws Exception {
        var o=origin("UTC"); var a=player(); var b=player(); String id=replay(o,CLOCK.instant());
        library.save(a.principal(),id);
        jdbc.update("UPDATE empresa SET active=false WHERE id=?",o.company());
        assertThat(request("GET",eventPath(id),b.token(),null).statusCode()).isEqualTo(404);
        assertThat(request("GET",eventPath(id),a.token(),null).statusCode()).isEqualTo(200);
        assertThat(items(request("GET","/api/v1/jogadores/biblioteca",a.token(),null))).hasSize(1);
        assertThat(request("GET","/api/v1/jogadores/quadras/"+o.court()+"/lances",a.token(),null).statusCode()).isEqualTo(404);
        assertThat(request("GET","/api/v1/jogadores/arenas/"+o.company()+"/quadras",a.token(),null).statusCode()).isEqualTo(404);
    }

    @Test void concurrentQuotaAndDuplicateSavesNeverExceedOneHundredOrDuplicateRelationship() throws Exception {
        var o=origin("UTC"); var a=player();
        for(int i=0;i<99;i++) {
            String event=replay(o,CLOCK.instant());
            jdbc.update("INSERT INTO library_entry(user_id,event_id,saved_at) VALUES (?,?,?)",a.id(),event,CLOCK.instant().atOffset(ZoneOffset.UTC));
        }
        String one=replay(o,CLOCK.instant()),two=replay(o,CLOCK.instant());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var x=pool.submit(()->{start.await();return saveStatus(a,one);});
            var y=pool.submit(()->{start.await();return saveStatus(a,two);}); start.countDown();
            assertThat(List.of(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_entry WHERE user_id=?",Long.class,a.id())).isEqualTo(100);
        String winner=jdbc.queryForObject("SELECT event_id FROM library_entry WHERE user_id=? AND event_id IN (?,?)",String.class,a.id(),one,two);
        assertThat(saveStatus(a,winner)).isEqualTo(200);
        var b=player(); String shared=replay(o,CLOCK.instant());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> save=()->{start.await();return saveStatus(b,shared);};
            var x=pool.submit(save); var y=pool.submit(save); start.countDown();
            assertThat(List.of(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);
        }
    }
    int saveStatus(Player player,String event) {
        try { return library.save(player.principal(),event).created()?201:200; }
        catch(ApiException error) { return error.status(); }
    }

    @Test void saveWaitingOnPurgeLockRechecksCommittedIntentInsteadOfGrantingBrokenLibrary() throws Exception {
        var o=origin("UTC"); var a=player(); String id=replay(o,CLOCK.instant());
        var tx=new TransactionTemplate(transactions);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
            tx.execute(status->{
                jdbc.queryForObject("SELECT evento_id FROM replay WHERE evento_id=? FOR UPDATE",String.class,id);
                jdbc.update("INSERT INTO replay_lifecycle(event_id,purge_reserved_at) VALUES (?,?)",id,CLOCK.instant().atOffset(ZoneOffset.UTC));
                pending.set(pool.submit(()->saveStatus(a,id)));
                return null;
            });
            assertThat(pending.get().get(15,TimeUnit.SECONDS)).isEqualTo(404);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_entry WHERE event_id=?",Integer.class,id)).isZero();
    }

    @Test void removalAndAccountClosurePreserveOtherOwnerAndRollbackCanBeRetried() throws Exception {
        var o=origin("UTC"); var a=player(); var b=player(); String id=replay(o,CLOCK.instant().minusSeconds(2*86400-30));
        library.save(a.principal(),id); library.save(b.principal(),id); CLOCK.advance(30);
        new TransactionTemplate(transactions).execute(status->{playerAccounts.close(a.principal());status.setRollbackOnly();return null;});
        assertThat(jdbc.queryForObject("SELECT active FROM app_user WHERE id=?",Boolean.class,a.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_entry WHERE event_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(request("DELETE","/api/v1/jogadores/conta",a.token(),null).statusCode()).isEqualTo(204);
        assertThat(request("GET",eventPath(id),a.token(),null).statusCode()).isEqualTo(401);
        assertThat(request("GET",eventPath(id),b.token(),null).statusCode()).isEqualTo(200);
        assertThat(request("DELETE",libraryPath(id),b.token(),null).statusCode()).isEqualTo(204);
        assertThat(request("DELETE",libraryPath(id),b.token(),null).statusCode()).isEqualTo(204);
        assertThat(request("GET",eventPath(id),b.token(),null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT purged_at IS NULL FROM replay WHERE evento_id=?",Boolean.class,id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT email FROM app_user WHERE id=?",String.class,a.id())).isEqualTo("#closed:"+a.id());
    }

    @Test void principalResolvedBeforeLogoutCannotIssueMediaOrMutateLibraryAfterRevocation() {
        var o=origin("UTC"); var a=player(); String id=replay(o,CLOCK.instant());
        playerSessions.logout(a.principal());
        assertThatThrownBy(()->playerReplays.access(a.principal(),id,Map.of("download",false)))
                .isInstanceOfSatisfying(ApiException.class,error->assertThat(error.status()).isEqualTo(401));
        assertThatThrownBy(()->library.save(a.principal(),id))
                .isInstanceOfSatisfying(ApiException.class,error->assertThat(error.status()).isEqualTo(401));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_entry WHERE user_id=?",Integer.class,a.id())).isZero();
    }
}
