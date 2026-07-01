package com.example.fx.demo.backend.order.schema;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public OrderSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        // 開発用DBの既存CHECK制約を、追加したenum値に合わせて補正する。
        recreateCheckConstraint(
                "trigger_orders",
                "trigger_orders_status_check",
                "status",
                "'PENDING', 'WAITING', 'TRIGGERED', 'CANCELED', 'CANCELLED', 'REJECTED', 'EXPIRED'"
        );
        recreateCheckConstraint(
                "trigger_orders",
                "trigger_orders_order_type_check",
                "order_type",
                "'MARKET', 'LIMIT', 'STOP'"
        );
        recreateCheckConstraint(
                "fx_orders",
                "fx_orders_order_type_check",
                "order_type",
                "'MARKET', 'LIMIT', 'STOP'"
        );
        recreateCheckConstraint(
                "fx_orders",
                "fx_orders_source_check",
                "source",
                "'MANUAL', 'LOSS_CUT', 'TRIGGER'"
        );
        recreateCheckConstraint(
                "positions",
                "positions_status_check",
                "status",
                "'OPEN', 'CLOSED'"
        );
        recreateCheckConstraint(
                "positions",
                "positions_side_check",
                "side",
                "'BUY', 'SELL', 'LONG', 'SHORT'"
        );
        recreateCheckConstraint(
                "trades",
                "trades_trade_kind_check",
                "trade_kind",
                "'OPEN', 'CLOSE'"
        );
    }

    private void recreateCheckConstraint(
            String tableName,
            String constraintName,
            String columnName,
            String allowedValues
    ) {
        jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT IF EXISTS " + constraintName);
        jdbcTemplate.execute(
                "ALTER TABLE " + tableName
                        + " ADD CONSTRAINT " + constraintName
                        + " CHECK (" + columnName + " IN (" + allowedValues + "))"
        );
    }
}
