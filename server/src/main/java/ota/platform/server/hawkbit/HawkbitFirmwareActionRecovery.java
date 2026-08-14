package ota.platform.server.hawkbit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "ota.hawkbit.dmf.enabled",
        havingValue = "true")
public class HawkbitFirmwareActionRecovery {

    private static final Logger log =
            LoggerFactory.getLogger(
                    HawkbitFirmwareActionRecovery.class);

    private final HawkbitConnectorActionRepository actionRepository;
    private final HawkbitFirmwareActionTracker actionTracker;

    public HawkbitFirmwareActionRecovery(
            HawkbitConnectorActionRepository actionRepository,
            HawkbitFirmwareActionTracker actionTracker) {

        this.actionRepository = actionRepository;
        this.actionTracker = actionTracker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreActiveActions() {
        List<HawkbitConnectorAction> activeActions =
                actionRepository.findAllActive();

        for (HawkbitConnectorAction persisted : activeActions) {
            actionTracker.restore(persisted);
        }

        log.info(
                "Active hawkBit actions restored: count={}",
                activeActions.size());
    }
}
