package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.blocks.BlockCirculationShielder;
import com.circulation.circulation_networks.blocks.BlockNodePedestal;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.blocks.nodes.BlockChargingNode;
import com.circulation.circulation_networks.blocks.nodes.BlockHub;
import com.circulation.circulation_networks.blocks.nodes.BlockPortNode;
import com.circulation.circulation_networks.blocks.nodes.BlockRelayNode;
import com.circulation.circulation_networks.blocks.nodes.BlockLongDistanceRelayNode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Objects;

@SuppressWarnings("UnusedReturnValue")
public final class RegistryBlocks {

    private static final ObjectList<Block> BLOCKS_TO_REGISTER = new ObjectArrayList<>();
    private static final ObjectList<Block> BLOCK_MODELS_TO_REGISTER = new ObjectArrayList<>();

    public static BlockPortNode blockPortNode;
    public static BlockChargingNode blockChargingNode;
    public static BlockRelayNode blockRelayNode;
    public static BlockLongDistanceRelayNode blockLongDistanceRelayNode;
    public static BlockCirculationShielder blockCirculationShielder;
    public static BlockHub blockHub;
    public static BlockNodePedestal blockNodePedestal;

    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        prepareItemBlockRegister(blockPortNode = registerBlock(new BlockPortNode()));
        prepareItemBlockRegister(blockChargingNode = registerBlock(new BlockChargingNode()));
        prepareItemBlockRegister(blockRelayNode = registerBlock(new BlockRelayNode()));
        prepareItemBlockRegister(blockLongDistanceRelayNode = registerBlock(new BlockLongDistanceRelayNode()));
        prepareItemBlockRegister(blockCirculationShielder = registerBlock(new BlockCirculationShielder()));
        prepareItemBlockRegister(blockHub = registerBlock(new BlockHub()));
        prepareItemBlockRegister(blockNodePedestal = registerBlock(new BlockNodePedestal()));

        BLOCKS_TO_REGISTER.add(CFNBlocks.blockMultiblockShell = new MultiblockShellBlock());

        CFNBlocks.blockPortNode = blockPortNode;
        CFNBlocks.blockChargingNode = blockChargingNode;
        CFNBlocks.blockRelayNode = blockRelayNode;
        CFNBlocks.blockLongDistanceRelayNode = blockLongDistanceRelayNode;
        CFNBlocks.blockCirculationShielder = blockCirculationShielder;
        CFNBlocks.blockHub = blockHub;
        CFNBlocks.blockNodePedestal = blockNodePedestal;

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
