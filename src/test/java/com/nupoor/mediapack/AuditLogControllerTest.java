package com.nupoor.mediapack;

import com.nupoor.mediapack.controller.AuditLogController;
import com.nupoor.mediapack.model.AuditLog;
import com.nupoor.mediapack.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    @DisplayName("GET /api/audit-logs — returns audit log entries for tenant")
    void testGetAuditLogs_ReturnsLogs() throws Exception {
        AuditLog log = new AuditLog("DE", "admin", "ASSET_UPLOADED", "asset-1", "Uploaded file", "127.0.0.1");
        Mockito.when(auditLogService.getRecentLogs("DE")).thenReturn(List.of(log));

        mockMvc.perform(get("/api/audit-logs")
                .header("X-Tenant-Region", "DE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantRegion").value("DE"))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.logs[0].action").value("ASSET_UPLOADED"))
            .andExpect(jsonPath("$.logs[0].resourceId").value("asset-1"));
    }
}
