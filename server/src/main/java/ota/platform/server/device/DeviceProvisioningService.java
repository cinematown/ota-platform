package ota.platform.server.device;

import org.springframework.stereotype.Service;

import ota.platform.server.hawkbit.HawkbitTargetClient;

@Service
public class DeviceProvisioningService {

    private final DeviceRepository deviceRepository;
    private final HawkbitTargetClient hawkbitTargetClient;

    public DeviceProvisioningService(
            DeviceRepository deviceRepository,
            HawkbitTargetClient hawkbitTargetClient) {

        this.deviceRepository = deviceRepository;
        this.hawkbitTargetClient = hawkbitTargetClient;
    }

    public Device create(
            String endpoint,
            String displayName) {

        Device device = deviceRepository.create(
                endpoint,
                displayName);

        hawkbitTargetClient.ensureTarget(
                device.endpoint(),
                device.displayName());

        return device;
    }
}
