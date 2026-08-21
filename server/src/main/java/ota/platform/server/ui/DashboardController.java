package ota.platform.server.ui;

import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import ota.platform.server.device.DeviceRepository;
import ota.platform.server.device.Device;
import ota.platform.server.security.ActiveDeviceCredential;
import ota.platform.server.security.DeviceCredentialRepository;

import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.LeshanServer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class DashboardController {

    private final DeviceRepository deviceRepository;
    private final DeviceCredentialRepository credentialRepository;
    private final LeshanServer leshanServer;
    private final String hawkbitUiUrl;

    public DashboardController(
            DeviceRepository deviceRepository,
            DeviceCredentialRepository credentialRepository,
            LeshanServer leshanServer,
            @Value("${ota.hawkbit.ui-url:http://localhost:8088}")
            String hawkbitUiUrl) {

        this.deviceRepository = deviceRepository;
        this.credentialRepository = credentialRepository;
        this.leshanServer = leshanServer;
        this.hawkbitUiUrl = hawkbitUiUrl;
    }

    @GetMapping
    public String dashboard(Model model) {
        Set<String> credentialEndpoints = credentialRepository
                .findAllActive()
                .stream()
                .map(ActiveDeviceCredential::endpoint)
                .collect(Collectors.toSet());

        List<DashboardDevice> devices =
                deviceRepository.findAll().stream()
                        .map(device -> new DashboardDevice(
                                device.id(),
                                device.endpoint(),
                                device.displayName(),
                                device.enabled(),
                                leshanServer
                                        .getRegistrationService()
                                        .getByEndpoint(device.endpoint())
                                        != null,
                                credentialEndpoints.contains(
                                        device.endpoint())))
                        .toList();

        model.addAttribute("devices", devices);
        model.addAttribute("totalDevices", devices.size());
        model.addAttribute(
                "onlineDevices",
                devices.stream()
                        .filter(DashboardDevice::online)
                        .count());
        model.addAttribute(
                "credentialReadyDevices",
                devices.stream()
                        .filter(DashboardDevice::credentialProvisioned)
                        .count());
        model.addAttribute(
                "offlineDevices",
                devices.stream()
                        .filter(device -> !device.online())
                        .count());
        model.addAttribute("hawkbitUiUrl", hawkbitUiUrl);

        return "dashboard";
    }

    @GetMapping("/devices/{endpoint}")
    public String deviceDetail(
            @PathVariable String endpoint,
            Model model) {

        Device device = deviceRepository
                .findByEndpoint(endpoint)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "device not found"));

        Registration registration = leshanServer
                .getRegistrationService()
                .getByEndpoint(endpoint);

        ActiveDeviceCredential activeCredential = credentialRepository
                .findActivePskByEndpoint(endpoint)
                .orElse(null);

        model.addAttribute(
                "device",
                new DashboardDevice(
                        device.id(),
                        device.endpoint(),
                        device.displayName(),
                        device.enabled(),
                        registration != null,
                        activeCredential != null));

        model.addAttribute(
                "registrationAddress",
                registration == null
                        ? null
                        : registration.getSocketAddress().toString());

        List<String> objectLinks =
                registration == null
                        || registration.getSortedObjectLinks() == null
                        ? List.of()
                        : Arrays.stream(
                                        registration.getSortedObjectLinks())
                                .map(link -> link.getUriReference())
                                .toList();

        model.addAttribute("objectLinks", objectLinks);

        model.addAttribute(
                "activeCredential",
                activeCredential);
        model.addAttribute("hawkbitUiUrl", hawkbitUiUrl);

        return "device-detail";
    }

}
