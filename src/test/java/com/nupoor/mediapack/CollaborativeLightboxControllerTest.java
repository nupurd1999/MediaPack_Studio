package com.nupoor.mediapack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nupoor.mediapack.controller.CollaborativeLightboxController;
import com.nupoor.mediapack.model.CollaborativeLightbox;
import com.nupoor.mediapack.service.CollaborativeLightboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CollaborativeLightboxController.class)
class CollaborativeLightboxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CollaborativeLightboxService lightboxService;

    @Test
    @DisplayName("POST /api/lightboxes — creates new lightbox successfully")
    void testCreateLightbox_Returns200() throws Exception {
        CollaborativeLightbox lb = new CollaborativeLightbox("Würth Tools Q3", "user-123");
        lb.setId("lb-001");

        Mockito.when(lightboxService.createLightbox("Würth Tools Q3", "user-123")).thenReturn(lb);

        mockMvc.perform(post("/api/lightboxes")
                .param("title", "Würth Tools Q3")
                .param("ownerUserId", "user-123")
                .header("X-Tenant-Region", "DE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("lb-001"))
            .andExpect(jsonPath("$.title").value("Würth Tools Q3"))
            .andExpect(jsonPath("$.ownerUserId").value("user-123"));
    }

    @Test
    @DisplayName("GET /api/lightboxes/{id} — returns lightbox by ID")
    void testGetLightboxById_ReturnsLightbox() throws Exception {
        CollaborativeLightbox lb = new CollaborativeLightbox("NL Catalog Campaign", "user-nl");
        lb.setId("lb-nl-1");

        Mockito.when(lightboxService.getLightboxById("lb-nl-1")).thenReturn(Optional.of(lb));

        mockMvc.perform(get("/api/lightboxes/lb-nl-1")
                .header("X-Tenant-Region", "NL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("lb-nl-1"))
            .andExpect(jsonPath("$.title").value("NL Catalog Campaign"));
    }

    @Test
    @DisplayName("GET /api/lightboxes/{id} — returns 404 when not found")
    void testGetLightboxById_Returns404() throws Exception {
        Mockito.when(lightboxService.getLightboxById("lb-unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/lightboxes/lb-unknown"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/lightboxes/user/{ownerUserId} — returns lightboxes by owner")
    void testGetLightboxesByOwner_ReturnsList() throws Exception {
        CollaborativeLightbox lb = new CollaborativeLightbox("US Heavy Machinery", "john-us");
        lb.setId("lb-us-1");

        Mockito.when(lightboxService.getLightboxesByOwner("john-us")).thenReturn(List.of(lb));

        mockMvc.perform(get("/api/lightboxes/user/john-us")
                .header("X-Tenant-Region", "US"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantRegion").value("US"))
            .andExpect(jsonPath("$.lightboxes[0].id").value("lb-us-1"));
    }

    @Test
    @DisplayName("POST /api/lightboxes/{id}/assets/{assetId} — adds asset to lightbox")
    void testAddAsset_ReturnsUpdatedLightbox() throws Exception {
        CollaborativeLightbox lb = new CollaborativeLightbox("Campaign", "user1");
        lb.setId("lb-001");
        lb.setAssetIds(List.of("asset-1"));

        Mockito.when(lightboxService.addAssetToLightbox("lb-001", "asset-1")).thenReturn(lb);

        mockMvc.perform(post("/api/lightboxes/lb-001/assets/asset-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assetIds[0]").value("asset-1"));
    }

    @Test
    @DisplayName("POST /api/lightboxes/{id}/assets/{assetId} — returns 409 CONFLICT on optimistic locking failure")
    void testAddAsset_OptimisticLockingFailure_Returns409() throws Exception {
        Mockito.when(lightboxService.addAssetToLightbox(eq("lb-001"), eq("asset-conflict")))
                .thenThrow(new OptimisticLockingFailureException("Version mismatch on CollaborativeLightbox @Version"));

        mockMvc.perform(post("/api/lightboxes/lb-001/assets/asset-conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("DELETE /api/lightboxes/{id}/assets/{assetId} — removes asset from lightbox")
    void testRemoveAsset_ReturnsUpdatedLightbox() throws Exception {
        CollaborativeLightbox lb = new CollaborativeLightbox("Campaign", "user1");
        lb.setId("lb-001");
        lb.setAssetIds(List.of());

        Mockito.when(lightboxService.removeAssetFromLightbox("lb-001", "asset-1")).thenReturn(lb);

        mockMvc.perform(delete("/api/lightboxes/lb-001/assets/asset-1"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/lightboxes/{id}/share — shares lightbox with another user")
    void testShareWithUser_ReturnsUpdatedLightbox() throws Exception {
        CollaborativeLightbox lb = new CollaborativeLightbox("Campaign", "user1");
        lb.setId("lb-001");
        lb.setSharedWithUserIds(List.of("colleague-2"));

        Mockito.when(lightboxService.shareWithUser("lb-001", "colleague-2")).thenReturn(lb);

        mockMvc.perform(post("/api/lightboxes/lb-001/share")
                .param("userId", "colleague-2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sharedWithUserIds[0]").value("colleague-2"));
    }
}
