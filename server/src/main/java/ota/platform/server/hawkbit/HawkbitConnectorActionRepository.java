package ota.platform.server.hawkbit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HawkbitConnectorActionRepository {

    private final JdbcClient jdbcClient;

    public HawkbitConnectorActionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<HawkbitConnectorAction> find(
            String tenant,
            String endpoint,
            long actionId) {

        return jdbcClient.sql("""
                SELECT
                    tenant,
                    endpoint,
                    action_id,
                    software_module_id,
                    install_after_download,
                    status,
                    install_requested,
                    last_firmware_state,
                    last_update_result,
                    detail,
                    created_at,
                    updated_at
                FROM hawkbit_connector_actions
                WHERE tenant = :tenant
                  AND endpoint = :endpoint
                  AND action_id = :actionId
                """)
                .param("tenant", tenant)
                .param("endpoint", endpoint)
                .param("actionId", actionId)
                .query(this::mapAction)
                .optional();
    }

    public boolean createIfAbsent(
            String tenant,
            HawkbitFirmwareAction action) {

        int insertedRows = jdbcClient.sql("""
                INSERT INTO hawkbit_connector_actions (
                    tenant,
                    endpoint,
                    action_id,
                    software_module_id,
                    install_after_download
                )
                VALUES (
                    :tenant,
                    :endpoint,
                    :actionId,
                    :softwareModuleId,
                    :installAfterDownload
                )
                ON CONFLICT DO NOTHING
                """)
                .param("tenant", tenant)
                .param("endpoint", action.endpoint())
                .param("actionId", action.actionId())
                .param("softwareModuleId", action.softwareModuleId())
                .param(
                        "installAfterDownload",
                        action.installAfterDownload())
                .update();

        return insertedRows == 1;
    }

    public Optional<HawkbitConnectorAction> findActive(
            String tenant,
            String endpoint) {

        return jdbcClient.sql("""
                SELECT
                    tenant,
                    endpoint,
                    action_id,
                    software_module_id,
                    install_after_download,
                    status,
                    install_requested,
                    last_firmware_state,
                    last_update_result,
                    detail,
                    created_at,
                    updated_at
                FROM hawkbit_connector_actions
                WHERE tenant = :tenant
                  AND endpoint = :endpoint
                  AND status NOT IN (
                      'FINISHED',
                      'ERROR',
                      'CANCELED'
                  )
                """)
                .param("tenant", tenant)
                .param("endpoint", endpoint)
                .query(this::mapAction)
                .optional();
    }

    public List<HawkbitConnectorAction> findAllActive() {

        return jdbcClient.sql("""
                SELECT
                    tenant,
                    endpoint,
                    action_id,
                    software_module_id,
                    install_after_download,
                    status,
                    install_requested,
                    last_firmware_state,
                    last_update_result,
                    detail,
                    created_at,
                    updated_at
                FROM hawkbit_connector_actions
                WHERE status NOT IN (
                    'FINISHED',
                    'ERROR',
                    'CANCELED'
                )
                ORDER BY created_at, endpoint
                """)
                .query(this::mapAction)
                .list();
    }
    
    public boolean updateStatus(
        HawkbitFirmwareAction action,
        HawkbitConnectorActionState status,
        String detail) {

    int updatedRows = jdbcClient.sql("""
            UPDATE hawkbit_connector_actions
            SET status = :status,
                detail = NULLIF(:detail, ''),
                updated_at = CURRENT_TIMESTAMP
            WHERE tenant = :tenant
              AND endpoint = :endpoint
              AND action_id = :actionId
            """)
            .param("status", status.name())
            .param("detail", detail == null ? "" : detail)
            .param("tenant", action.tenant())
            .param("endpoint", action.endpoint())
            .param("actionId", action.actionId())
            .update();

    return updatedRows == 1;
}

    public boolean updateFirmwareState(
            HawkbitFirmwareAction action,
            int firmwareState,
            HawkbitConnectorActionState status,
            String detail) {

        int updatedRows = jdbcClient.sql("""
                UPDATE hawkbit_connector_actions
                SET status = :status,
                    last_firmware_state = :firmwareState,
                    detail = NULLIF(:detail, ''),
                    updated_at = CURRENT_TIMESTAMP
                WHERE tenant = :tenant
                AND endpoint = :endpoint
                AND action_id = :actionId
                AND last_firmware_state
                    IS DISTINCT FROM :firmwareState
                """)
                .param("status", status.name())
                .param("firmwareState", firmwareState)
                .param("detail", detail == null ? "" : detail)
                .param("tenant", action.tenant())
                .param("endpoint", action.endpoint())
                .param("actionId", action.actionId())
                .update();

        return updatedRows == 1;
    }
    
    public boolean markInstallRequested(
            HawkbitFirmwareAction action) {

        int updatedRows = jdbcClient.sql("""
                UPDATE hawkbit_connector_actions
                SET install_requested = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE tenant = :tenant
                AND endpoint = :endpoint
                AND action_id = :actionId
                AND install_requested = FALSE
                """)
                .param("tenant", action.tenant())
                .param("endpoint", action.endpoint())
                .param("actionId", action.actionId())
                .update();

        return updatedRows == 1;
    }

    public boolean updateFirmwareResult(
            HawkbitFirmwareAction action,
            int updateResult,
            HawkbitConnectorActionState status,
            String detail) {

        int updatedRows = jdbcClient.sql("""
                UPDATE hawkbit_connector_actions
                SET status = :status,
                    last_update_result = :updateResult,
                    detail = NULLIF(:detail, ''),
                    updated_at = CURRENT_TIMESTAMP
                WHERE tenant = :tenant
                AND endpoint = :endpoint
                AND action_id = :actionId
                AND last_update_result
                    IS DISTINCT FROM :updateResult
                """)
                .param("status", status.name())
                .param("updateResult", updateResult)
                .param("detail", detail == null ? "" : detail)
                .param("tenant", action.tenant())
                .param("endpoint", action.endpoint())
                .param("actionId", action.actionId())
                .update();

        return updatedRows == 1;
    }

    private HawkbitConnectorAction mapAction(
            ResultSet resultSet,
            int rowNumber) throws SQLException {

        return new HawkbitConnectorAction(
                resultSet.getString("tenant"),
                resultSet.getString("endpoint"),
                resultSet.getLong("action_id"),
                resultSet.getLong("software_module_id"),
                resultSet.getBoolean("install_after_download"),
                HawkbitConnectorActionState.valueOf(
                        resultSet.getString("status")),
                resultSet.getBoolean("install_requested"),
                resultSet.getObject("last_firmware_state", Integer.class),
                resultSet.getObject("last_update_result", Integer.class),
                resultSet.getString("detail"),
                resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class).toInstant(),
                resultSet.getObject(
                        "updated_at",
                        OffsetDateTime.class).toInstant());
    }

}
