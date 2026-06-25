package com.yumesumi.finance.money;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 存在 ItemStack 数据组件里的现金定义，用来给未来的纪念钞、活动券留扩展口。
 */
public record MoneyData(long valueCents, String displayName, CashKind kind) {
    public static final Codec<MoneyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("value_cents").forGetter(MoneyData::valueCents),
            Codec.STRING.fieldOf("display_name").forGetter(MoneyData::displayName),
            Codec.STRING.xmap(CashKind::valueOf, CashKind::name).fieldOf("kind").forGetter(MoneyData::kind)
    ).apply(instance, MoneyData::new));

    public MoneyData {
        if (valueCents <= 0) {
            throw new IllegalArgumentException("现金面值必须大于 0 分");
        }
    }
}
