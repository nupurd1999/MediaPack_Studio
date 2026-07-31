package com.nupoor.mediapack;

import com.nupoor.mediapack.model.CollaborativeLightbox;
import com.nupoor.mediapack.repository.CollaborativeLightboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
class CollaborativeLightboxTest {

    @Autowired
    private CollaborativeLightboxRepository lightboxRepository;

    @Test
    @DisplayName("Collaborative Lightbox — Concurrent edit triggers OptimisticLockingFailureException")
    void testCollaborativeLightbox_ConcurrentEdit_TriggersOptimisticLockingFailure() {
        // 1. Create and save initial Lightbox
        CollaborativeLightbox lightbox = new CollaborativeLightbox("Würth Tools Q3 Campaign", "user1");
        CollaborativeLightbox saved = lightboxRepository.save(lightbox);
        assertNotNull(saved.getId());
        assertEquals(0L, saved.getVersion());

        // 2. Load two separate instances representing 2 concurrent HTTP requests
        CollaborativeLightbox client1View = lightboxRepository.findById(saved.getId()).orElseThrow();
        CollaborativeLightbox client2View = lightboxRepository.findById(saved.getId()).orElseThrow();

        // 3. Client 1 updates title and saves (increments version from 0 to 1)
        client1View.setTitle("Würth Tools Q3 - Updated by Client 1");
        CollaborativeLightbox client1Saved = lightboxRepository.save(client1View);
        assertEquals(1L, client1Saved.getVersion());

        // 4. Client 2 tries to save stale version 0
        client2View.setTitle("Würth Tools Q3 - Conflicting Edit by Client 2");

        // Assert OptimisticLockingFailureException is thrown
        assertThrows(OptimisticLockingFailureException.class, () -> {
            lightboxRepository.save(client2View);
        });
    }
}
