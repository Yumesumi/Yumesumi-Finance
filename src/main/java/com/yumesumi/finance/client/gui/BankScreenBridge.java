package com.yumesumi.finance.client.gui;

import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import net.minecraft.client.Minecraft;

public final class BankScreenBridge {
    private BankScreenBridge() {
    }

    public static void openOrUpdate(BankSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof BankScreen bankScreen) {
            bankScreen.update(payload);
            return;
        }
        minecraft.setScreen(new BankScreen(payload));
    }
}
