package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.*;
import br.com.clicklance.repository.LibraryRepository;
import br.com.clicklance.repository.PlayerReplayRepository;
import br.com.clicklance.security.PlayerPrincipal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class LibraryService {
    private final LibraryRepository library;
    private final PlayerReplayRepository replays;
    private final PlayerSessionService sessions;
    private final Clock clock;
    public LibraryService(LibraryRepository library,PlayerReplayRepository replays,PlayerSessionService sessions,Clock clock) {
        this.library=library; this.replays=replays; this.sessions=sessions; this.clock=clock;
    }
    @Transactional
    public LibrarySave save(PlayerPrincipal player,String event) {
        PlayerReplayService.authenticated(player); sessions.requireCurrent(player);
        var replay=replays.find(player.userId(),event,true).orElseThrow(PlayerReplayService::absent);
        PlayerReplayService.requireAccess(replay,clock.instant());
        if(replay.saved()) return new LibrarySave(false,replay.summary());
        if(library.count(player.userId())>=100) throw new ApiException(409,"LIBRARY_LIMIT_REACHED");
        library.insert(player.userId(),event,clock.instant());
        return new LibrarySave(true,replays.find(player.userId(),event,false).orElseThrow(PlayerReplayService::absent).summary());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(PlayerPrincipal player,int page,int size) {
        PlayerReplayService.authenticated(player); PlayerReplayService.pagination(page,size);
        return PlayerReplayService.page(replays.library(player.userId(),size,(long)page*size).stream().map(PlayerReplay::summary).toList(),page,size,replays.libraryVisibleCount(player.userId()));
    }
    @Transactional
    public void remove(PlayerPrincipal player,String event) {
        PlayerReplayService.authenticated(player); sessions.requireCurrent(player);
        replays.find(player.userId(),event,true); library.remove(player.userId(),event);
        // The committed missing relationship makes expired replay eligible for the durable purge selector.
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void removeAllForAccount(UUID user) {
        // Caller owns app_user lock; preserve the global user -> ordered replay lock order.
        library.lockAll(user); library.removeAll(user);
    }
}
