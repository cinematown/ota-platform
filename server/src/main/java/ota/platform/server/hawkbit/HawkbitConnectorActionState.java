package ota.platform.server.hawkbit;

public enum HawkbitConnectorActionState {

    RECEIVED,
    DOWNLOAD,
    DOWNLOADED,
    RUNNING,
    FINISHED,
    WARNING,
    ERROR,
    CANCELED,
    CANCEL_REJECTED;

    public boolean isTerminal() {
        return this == FINISHED
                || this == ERROR
                || this == CANCELED;
    }
}
