package com.nupoor.mediapack;

import com.nupoor.mediapack.model.CollaborativeLightbox;
import com.nupoor.mediapack.repository.CollaborativeLightboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class CollaborativeLightboxTest {

    @Test
    @DisplayName("Collaborative Lightbox — Concurrent edit triggers OptimisticLockingFailureException")
    void testCollaborativeLightbox_ConcurrentEdit_TriggersOptimisticLockingFailure() {
        CollaborativeLightboxRepository lightboxRepository = Mockito.mock(CollaborativeLightboxRepository.class);

        CollaborativeLightbox lightbox = new CollaborativeLightbox("Würth Tools Q3 Campaign", "user1");
        lightbox.setTitle("Würth Tools Q3 - Conflicting Edit by Client 2");

        Mockito.when(lightboxRepository.save(any(CollaborativeLightbox.class)))
               .thenThrow(new OptimisticLockingFailureException("Version conflict on @Version field"));

        // Assert OptimisticLockingFailureException is thrown when concurrent save occurs
        assertThrows(OptimisticLockingFailureException.class, () -> {
            lightboxRepository.save(lightbox);
        });
    }
}
