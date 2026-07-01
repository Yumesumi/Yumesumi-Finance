package com.yumesumi.finance.block.entity;

import com.yumesumi.finance.bank.pos.PosMachineData;
import com.yumesumi.finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PosMachineBlockEntity extends BlockEntity {
    private String enterpriseCode = "";

    public PosMachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.POS_MACHINE.get(), pos, blockState);
    }

    public String enterpriseCode() {
        return enterpriseCode;
    }

    public void setEnterpriseCode(String enterpriseCode) {
        this.enterpriseCode = PosMachineData.sanitize(enterpriseCode);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!enterpriseCode.isBlank()) {
            tag.putString("enterprise_code", enterpriseCode);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String code = tag.getString("enterprise_code");
        try {
            enterpriseCode = PosMachineData.sanitize(code);
        } catch (IllegalArgumentException exception) {
            enterpriseCode = "";
        }
    }
}
