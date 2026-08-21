(() => {
    "use strict";

    const rows = Array.from(
        document.querySelectorAll("#device-table-body tr"));
    const searchInput = document.getElementById("device-search");
    const filterButtons = Array.from(
        document.querySelectorAll("[data-filter]"));
    const resultCount = document.getElementById("device-result-count");
    const noResults = document.getElementById("device-no-results");
    const resetButton = document.getElementById("device-filter-reset");

    let activeFilter = "all";

    const normalized = value =>
        (value || "").toLocaleLowerCase("ko-KR").trim();

    const matchesFilter = row => {
        if (activeFilter === "online") {
            return row.dataset.online === "true";
        }

        if (activeFilter === "offline") {
            return row.dataset.online !== "true";
        }

        if (activeFilter === "credential") {
            return row.dataset.credential !== "true";
        }

        return true;
    };

    const applyFilters = () => {
        const query = normalized(searchInput?.value);
        let visibleCount = 0;

        rows.forEach(row => {
            const haystack = normalized(
                row.dataset.name + " " + row.dataset.endpoint);
            const visible =
                matchesFilter(row) && haystack.includes(query);

            row.hidden = !visible;

            if (visible) {
                visibleCount += 1;
            }
        });

        if (resultCount) {
            resultCount.textContent = visibleCount + "개 장치";
        }

        if (noResults) {
            noResults.hidden = rows.length === 0 || visibleCount > 0;
        }
    };

    searchInput?.addEventListener("input", applyFilters);

    filterButtons.forEach(button => {
        button.addEventListener("click", () => {
            activeFilter = button.dataset.filter;

            filterButtons.forEach(candidate => {
                candidate.setAttribute(
                    "aria-pressed",
                    String(candidate === button));
            });

            applyFilters();
        });
    });

    resetButton?.addEventListener("click", () => {
        activeFilter = "all";

        if (searchInput) {
            searchInput.value = "";
            searchInput.focus();
        }

        filterButtons.forEach(button => {
            button.setAttribute(
                "aria-pressed",
                String(button.dataset.filter === "all"));
        });

        applyFilters();
    });

    const dialog = document.getElementById("device-create-dialog");
    const form = document.getElementById("device-create-form");
    const endpointInput = document.getElementById("endpoint");
    const displayNameInput = document.getElementById("display-name");
    const feedback = document.getElementById("device-create-result");

    const setFeedback = (message, state = "") => {
        if (!feedback) {
            return;
        }

        feedback.textContent = message;
        feedback.dataset.state = state;
    };

    const openDialog = () => {
        if (!dialog) {
            return;
        }

        setFeedback("");

        if (typeof dialog.showModal === "function") {
            dialog.showModal();
        } else {
            dialog.setAttribute("open", "");
        }

        window.setTimeout(() => endpointInput?.focus(), 0);
    };

    const closeDialog = () => {
        if (!dialog) {
            return;
        }

        if (typeof dialog.close === "function") {
            dialog.close();
        } else {
            dialog.removeAttribute("open");
        }
    };

    document.querySelectorAll("[data-open-device-dialog]")
        .forEach(button => button.addEventListener("click", openDialog));

    document.querySelectorAll("[data-close-device-dialog]")
        .forEach(button => button.addEventListener("click", closeDialog));

    dialog?.addEventListener("click", event => {
        if (event.target === dialog) {
            closeDialog();
        }
    });

    const readError = async response => {
        const body = await response.text();

        if (!body) {
            return "HTTP " + response.status;
        }

        try {
            const payload = JSON.parse(body);
            const messages = {
                "endpoint is invalid":
                    "Endpoint는 공백 없이 1~255자로 입력해 주세요.",
                "displayName is too long":
                    "표시 이름은 255자 이하로 입력해 주세요.",
                "endpoint already exists":
                    "이미 등록된 endpoint입니다."
            };

            return messages[payload.error]
                || payload.error
                || "HTTP " + response.status;
        } catch {
            return body;
        }
    };

    form?.addEventListener("submit", async event => {
        event.preventDefault();

        const endpoint = endpointInput.value.trim();
        const displayName = displayNameInput.value.trim();
        const submitButton = form.querySelector('[type="submit"]');
        const originalLabel = submitButton.textContent;

        endpointInput.value = endpoint;
        submitButton.disabled = true;
        submitButton.textContent = "등록 중…";
        setFeedback("Device Registry에 장치를 등록하고 있습니다.");

        try {
            const response = await fetch("/api/devices", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    endpoint,
                    displayName: displayName || null
                })
            });

            if (!response.ok) {
                setFeedback(await readError(response), "error");
                return;
            }

            const device = await response.json();

            window.location.assign(
                "/admin/devices/" + encodeURIComponent(device.endpoint));
        } catch (error) {
            setFeedback(
                "서버에 연결할 수 없습니다: " + error.message,
                "error");
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = originalLabel;
        }
    });

    applyFilters();
})();
