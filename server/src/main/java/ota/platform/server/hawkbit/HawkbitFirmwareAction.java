package ota.platform.server.hawkbit;

public record HawkbitFirmwareAction(
        String tenant,
        String endpoint,
        long actionId,
        long softwareModuleId,
        boolean installAfterDownload) {
}
