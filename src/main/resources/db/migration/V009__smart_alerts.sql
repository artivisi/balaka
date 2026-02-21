-- Smart Alerts: alert rules and events
CREATE TABLE alert_rules (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    alert_type VARCHAR(30) NOT NULL UNIQUE,
    threshold DECIMAL(19, 2) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    last_triggered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP
);

CREATE TABLE alert_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_alert_rule UUID NOT NULL REFERENCES alert_rules(id),
    triggered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    severity VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    details TEXT,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100)
);

CREATE INDEX idx_alert_events_rule ON alert_events(id_alert_rule);
CREATE INDEX idx_alert_events_triggered ON alert_events(triggered_at DESC);
CREATE INDEX idx_alert_events_unacknowledged ON alert_events(acknowledged_at) WHERE acknowledged_at IS NULL;

-- Seed 7 default rules
INSERT INTO alert_rules (id, row_version, alert_type, threshold, enabled, description) VALUES
    (gen_random_uuid(), 0, 'CASH_LOW', 10000000.00, true, 'Peringatan jika saldo kas + bank di bawah ambang batas'),
    (gen_random_uuid(), 0, 'RECEIVABLE_OVERDUE', 0, true, 'Peringatan jika ada piutang yang jatuh tempo'),
    (gen_random_uuid(), 0, 'EXPENSE_SPIKE', 30.00, true, 'Peringatan jika biaya bulan ini naik lebih dari X% dari rata-rata 3 bulan sebelumnya'),
    (gen_random_uuid(), 0, 'PROJECT_COST_OVERRUN', 0, true, 'Peringatan jika ada proyek yang melebihi anggaran'),
    (gen_random_uuid(), 0, 'PROJECT_MARGIN_DROP', 10.00, true, 'Peringatan jika margin proyek turun di bawah X%'),
    (gen_random_uuid(), 0, 'COLLECTION_SLOWDOWN', 30.00, true, 'Peringatan jika rata-rata hari penagihan melebihi X hari'),
    (gen_random_uuid(), 0, 'CLIENT_CONCENTRATION', 50.00, true, 'Peringatan jika satu klien menyumbang lebih dari X% pendapatan');
