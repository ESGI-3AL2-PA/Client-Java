package com.connectedneighbours.service;

import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.repository.ApiClient;
import com.connectedneighbours.repository.DistrictRepository;
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
        DistrictRepository districtRepo = mock(DistrictRepository.class);
        ObjectMapper mapper = JacksonConfig.get();

        List<SyncStatus> statuses = new ArrayList<>();

        SyncService service = new SyncService(
                apiClient,
                incidentRepo,
                userRepo,
                districtRepo,
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
        DistrictRepository districtRepo = mock(DistrictRepository.class);
        ObjectMapper mapper = JacksonConfig.get();

        when(incidentRepo.findUnsynced()).thenReturn(List.of());
        when(apiClient.get("/incidents?source=remote")).thenReturn("[]");
        when(apiClient.get("/users?source=remote")).thenReturn("[]");
        when(apiClient.get("/districts")).thenReturn("[]");

        List<SyncStatus> statuses = new ArrayList<>();

        SyncService service = new SyncService(
                apiClient,
                incidentRepo,
                userRepo,
                districtRepo,
                mapper,
                () -> true,
                Runnable::run
        );
        service.setStatusListener(statuses::add);

        service.syncCycle();

        assertEquals(List.of(SyncStatus.SYNCING, SyncStatus.SUCCESS), statuses);
        verify(incidentRepo).findUnsynced();
        verify(apiClient).get("/incidents?source=remote");
        verify(apiClient).get("/users?source=remote");
    }

    @Test
    void syncCycle_resolvesIncidentConflict_usesMostRecentLocal() throws Exception {
        ApiClient apiClient = mock(ApiClient.class);
        IncidentRepository incidentRepo = mock(IncidentRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        DistrictRepository districtRepo = mock(DistrictRepository.class);
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
        when(apiClient.get("/users?source=remote")).thenReturn("[]");
        when(apiClient.get("/districts")).thenReturn("[]");

        SyncService service = new SyncService(
                apiClient,
                incidentRepo,
                userRepo,
                districtRepo,
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

    @Test
    void syncCycle_localIncidentNotFoundOnServer_createsViaPost() throws Exception {
        ApiClient apiClient = mock(ApiClient.class);
        IncidentRepository incidentRepo = mock(IncidentRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        DistrictRepository districtRepo = mock(DistrictRepository.class);
        ObjectMapper mapper = JacksonConfig.get();

        Incident local = new Incident();
        local.setId("inc-new");
        local.setUpdatedAt(LocalDateTime.now());
        local.setSynced(false);

        // Le GET renvoie un 404 (ApiException) → l'incident n'existe pas sur le serveur
        when(incidentRepo.findUnsynced()).thenReturn(List.of(local));
        when(apiClient.get("/incidents/inc-new")).thenThrow(
                new com.connectedneighbours.repository.ApiException(404, "Not Found"));
        when(apiClient.get("/incidents?source=remote")).thenReturn("[]");
        when(apiClient.get("/users?source=remote")).thenReturn("[]");
        when(apiClient.get("/districts")).thenReturn("[]");

        SyncService service = new SyncService(
                apiClient, incidentRepo, userRepo, districtRepo,
                mapper, () -> true, Runnable::run
        );

        service.syncCycle();

        // Vérifie qu'un POST a été fait pour créer l'incident
        verify(apiClient).post(eq("/incidents"), any(Incident.class));
        verify(apiClient, never()).put(anyString(), any());
        assertTrue(local.isSynced());
        verify(incidentRepo).update(any(Incident.class));
    }
}
