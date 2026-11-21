package com.nupoor.mediapack;

import com.nupoor.mediapack.model.ExportJobRequest;
import com.nupoor.mediapack.model.ExportJobState;
import com.nupoor.mediapack.service.RedisTaskPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class RedisTaskPublisherTest {

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private ListOperations<String, Object> listOps;
    private RedisTaskPublisher publisher;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(RedisTemplate.class);
        hashOps = Mockito.mock(HashOperations.class);
        listOps = Mockito.mock(ListOperations.class);

        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForList()).thenReturn(listOps);

        publisher = new RedisTaskPublisher(redisTemplate);
    }

    @Test
    @DisplayName("enqueue — successfully creates ExportJobState and falls back in memory")
    void testEnqueue_Success() {
        ExportJobRequest request = new ExportJobRequest("job-123", "lb-1", List.of("SKU-1", "SKU-2"), "DE");
        ExportJobState state = publisher.enqueue(request, "job-123", "DE");

        assertNotNull(state);
        assertEquals("job-123", state.getJobId());
        assertEquals("DE", state.getTenantRegion());
        assertEquals("SUBMITTED", state.getStatus());

        Optional<Map<Object, Object>> retrieved = publisher.getJobState("job-123");
        assertTrue(retrieved.isPresent());
        assertEquals("SUBMITTED", retrieved.get().get("status"));
    }

    @Test
    @DisplayName("updateJobState — updates progress and status in memory and records completedAt on completion")
    void testUpdateJobState_Completed() {
        ExportJobRequest request = new ExportJobRequest("job-456", null, List.of("SKU-1"), "NL");
        publisher.enqueue(request, "job-456", "NL");

        publisher.updateJobState("job-456", "PROCESSING", 50, null, null);
        Optional<Map<Object, Object>> proc = publisher.getJobState("job-456");
        assertTrue(proc.isPresent());
        assertEquals("50", proc.get().get("progress"));
        assertEquals("PROCESSING", proc.get().get("status"));

        publisher.updateJobState("job-456", "COMPLETED", 100, "/api/exports/download/job-456", null);
        Optional<Map<Object, Object>> completed = publisher.getJobState("job-456");
        assertTrue(completed.isPresent());
        assertEquals("100", completed.get().get("progress"));
        assertEquals("COMPLETED", completed.get().get("status"));
        assertNotNull(completed.get().get("completedAt"));
    }

    @Test
    @DisplayName("enqueue — handles null request gracefully without throwing NPE")
    void testEnqueue_NullRequest() {
        assertDoesNotThrow(() -> {
            ExportJobState state = publisher.enqueue(null, "job-null", null);
            assertEquals("DE", state.getTenantRegion());
        });
    }
}
