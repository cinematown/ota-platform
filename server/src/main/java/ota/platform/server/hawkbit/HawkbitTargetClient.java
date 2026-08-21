package ota.platform.server.hawkbit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HawkbitTargetClient {

    private static final Logger log =
            LoggerFactory.getLogger(HawkbitTargetClient.class);

    private final RestClient restClient;
    private final boolean enabled;

    public HawkbitTargetClient(
            @Value("${ota.hawkbit.management.enabled}")
                    boolean enabled,
            @Value("${ota.hawkbit.management.base-url}")
                    String baseUrl,
            @Value("${ota.hawkbit.management.username}")
                    String username,
            @Value("${ota.hawkbit.management.password}")
                    String password) {

        this.enabled = enabled;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers ->
                        headers.setBasicAuth(
                                username,
                                password))
                .build();
    }

    public boolean ensureTarget(
            String endpoint,
            String displayName) {

        if (!enabled) {
            return false;
        }

        String targetName =
                displayName == null || displayName.isBlank()
                        ? endpoint
                        : displayName;

        try {
            restClient.post()
                    .uri("/rest/v1/targets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(List.of(
                            new HawkbitTargetRequest(
                                    endpoint,
                                    targetName)))
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "hawkBit Target created: endpoint={}",
                    endpoint);
            return true;

        } catch (HttpClientErrorException.Conflict error) {
            log.info(
                    "hawkBit Target already exists: endpoint={}",
                    endpoint);
            return true;

        } catch (RestClientException error) {
            log.warn(
                    "Failed to ensure hawkBit Target: "
                            + "endpoint={}, error={}",
                    endpoint,
                    error.getMessage());
            return false;
        }
    }
}
