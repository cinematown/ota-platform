package ota.platform.server.hawkbit;

import java.time.Instant;

public record HawkbitConnectorAction(
        String tenant,
        String endpoint,
        long actionId,
        long softwareModuleId,
        boolean installAfterDownload,
        HawkbitConnectorActionState status,
        boolean installRequested,
        Integer lastFirmwareState,
        Integer lastUpdateResult,
        String detail,
        Instant createdAt,
        Instant updatedAt) {
}
