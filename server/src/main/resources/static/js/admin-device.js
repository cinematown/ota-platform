(() => {
    "use strict";

    const endpoint = document.body.dataset.endpoint;
    const online = document.body.dataset.online === "true";
    const endpointPath = encodeURIComponent(endpoint);
    const toast = document.getElementById("toast");
    let toastTimer;

    const detailTabs = Array.from(
        document.querySelectorAll("[data-detail-tab]"));
    const detailPanels = Array.from(
        document.querySelectorAll("[data-tab-panel]"));

    const activateDetailTab = tabName => {
        detailTabs.forEach(tab => {
            const active = tab.dataset.detailTab === tabName;
            tab.setAttribute("aria-selected", String(active));
            tab.tabIndex = active ? 0 : -1;
        });

        detailPanels.forEach(panel => {
            panel.hidden = panel.dataset.tabPanel !== tabName;
        });
    };

    detailTabs.forEach(tab => {
        tab.addEventListener("click", () =>
            activateDetailTab(tab.dataset.detailTab));
    });

    activateDetailTab("overview");

    const showToast = message => {
        if (!toast) {
            return;
        }

        window.clearTimeout(toastTimer);
        toast.textContent = message;
        toast.hidden = false;
        toastTimer = window.setTimeout(() => {
            toast.hidden = true;
        }, 2600);
    };

    const errorMessage = (response, payload) => {
        if (response.status === 404) {
            return "장치가 오프라인이거나 Resource를 제공하지 않습니다.";
        }

        if (payload && typeof payload === "object" && payload.error) {
            return payload.error;
        }

        if (typeof payload === "string" && payload) {
            return payload;
        }

        return "HTTP " + response.status;
    };

    const requestJson = async (path, options) => {
        const response = await fetch(path, options);
        const body = await response.text();
        let payload = null;

        if (body) {
            try {
                payload = JSON.parse(body);
            } catch {
                payload = body;
            }
        }

        if (!response.ok) {
            throw new Error(errorMessage(response, payload));
        }

        return payload;
    };

    const setCard = (cardId, valueId, metaId, value, meta) => {
        const card = document.getElementById(cardId);
        const valueElement = document.getElementById(valueId);
        const metaElement = document.getElementById(metaId);

        card?.classList.remove("is-loading");

        if (valueElement) {
            valueElement.textContent = value;
        }

        if (metaElement) {
            metaElement.textContent = meta;
        }
    };

    const setLoading = (cardId, valueId, message = "읽는 중…") => {
        document.getElementById(cardId)?.classList.add("is-loading");
        const valueElement = document.getElementById(valueId);

        if (valueElement) {
            valueElement.textContent = message;
        }
    };

    const firmwareStates = {
        0: "Idle",
        1: "Downloading",
        2: "Downloaded",
        3: "Updating"
    };

    const updateResults = {
        0: "Initial",
        1: "Success",
        2: "Not enough storage",
        3: "Out of memory",
        4: "Connection lost",
        5: "Integrity check failed",
        6: "Unsupported package",
        7: "Invalid URI",
        8: "Update failed",
        9: "Unsupported protocol",
        10: "Canceled",
        11: "Deferred"
    };

    const protocolNames = {
        0: "CoAP",
        1: "CoAPS",
        2: "HTTP",
        3: "HTTPS",
        4: "CoAP+TCP",
        5: "CoAPS+TCP"
    };

    const deliveryMethods = {
        0: "Pull only",
        1: "Push only",
        2: "Pull & Push"
    };

    const readFirmware = async () => {
        setLoading("firmware-state-card", "firmware-state-value");
        setLoading("update-result-card", "update-result-value");

        try {
            const status = await requestJson(
                "/api/devices/" + endpointPath + "/firmware/status");
            const stateLabel =
                firmwareStates[status.state] || "Unknown " + status.state;
            const resultLabel =
                updateResults[status.updateResult]
                || "Unknown " + status.updateResult;

            setCard(
                "firmware-state-card",
                "firmware-state-value",
                "firmware-state-meta",
                stateLabel,
                "State " + status.state + " · /5/0/3");
            setCard(
                "update-result-card",
                "update-result-value",
                "update-result-meta",
                resultLabel,
                "Result " + status.updateResult + " · /5/0/5");
        } catch (error) {
            setCard(
                "firmware-state-card",
                "firmware-state-value",
                "firmware-state-meta",
                "읽기 실패",
                error.message);
            setCard(
                "update-result-card",
                "update-result-value",
                "update-result-meta",
                "읽기 실패",
                error.message);
            throw error;
        }
    };

    const readCapabilities = async () => {
        setLoading("capability-card", "capability-value");

        try {
            const capabilities = await requestJson(
                "/api/devices/" + endpointPath
                + "/firmware/capabilities");
            const protocols = capabilities.protocolSupport
                .map(value => protocolNames[value] || "Protocol " + value)
                .join(", ");
            const method = deliveryMethods[capabilities.deliveryMethod]
                || "Method " + capabilities.deliveryMethod;

            setCard(
                "capability-card",
                "capability-value",
                "capability-meta",
                method,
                protocols + " · /5/0/8, /5/0/9");
        } catch (error) {
            setCard(
                "capability-card",
                "capability-value",
                "capability-meta",
                "읽기 실패",
                error.message);
            throw error;
        }
    };

    const readBms = async () => {
        setLoading("bms-voltage-card", "bms-voltage-value");

        try {
            const telemetry = await requestJson(
                "/api/devices/" + endpointPath + "/bms/voltage");
            const collectedAt = telemetry.collectedAt
                ? new Intl.DateTimeFormat("ko-KR", {
                    dateStyle: "short",
                    timeStyle: "medium"
                }).format(new Date(telemetry.collectedAt))
                : "수집 시각 없음";

            setCard(
                "bms-voltage-card",
                "bms-voltage-value",
                "bms-voltage-meta",
                Number(telemetry.voltage).toFixed(2) + " " + telemetry.unit,
                collectedAt + " · /33000/0/0");
        } catch (error) {
            setCard(
                "bms-voltage-card",
                "bms-voltage-value",
                "bms-voltage-meta",
                "읽기 실패",
                error.message);
            throw error;
        }
    };

    const readAll = async () => {
        const results = await Promise.allSettled([
            readFirmware(),
            readCapabilities(),
            readBms()
        ]);
        const failures = results.filter(
            result => result.status === "rejected").length;

        showToast(
            failures === 0
                ? "장치 상태를 모두 읽었습니다."
                : "일부 Resource를 읽지 못했습니다.");
    };

    const runRead = async (action, button) => {
        if (!online) {
            showToast("장치가 오프라인입니다.");
            return;
        }

        const originalLabel = button?.textContent;

        if (button) {
            button.disabled = true;
            button.textContent = "읽는 중…";
        }

        try {
            if (action === "firmware") {
                await readFirmware();
                showToast("Firmware 상태를 읽었습니다.");
            } else if (action === "capabilities") {
                await readCapabilities();
                showToast("Firmware capability를 읽었습니다.");
            } else if (action === "bms") {
                await readBms();
                showToast("BMS 전압을 읽었습니다.");
            } else {
                await readAll();
            }
        } catch (error) {
            showToast("읽기 실패: " + error.message);
        } finally {
            if (button) {
                button.disabled = false;
                button.textContent = originalLabel;
            }
        }
    };

    document.querySelectorAll("[data-read-action]").forEach(button => {
        button.addEventListener("click", () =>
            runRead(button.dataset.readAction, button));
    });

    document.getElementById("refresh-all")?.addEventListener(
        "click",
        event => runRead("all", event.currentTarget));

    const resourceForm = document.getElementById("resource-read-form");
    const objectIdInput = document.getElementById("object-id");
    const instanceIdInput = document.getElementById("instance-id");
    const resourceIdInput = document.getElementById("resource-id");
    const resourceResult = document.getElementById("resource-result");

    document.querySelectorAll("[data-resource-preset]")
        .forEach(button => {
            button.addEventListener("click", () => {
                const [objectId, instanceId, resourceId] =
                    button.dataset.resourcePreset.split("/");

                objectIdInput.value = objectId;
                instanceIdInput.value = instanceId;
                resourceIdInput.value = resourceId;
                resourceForm.requestSubmit();
            });
        });

    resourceForm?.addEventListener("submit", async event => {
        event.preventDefault();

        const objectId = objectIdInput.value;
        const instanceId = instanceIdInput.value;
        const resourceId = resourceIdInput.value;
        const submitButton = resourceForm.querySelector('[type="submit"]');
        const originalLabel = submitButton.textContent;
        const path = "/api/devices/" + endpointPath
            + "/lwm2m/" + objectId + "/" + instanceId + "/" + resourceId;

        submitButton.disabled = true;
        submitButton.textContent = "읽는 중…";
        resourceResult.textContent =
            "/" + objectId + "/" + instanceId + "/" + resourceId
            + " 요청 중…";

        try {
            const payload = await requestJson(path);
            resourceResult.textContent =
                JSON.stringify(payload, null, 2);
        } catch (error) {
            resourceResult.textContent = "읽기 실패\n" + error.message;
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = originalLabel;
        }
    });

    const copyText = async text => {
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
            return;
        }

        const temporary = document.createElement("textarea");
        temporary.value = text;
        temporary.style.position = "fixed";
        temporary.style.opacity = "0";
        document.body.appendChild(temporary);
        temporary.select();
        document.execCommand("copy");
        temporary.remove();
    };

    document.querySelectorAll("[data-object-link]").forEach(button => {
        button.addEventListener("click", async () => {
            try {
                await copyText(button.dataset.objectLink);
                showToast(button.dataset.objectLink + " 경로를 복사했습니다.");
            } catch (error) {
                showToast("복사 실패: " + error.message);
            }
        });
    });

    const credentialForm = document.getElementById("credential-form");
    const credentialIdentity =
        document.getElementById("credential-identity");
    const credentialKey = document.getElementById("credential-key");
    const credentialResult =
        document.getElementById("credential-result");
    const generateButton =
        document.getElementById("credential-generate");
    const toggleButton =
        document.getElementById("credential-toggle");
    const copyButton =
        document.getElementById("credential-copy");

    const setCredentialFeedback = (message, state = "") => {
        credentialResult.textContent = message;
        credentialResult.dataset.state = state;
    };

    generateButton?.addEventListener("click", () => {
        if (typeof window.crypto?.getRandomValues !== "function") {
            setCredentialFeedback(
                "이 브라우저에서는 안전한 PSK를 생성할 수 없습니다.",
                "error");
            return;
        }

        const bytes = new Uint8Array(32);
        window.crypto.getRandomValues(bytes);
        credentialKey.value = Array.from(
            bytes,
            byte => byte.toString(16).padStart(2, "0"))
            .join("");
        credentialKey.type = "text";
        toggleButton.textContent = "숨기기";
        toggleButton.setAttribute("aria-pressed", "true");
        setCredentialFeedback(
            "32 byte PSK를 생성했습니다. 장치에 provisioning할 값을 복사해 두세요.");
    });

    toggleButton?.addEventListener("click", () => {
        const reveal = credentialKey.type === "password";
        credentialKey.type = reveal ? "text" : "password";
        toggleButton.textContent = reveal ? "숨기기" : "보기";
        toggleButton.setAttribute("aria-pressed", String(reveal));
    });

    copyButton?.addEventListener("click", async () => {
        if (!credentialKey.value) {
            setCredentialFeedback("먼저 PSK를 입력하거나 생성해 주세요.", "error");
            return;
        }

        try {
            await copyText(credentialKey.value);
            setCredentialFeedback(
                "PSK를 복사했습니다. 노출되지 않도록 안전하게 다뤄 주세요.");
        } catch (error) {
            setCredentialFeedback("복사 실패: " + error.message, "error");
        }
    });

    credentialForm?.addEventListener("submit", async event => {
        event.preventDefault();

        const mode = credentialForm.dataset.mode;
        const suffix = mode === "rotate" ? "/rotate" : "";
        const submitButton = credentialForm.querySelector('[type="submit"]');
        const originalLabel = submitButton.textContent;

        submitButton.disabled = true;
        submitButton.textContent =
            mode === "rotate" ? "교체 중…" : "생성 중…";
        setCredentialFeedback(
            mode === "rotate"
                ? "PSK를 교체하고 기존 DTLS session을 종료합니다."
                : "PSK credential을 생성합니다.");

        try {
            await requestJson(
                "/api/devices/" + endpointPath
                + "/credentials/psk" + suffix,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({
                        identity: credentialIdentity.value.trim(),
                        keyHex: credentialKey.value
                    })
                });

            credentialKey.value = "";
            window.location.reload();
        } catch (error) {
            setCredentialFeedback(error.message, "error");
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = originalLabel;
        }
    });

    document.getElementById("credential-revoke")
        ?.addEventListener("click", async event => {
            if (!window.confirm(
                "활성 PSK를 폐기하고 현재 DTLS 연결을 종료할까요?")) {
                return;
            }

            const button = event.currentTarget;
            const originalLabel = button.textContent;
            button.disabled = true;
            button.textContent = "폐기 중…";
            setCredentialFeedback(
                "활성 PSK를 폐기하고 DTLS session을 종료합니다.");

            try {
                await requestJson(
                    "/api/devices/" + endpointPath
                    + "/credentials/psk/revoke",
                    {method: "POST"});
                window.location.reload();
            } catch (error) {
                setCredentialFeedback(error.message, "error");
                button.disabled = false;
                button.textContent = originalLabel;
            }
        });
})();
