package com.nupoor.mediapack.repository;

import com.nupoor.mediapack.model.CollaborativeLightbox;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollaborativeLightboxRepository extends MongoRepository<CollaborativeLightbox, String> {
    List<CollaborativeLightbox> findByOwnerUserId(String ownerUserId);
    List<CollaborativeLightbox> findBySharedWithUserIdsContaining(String userId);
}
