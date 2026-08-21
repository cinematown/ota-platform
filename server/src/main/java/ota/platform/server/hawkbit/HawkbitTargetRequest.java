package ota.platform.server.hawkbit;

public record HawkbitTargetRequest(
        String controllerId,
        String name) {
}
