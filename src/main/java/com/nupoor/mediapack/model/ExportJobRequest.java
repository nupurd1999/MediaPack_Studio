package com.nupoor.mediapack.model;

import java.util.List;

public record ExportJobRequest(
    String jobId,
    String lightboxId,
    List<String> assetKeys,
    String tenantRegion
) {}
