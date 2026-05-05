plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter parameters {
    replacements.string(eval(current.version, ">=1.20"), "mc_imports") {
        replace("net.minecraft.item.ItemStack;", "net.minecraft.world.item.ItemStack;")
        replace("net.minecraft.item.Item;", "net.minecraft.world.item.Item;")
        replace("net.minecraft.nbt.NBTTagCompound;", "net.minecraft.nbt.CompoundTag;")
        replace("net.minecraft.nbt.NBTTagList;", "net.minecraft.nbt.ListTag;")
        replace("net.minecraft.nbt.NBTTagLong;", "net.minecraft.nbt.LongTag;")
        replace("net.minecraft.tileentity.TileEntity;", "net.minecraft.world.level.block.entity.BlockEntity;")
        replace("net.minecraft.util.math.BlockPos;", "net.minecraft.core.BlockPos;")
        replace("net.minecraft.util.math.ChunkPos;", "net.minecraft.world.level.ChunkPos;")
        replace("net.minecraft.util.ResourceLocation;", "net.minecraft.resources.ResourceLocation;")
        replace("net.minecraftforge.fml.common.eventhandler.Cancelable;", "net.minecraftforge.eventbus.api.Cancelable;")
        replace("net.minecraftforge.fml.common.eventhandler.Event;", "net.minecraftforge.eventbus.api.Event;")
        replace("net.minecraft.world.World;", "net.minecraft.world.level.Level;")
        replace("net.minecraftforge.fml.common.eventhandler.SubscribeEvent;", "net.minecraftforge.eventbus.api.SubscribeEvent;")
        replace("net.minecraftforge.fml.relauncher.Side;", "net.minecraftforge.api.distmarker.Dist;")
        replace("net.minecraftforge.fml.relauncher.SideOnly;", "net.minecraftforge.api.distmarker.OnlyIn;")
        replace("net.minecraft.entity.player.EntityPlayerMP", "net.minecraft.server.level.ServerPlayer")
        replace("net.minecraft.entity.player.EntityPlayer", "net.minecraft.world.entity.player.Player")
    }

    replacements.string(eval(current.version, ">=1.21"), "neo_imports") {
        replace("net.minecraftforge.api.distmarker.Dist;", "net.neoforged.api.distmarker.Dist;")
        replace("net.minecraftforge.api.distmarker.OnlyIn;", "net.neoforged.api.distmarker.OnlyIn;")
        replace("net.minecraftforge.client.event.InputEvent;", "net.neoforged.neoforge.client.event.InputEvent;")
        replace("net.minecraftforge.client.event.RenderLevelStageEvent;", "net.neoforged.neoforge.client.event.RenderLevelStageEvent;")
        replace("net.minecraftforge.eventbus.api.Cancelable;", "net.neoforged.bus.api.Cancelable;")
        replace("net.minecraftforge.eventbus.api.Event;", "net.neoforged.bus.api.Event;")
        replace("net.minecraftforge.eventbus.api.SubscribeEvent;", "net.neoforged.bus.api.SubscribeEvent;")
    }
}

stonecutter.active("1.12.2")
