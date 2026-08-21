package ota.platform.server.security;

import org.eclipse.leshan.core.endpoint.EndpointUri;
import org.eclipse.leshan.core.peer.LwM2mPeer;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.request.UplinkRequest;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.security.Authorizer;
import org.eclipse.leshan.server.security.DefaultAuthorizer;
import org.eclipse.leshan.servers.security.Authorization;
import org.eclipse.leshan.servers.security.SecurityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import ota.platform.server.device.Device;
import ota.platform.server.device.DeviceRepository;

@Component
public class DeviceRegistryAuthorizer implements Authorizer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DeviceRegistryAuthorizer.class);

    private final DeviceRepository deviceRepository;
    private final DefaultAuthorizer defaultAuthorizer;

    public DeviceRegistryAuthorizer(
            DeviceRepository deviceRepository,
            SecurityStore securityStore) {

        this.deviceRepository = deviceRepository;
        this.defaultAuthorizer = new DefaultAuthorizer(securityStore);
    }

    @Override
    public Authorization isAuthorized(
            UplinkRequest<?> request,
            Registration registration,
            LwM2mPeer sender,
            EndpointUri endpointUri) {

        Authorization defaultAuthorization =
                defaultAuthorizer.isAuthorized(
                        request,
                        registration,
                        sender,
                        endpointUri);

        if (defaultAuthorization.isDeclined()) {
            return defaultAuthorization;
        }

        if (!(request instanceof RegisterRequest)) {
            return defaultAuthorization;
        }

        String endpoint = registration.getEndpoint();

        try {
            Device device = deviceRepository
                    .findByEndpoint(endpoint)
                    .orElse(null);

            if (device != null && device.enabled()) {
                return defaultAuthorization;
            }
        } catch (DataAccessException error) {
            log.error(
                    "Device registry lookup failed during "
                            + "LwM2M authorization: endpoint={}",
                    endpoint,
                    error);
            return Authorization.declined();
        }

        log.warn(
                "LwM2M registration declined by device registry: "
                        + "endpoint={}",
                endpoint);

        return Authorization.declined();
    }
}