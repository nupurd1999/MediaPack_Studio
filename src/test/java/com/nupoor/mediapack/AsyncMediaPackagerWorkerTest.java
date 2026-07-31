package com.nupoor.mediapack;

import com.nupoor.mediapack.service.AsyncMediaPackagerWorker;
import com.nupoor.mediapack.service.S3StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

class AsyncMediaPackagerWorkerTest {

    @Test
    @DisplayName("AsyncMediaPackagerWorker — Processes export and publishes WebSockets progress events")
    void testProcessExportJob_PublishesProgressEvents() {
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        S3StorageService s3Service = Mockito.mock(S3StorageService.class);

        Mockito.when(s3Service.generatePresignedUrl(any())).thenReturn("http://localhost:4566/bucket/exports/job-1.zip");

        AsyncMediaPackagerWorker worker = new AsyncMediaPackagerWorker(messagingTemplate, s3Service);

        worker.processExportJob("job-1", List.of("SKU-001", "SKU-002"));

        // Verify WebSocket message conversion and sending was invoked for topic
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/export-progress/job-1"), any(AsyncMediaPackagerWorker.ExportProgressEvent.class));
    }
}
