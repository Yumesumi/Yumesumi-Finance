package com.yumesumi.finance.network;

import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import java.lang.reflect.Method;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleBankSnapshot(BankSnapshotPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        // 避免专用服务端加载任何 Minecraft 客户端类。
        context.enqueueWork(() -> {
            try {
                Class<?> bridge = Class.forName("com.yumesumi.finance.client.gui.BankScreenBridge");
                Method method = bridge.getMethod("openOrUpdate", BankSnapshotPayload.class);
                method.invoke(null, payload);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("无法打开梦澄金融 GUI", exception);
            }
        });
    }
}
