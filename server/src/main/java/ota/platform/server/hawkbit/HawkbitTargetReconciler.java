package ota.platform.server.hawkbit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ota.platform.server.device.Device;
import ota.platform.server.device.DeviceRepository;

@Component
@ConditionalOnProperty(
        name = "ota.hawkbit.management.enabled",
        havingValue = "true")
public class HawkbitTargetReconciler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    HawkbitTargetReconciler.class);

    private final DeviceRepository deviceRepository;
    private final HawkbitTargetClient hawkbitTargetClient;

    public HawkbitTargetReconciler(
            DeviceRepository deviceRepository,
            HawkbitTargetClient hawkbitTargetClient) {

        this.deviceRepository = deviceRepository;
        this.hawkbitTargetClient = hawkbitTargetClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureManagedTargets() {
        List<Device> enabledDevices =
                deviceRepository.findAll()
                        .stream()
                        .filter(Device::enabled)
                        .toList();

        long synchronizedCount = enabledDevices
                .stream()
                .filter(device ->
                        hawkbitTargetClient.ensureTarget(
                                device.endpoint(),
                                device.displayName()))
                .count();

        log.info(
                "hawkBit Target reconciliation completed: "
                        + "managed={}, synchronized={}",
                enabledDevices.size(),
                synchronizedCount);
    }
}
