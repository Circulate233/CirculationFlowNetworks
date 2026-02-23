package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.blocks.machines.BlockCirculationFurnace;
import com.circulation.circulation_networks.blocks.machines.BlockWindTurbine;
import com.circulation.circulation_networks.blocks.nodes.BlockChargingTower;
import com.circulation.circulation_networks.blocks.nodes.BlockEnergyInductionTower;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class RegistryBlocks {

    private static final List<Block> BLOCKS_TO_REGISTER = new LinkedList<>();
    private static final List<Block> BLOCK_MODELS_TO_REGISTER = new LinkedList<>();

    public static BlockEnergyInductionTower blockEnergyInductionTower;
    public static BlockChargingTower blockChargingTower;
    public static BlockWindTurbine blockWindTurbine;
    public static BlockCirculationFurnace blockCirculationFurnace;

    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        prepareItemBlockRegister(blockEnergyInductionTower = registerBlock(new BlockEnergyInductionTower()));
        prepareItemBlockRegister(blockChargingTower = registerBlock(new BlockChargingTower()));
        prepareItemBlockRegister(blockWindTurbine = registerBlock(new BlockWindTurbine()));
        prepareItemBlockRegister(blockCirculationFurnace = registerBlock(new BlockCirculationFurnace()));

        BLOCKS_TO_REGISTER.forEach(event.getRegistry()::register);
        BLOCKS_TO_REGISTER.clear();
    }

    public static void registerBlockModels() {
        if (FMLCommonHandler.instance().getSide().isServer()) {
            BLOCK_MODELS_TO_REGISTER.clear();
            return;
        }
        BLOCK_MODELS_TO_REGISTER.forEach(RegistryBlocks::registerBlockModel);
        BLOCK_MODELS_TO_REGISTER.clear();
    }

    public static void registerBlockModel(final Block block) {
        Item item = Item.getItemFromBlock(block);
        ResourceLocation registryName = Objects.requireNonNull(item.getRegistryName());
        ModelBakery.registerItemVariants(item, registryName);
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(registryName, "inventory"));
    }

    public static <T extends Block> T registerBlock(T block) {
        BLOCKS_TO_REGISTER.add(block);
        BLOCK_MODELS_TO_REGISTER.add(block);
        return block;
    }

    public static ItemBlock prepareItemBlockRegister(Block block) {
        return prepareItemBlockRegister(new ItemBlock(block));
    }

    public static <T extends ItemBlock> T prepareItemBlockRegister(T item) {
        if (item.getRegistryName() == null) {
            Block block = item.getBlock();
            ResourceLocation registryName = Objects.requireNonNull(block.getRegistryName());
            String translationKey = block.getTranslationKey();
            item.setRegistryName(registryName).setTranslationKey(translationKey);
        }
        RegistryItems.registryItem(item);
        return item;
    }

}