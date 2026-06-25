package com.yumesumi.finance;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALLOW_CUSTOM_CASH = BUILDER
            .comment("是否允许管理员或未来数据包创建自定义纪念钞。")
            .define("allowCustomCash", true);

    public static final ModConfigSpec.LongValue MAX_CUSTOM_CASH_CENTS = BUILDER
            .comment("自定义现金的最大面值，单位为分。")
            .defineInRange("maxCustomCashCents", 1_000_000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
