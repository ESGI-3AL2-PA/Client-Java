package com.connectedneighbours.service;

import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.repository.ApiClient;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SyncServiceTest {

    @Test
    void syncCycle_offline_notifiesOfflineAndSkipsWork() {
        ApiClient apiClient = mock(ApiClient.class);
        IncidentRepository incidentRepo = mock(IncidentRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        ObjectMapper mapper = JacksonConfig.get();

        List<SyncStatus> statuses = new ArrayList<>();

        SyncService service = new SyncService(
                apiClient,
                incidentRepo,
                userRepo,
                mapper,
                () -> false,
                Runnable::run
        );
        service.setStatusListener(statuses::add);

        service.syncCycle();

        assertEquals(List.of(SyncStatus.OFFLINE), statuses);
        verifyNoInteractions(apiClient, incidentRepo, userRepo);
    }

    @Test
    void syncCycle_online_noChanges_reportsSuccess() throws Exception {
        ApiClient apiClient = mock(ApiClient.class);
        IncidentRepository incidentRepo = mock(IncidentRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        ObjectMapper mapper = JacksonConfig.get();

        when(incidentRepo.findUnsynced()).thenReturn(List.of());
        when(apiClient.get("/incidents?source=remote")).thenReturn("[]");
        when(userRepo.findAll()).thenReturn(List.of());
        when(apiClient.get("/users?source=remote")).thenReturn("[]");

        List<SyncStatus> statuses = new ArrayList<>();

        SyncService service = new SyncService(
                apiClient,
                incidentRepo,
                userRepo,
                mapper,
                () -> true,
                Runnable::run
        );
        service.setStatusListener(statuses::add);

        service.syncCycle();

        assertEquals(List.of(SyncStatus.SYNCING, SyncStatus.SUCCESS), statuses);
        verify(incidentRepo).findUnsynced();
        verify(apiClient).get("/incidents?source=remote");
        verify(userRepo).findAll();
        verify(apiClient).get("/users?source=remote");
    }

    @Test
    void syncCycle_resolvesIncidentConflict_usesMostRecentLocal() throws Exception {
        ApiClient apiClient = mock(ApiClient.class);
        IncidentRepository incidentRepo = mock(IncidentRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        ObjectMapper mapper = JacksonConfig.get();

        Incident local = new Incident();
        local.setId("inc-1");
        local.setUpdatedAt(LocalDateTime.now().plusHours(1));
        local.setSynced(false);

        Incident remote = new Incident();
        remote.setId("inc-1");
        remote.setUpdatedAt(LocalDateTime.now());

        when(incidentRepo.findUnsynced()).thenReturn(List.of(local));
        when(apiClient.get("/incidents/inc-1")).thenReturn(mapper.writeValueAsString(remote));
        when(apiClient.get("/incidents?source=remote")).thenReturn("[]");
        when(userRepo.findAll()).thenReturn(List.of());
        when(apiClient.get("/users?source=remote")).thenReturn("[]");

        SyncService service = new SyncService(
                apiClient,
                incidentRepo,
                userRepo,
                mapper,
                () -> true,
                Runnable::run
        );

        service.syncCycle();

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(apiClient).put(eq("/incidents/inc-1"), captor.capture());
        assertSame(local, captor.getValue());
        verify(incidentRepo).update(any(Incident.class));
        assertTrue(local.isSynced());
    }
}

