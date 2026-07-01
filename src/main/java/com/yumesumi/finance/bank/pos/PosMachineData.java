package com.yumesumi.finance.bank.pos;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yumesumi.finance.bank.enterprise.EnterpriseAccount;

public record PosMachineData(String enterpriseCode) {
    public static final Codec<PosMachineData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("enterprise_code", "").forGetter(PosMachineData::enterpriseCode)
    ).apply(instance, PosMachineData::new));

    public PosMachineData {
        enterpriseCode = sanitize(enterpriseCode);
    }

    public boolean bound() {
        return !enterpriseCode.isBlank();
    }

    public static String sanitize(String code) {
        if (code == null || code.isBlank()) return "";
        return EnterpriseAccount.normalizeCode(code);
    }
}
