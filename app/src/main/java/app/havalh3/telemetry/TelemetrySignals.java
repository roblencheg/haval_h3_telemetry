package app.havalh3.telemetry;

final class TelemetrySignals {
    static final String FUEL_PERCENT = "car.basic.remain_fuel_percentage";
    static final String COOLANT_TEMP = "car.basic.coolant_temp";
    static final String BATTERY_VOLTAGE = "car.basic.battery_voltage";
    static final String MASTER_OIL_LEVEL = "car.basic.master.oil.tank.level";
    static final String EV_BATTERY_PERCENT = "car.ev_info.cur_battery_power_percentage";
    static final String ENGINE_OIL_NOTIFY = "car.basic.engne_oil_level_notify";
    static final String INSIDE_TEMP = "car.basic.inside_temp";
    static final String BATTERY_POWER_LEVEL = "car.basic.battery_power_level";
    static final String CURRENT_GEAR = "car.basic.current_gear";
    static final String GEAR_STATUS = "car.basic.gear_status";
    static final String AVG_FUEL = "car.basic.avg_fuel_consumption";
    static final String JOURNEY_AVG_FUEL = "car.basic.cur_journey_avg_fuel_consumption_a";
    static final String ODOMETER = "car.basic.total_odometer";
    static final String ENGINE_STATE = "car.basic.engine_state";
    static final String TPMS_STATUS = "car.basic.tpms_status";
    static final String TPMS_UNITS = "car.basic.tpms_units";

    static final String[] ALL = {
            FUEL_PERCENT,
            COOLANT_TEMP,
            BATTERY_VOLTAGE,
            MASTER_OIL_LEVEL,
            EV_BATTERY_PERCENT,
            ENGINE_OIL_NOTIFY,
            INSIDE_TEMP,
            BATTERY_POWER_LEVEL,
            CURRENT_GEAR,
            GEAR_STATUS,
            AVG_FUEL,
            JOURNEY_AVG_FUEL,
            ODOMETER,
            ENGINE_STATE,
            TPMS_STATUS,
            TPMS_UNITS
    };

    static String label(String key) {
        if (FUEL_PERCENT.equals(key)) return "Остаток топлива";
        if (COOLANT_TEMP.equals(key)) return "Температура охлаждающей жидкости";
        if (BATTERY_VOLTAGE.equals(key)) return "Напряжение аккумулятора";
        if (MASTER_OIL_LEVEL.equals(key)) return "Уровень основного бака";
        if (EV_BATTERY_PERCENT.equals(key)) return "Заряд тяговой батареи";
        if (ENGINE_OIL_NOTIFY.equals(key)) return "Предупреждение об уровне масла";
        if (INSIDE_TEMP.equals(key)) return "Температура в салоне";
        if (BATTERY_POWER_LEVEL.equals(key)) return "Уровень заряда аккумулятора";
        if (CURRENT_GEAR.equals(key)) return "Текущая передача";
        if (GEAR_STATUS.equals(key)) return "Состояние коробки передач";
        if (AVG_FUEL.equals(key)) return "Средний расход топлива";
        if (JOURNEY_AVG_FUEL.equals(key)) return "Средний расход за поездку A";
        if (ODOMETER.equals(key)) return "Общий пробег";
        if (ENGINE_STATE.equals(key)) return "Состояние двигателя";
        if (TPMS_STATUS.equals(key)) return "Состояние датчиков давления шин";
        if (TPMS_UNITS.equals(key)) return "Единицы давления в шинах";
        return key;
    }

    private TelemetrySignals() {
    }
}
