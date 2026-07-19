package net.camacraft.judgemod.menu;

import net.camacraft.judgemod.JudgeMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, JudgeMod.MODID);

    public static final RegistryObject<MenuType<TribunalMenu>> TRIBUNAL_MENU =
            MENUS.register("tribunal_menu", () -> IForgeMenuType.create((IContainerFactory<TribunalMenu>) (windowId, inv, data) -> new TribunalMenu(windowId, inv, data)));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
