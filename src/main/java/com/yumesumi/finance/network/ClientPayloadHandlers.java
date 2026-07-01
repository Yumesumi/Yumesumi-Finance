package com.yumesumi.finance.network;

import com.yumesumi.finance.client.gui.BankAdminScreen;
import com.yumesumi.finance.client.gui.BankScreen;
import com.yumesumi.finance.client.gui.EnterpriseScreen;
import com.yumesumi.finance.client.gui.PosMachineConfigScreen;
import com.yumesumi.finance.network.payload.BankAdminSnapshotPayload;
import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import com.yumesumi.finance.network.payload.EnterpriseSnapshotPayload;
import com.yumesumi.finance.network.payload.PosConfigPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    public static void handleBankSnapshot(BankSnapshotPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof BankScreen screen) {
                screen.update(payload);
            } else if (minecraft.screen instanceof BankScreen.TransferLoadingScreen loading) {
                loading.parent().update(payload);
                minecraft.setScreen(loading.parent());
            } else {
                minecraft.setScreen(new BankScreen(payload));
            }
        });
    }

    public static void handleAdminSnapshot(BankAdminSnapshotPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof BankAdminScreen screen) {
                screen.update(payload);
            } else {
                minecraft.setScreen(new BankAdminScreen(payload));
            }
        });
    }

    public static void handleEnterpriseSnapshot(EnterpriseSnapshotPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof EnterpriseScreen screen) {
                screen.update(payload);
            } else {
                minecraft.setScreen(new EnterpriseScreen(payload));
            }
        });
    }

    public static void handlePosConfig(PosConfigPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new PosMachineConfigScreen(payload.hand(), payload.enterpriseCode())));
    }
}
