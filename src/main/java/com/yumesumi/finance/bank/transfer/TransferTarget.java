package com.yumesumi.finance.bank.transfer;

import java.util.UUID;

public record TransferTarget(UUID owner, String displayName) {
    public TransferTarget {
        displayName = displayName == null || displayName.isBlank() ? owner.toString() : displayName.trim();
    }
}
