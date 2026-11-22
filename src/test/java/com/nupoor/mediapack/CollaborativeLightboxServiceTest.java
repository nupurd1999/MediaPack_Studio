package com.nupoor.mediapack;

import com.nupoor.mediapack.model.CollaborativeLightbox;
import com.nupoor.mediapack.repository.CollaborativeLightboxRepository;
import com.nupoor.mediapack.service.CollaborativeLightboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CollaborativeLightboxServiceTest {

    private CollaborativeLightboxRepository repository;
    private CollaborativeLightboxService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(CollaborativeLightboxRepository.class);
        service = new CollaborativeLightboxService(repository);
    }

    @Test
    @DisplayName("createLightbox — saves and returns new lightbox")
    void testCreateLightbox() {
        CollaborativeLightbox lb = new CollaborativeLightbox("Test Title", "user1");
        lb.setId("lb-1");
        when(repository.save(any(CollaborativeLightbox.class))).thenReturn(lb);

        CollaborativeLightbox created = service.createLightbox("Test Title", "user1");
        assertNotNull(created);
        assertEquals("lb-1", created.getId());
        assertEquals("Test Title", created.getTitle());
    }

    @Test
    @DisplayName("addAssetToLightbox — adds asset and saves")
    void testAddAssetToLightbox_Success() {
        CollaborativeLightbox lb = new CollaborativeLightbox("Title", "user1");
        lb.setId("lb-1");
        lb.setAssetIds(new ArrayList<>());

        when(repository.findById("lb-1")).thenReturn(Optional.of(lb));
        when(repository.save(any(CollaborativeLightbox.class))).thenAnswer(inv -> inv.getArgument(0));

        CollaborativeLightbox updated = service.addAssetToLightbox("lb-1", "asset-100");
        assertTrue(updated.getAssetIds().contains("asset-100"));
    }

    @Test
    @DisplayName("addAssetToLightbox — throws IllegalArgumentException when assetId is blank")
    void testAddAssetToLightbox_BlankAssetId() {
        assertThrows(IllegalArgumentException.class, () -> service.addAssetToLightbox("lb-1", "   "));
    }

    @Test
    @DisplayName("removeAssetFromLightbox — removes asset and saves")
    void testRemoveAssetFromLightbox_Success() {
        CollaborativeLightbox lb = new CollaborativeLightbox("Title", "user1");
        lb.setId("lb-1");
        lb.setAssetIds(new ArrayList<>(List.of("asset-100")));

        when(repository.findById("lb-1")).thenReturn(Optional.of(lb));
        when(repository.save(any(CollaborativeLightbox.class))).thenAnswer(inv -> inv.getArgument(0));

        CollaborativeLightbox updated = service.removeAssetFromLightbox("lb-1", "asset-100");
        assertFalse(updated.getAssetIds().contains("asset-100"));
    }

    @Test
    @DisplayName("shareWithUser — adds user to sharedWithUserIds")
    void testShareWithUser_Success() {
        CollaborativeLightbox lb = new CollaborativeLightbox("Title", "user1");
        lb.setId("lb-1");
        lb.setSharedWithUserIds(new ArrayList<>());

        when(repository.findById("lb-1")).thenReturn(Optional.of(lb));
        when(repository.save(any(CollaborativeLightbox.class))).thenAnswer(inv -> inv.getArgument(0));

        CollaborativeLightbox updated = service.shareWithUser("lb-1", "user2");
        assertTrue(updated.getSharedWithUserIds().contains("user2"));
    }

    @Test
    @DisplayName("deleteLightbox — deletes lightbox by id")
    void testDeleteLightbox() {
        service.deleteLightbox("lb-1");
        Mockito.verify(repository).deleteById("lb-1");
    }
}
