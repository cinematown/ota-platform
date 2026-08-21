package ota.platform.server.firmware;

import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.eclipse.leshan.core.request.exception.RequestCanceledException;
import org.eclipse.leshan.server.LeshanServer;
import org.eclipse.leshan.server.registration.Registration;


import org.springframework.stereotype.Service;

@Service
public class FirmwareUpdateDeviceService {

    private final LeshanServer leshanServer;

    public FirmwareUpdateDeviceService(LeshanServer leshanServer) {
        this.leshanServer = leshanServer;
    }

    public FirmwareCommandResult requestDownload(
            String endpoint,
            String packageUri) throws InterruptedException {

        Registration registration = findRegistration(endpoint);

        if (registration == null) {
            return FirmwareCommandResult.deviceOffline();
        }

        try{
            WriteResponse response = leshanServer.send(
                registration,
                new WriteRequest(5, 0, 1, packageUri));
            
            if (response == null) {
                return FirmwareCommandResult.deviceOffline();
            }

            if (response.isSuccess()) {
                return FirmwareCommandResult.acceptedResult();
            }

            return FirmwareCommandResult.rejected(String.valueOf(response));

        } catch (RequestCanceledException error) {
            return FirmwareCommandResult.deviceOffline();
        }

    }

    public FirmwareCommandResult requestInstall(
            String endpoint) throws InterruptedException {

        Registration registration = findRegistration(endpoint);

        if (registration == null) {
            return FirmwareCommandResult.deviceOffline();
        }

        try {
            ExecuteResponse response = leshanServer.send(
                registration,
                new ExecuteRequest(5, 0, 2));

            if (response == null) {
                return FirmwareCommandResult.deviceOffline();
            }

            if (response.isSuccess()) {
                return FirmwareCommandResult.acceptedResult();
            }

            return FirmwareCommandResult.rejected(
                    String.valueOf(response));

        } catch (RequestCanceledException error) {
            return FirmwareCommandResult.deviceOffline();
        }
    }

    public FirmwareCommandResult requestCancel(
            String endpoint) throws InterruptedException {

        Registration registration = findRegistration(endpoint);

        if (registration == null) {
            return FirmwareCommandResult.deviceOffline();
        }
        
        try {
            ExecuteResponse response = leshanServer.send(
                        registration,
                        new ExecuteRequest(5, 0, 10));

            if (response == null) {
                return FirmwareCommandResult.deviceOffline();
            }

            if (response.isSuccess()) {
                return FirmwareCommandResult.acceptedResult();
            }

            return FirmwareCommandResult.rejected(
                    String.valueOf(response));

        } catch (RequestCanceledException error) {
            return FirmwareCommandResult.deviceOffline();
        }

    }

    private Registration findRegistration(String endpoint) {
        return leshanServer
                .getRegistrationService()
                .getByEndpoint(endpoint);
    }
}