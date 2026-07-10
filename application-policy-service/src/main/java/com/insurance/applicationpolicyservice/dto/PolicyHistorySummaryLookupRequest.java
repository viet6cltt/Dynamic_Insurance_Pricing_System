package com.insurance.applicationpolicyservice.dto;

import java.util.List;
import java.util.UUID;

public record PolicyHistorySummaryLookupRequest(
        List<UUID> insuredPersonIds
) {}
