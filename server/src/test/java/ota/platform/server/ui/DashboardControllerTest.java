package ota.platform.server.ui;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ota.platform.server.device.Device;
import ota.platform.server.device.DeviceRepository;
import ota.platform.server.security.ActiveDeviceCredential;
import ota.platform.server.security.DeviceCredentialRepository;

import org.eclipse.leshan.server.LeshanServer;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class DashboardControllerTest {

    private MockMvc mockMvc;
    private DeviceRepository deviceRepository;
    private DeviceCredentialRepository credentialRepository;
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        credentialRepository =
                mock(DeviceCredentialRepository.class);
        LeshanServer leshanServer = mock(LeshanServer.class);
        registrationService = mock(RegistrationService.class);

        when(leshanServer.getRegistrationService())
                .thenReturn(registrationService);

        DashboardController controller = new DashboardController(
                deviceRepository,
                credentialRepository,
                leshanServer,
                "http://localhost:8088");

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setViewResolvers(thymeleafViewResolver())
                .build();
    }

    @Test
    void rendersFleetSummaryAndDeviceStates() throws Exception {
        Device onlineDevice = device(
                "linux-reference-01",
                "Linux Reference");
        Device offlineDevice = device(
                "stm32-f429zi-01",
                null);

        when(deviceRepository.findAll())
                .thenReturn(List.of(onlineDevice, offlineDevice));
        when(credentialRepository.findAllActive())
                .thenReturn(List.of(credential(onlineDevice)));
        when(registrationService.getByEndpoint(
                onlineDevice.endpoint()))
                .thenReturn(mock(Registration.class));

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("totalDevices", 2))
                .andExpect(model().attribute("onlineDevices", 1L))
                .andExpect(model().attribute(
                        "credentialReadyDevices",
                        1L))
                .andExpect(model().attribute("offlineDevices", 1L))
                .andExpect(content().string(
                        containsString("Linux Reference")))
                .andExpect(content().string(
                        containsString("class=\"admin-shell\"")))
                .andExpect(content().string(
                        containsString("class=\"data-table\"")))
                .andExpect(content().string(
                        containsString("PSK 필요")));
    }

    @Test
    void rendersDeviceDetailWithCredentialControls()
            throws Exception {

        Device device = device(
                "linux-reference-01",
                "Linux Reference");
        ActiveDeviceCredential credential = credential(device);

        when(deviceRepository.findByEndpoint(device.endpoint()))
                .thenReturn(Optional.of(device));
        when(credentialRepository.findActivePskByEndpoint(
                device.endpoint()))
                .thenReturn(Optional.of(credential));
        when(registrationService.getByEndpoint(device.endpoint()))
                .thenReturn(null);

        mockMvc.perform(get(
                        "/admin/devices/{endpoint}",
                        device.endpoint()))
                .andExpect(status().isOk())
                .andExpect(view().name("device-detail"))
                .andExpect(content().string(
                        containsString("DTLS-PSK Credential")))
                .andExpect(content().string(
                        containsString("data-online=\"false\"")))
                .andExpect(content().string(
                        containsString("data-detail-tab=\"resources\"")))
                .andExpect(content().string(
                        containsString("data-tab-panel=\"security\"")))
                .andExpect(content().string(
                        containsString("class=\"resource-table\"")))
                .andExpect(content().string(
                        containsString(credential.secretReference())));
    }

    private ThymeleafViewResolver thymeleafViewResolver() {
        ClassLoaderTemplateResolver templateResolver =
                new ClassLoaderTemplateResolver();

        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");
        templateResolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        ThymeleafViewResolver viewResolver =
                new ThymeleafViewResolver();

        viewResolver.setTemplateEngine(templateEngine);
        viewResolver.setCharacterEncoding("UTF-8");

        return viewResolver;
    }

    private Device device(String endpoint, String displayName) {
        Instant createdAt = Instant.parse("2026-08-21T00:00:00Z");

        return new Device(
                UUID.randomUUID(),
                endpoint,
                displayName,
                true,
                createdAt,
                createdAt);
    }

    private ActiveDeviceCredential credential(Device device) {
        UUID credentialId = UUID.randomUUID();

        return new ActiveDeviceCredential(
                credentialId,
                device.id(),
                device.endpoint(),
                device.endpoint(),
                "db:" + credentialId);
    }
}
