package dev.molang.iamzombieq;
import dev.molang.iamzombieq.util.ModIds;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class IAmZombieItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IAmZombieMod.MOD_ID);
    private static final Identifier OP_BLOCKS_TAB = Identifier.fromNamespaceAndPath("minecraft", "op_blocks");

    public static final DeferredItem<BlockItem> COFFIN = ITEMS.registerSimpleBlockItem(IAmZombieBlocks.COFFIN);

    public static final DeferredItem<Item> SUPER_ROTTEN_FLESH = ITEMS.registerSimpleItem(
            "super_rotten_flesh",
            properties -> properties.food(new FoodProperties.Builder()
                    .alwaysEdible()
                    .nutrition(8)
                    .saturationModifier(1.2f)
                    .build())
    );

    // The Herobrine head item now places the floor/wall head blocks (StandingAndWallBlockItem) the vanilla-skull
    // way, while staying equippable on the head (its sun-block protection) and unbreakable. Registry id, the
    // "item.iamzombieq.herobrine_head" translation, the sun-block hook (headStack.is(HEROBRINE_HEAD.get())) and
    // the creative-tab entries are all preserved — StandingAndWallBlockItem is still an Item.
    public static final DeferredItem<StandingAndWallBlockItem> HEROBRINE_HEAD = ITEMS.registerItem(
            "herobrine_head",
            properties -> new StandingAndWallBlockItem(
                    IAmZombieBlocks.HEROBRINE_HEAD.get(),
                    IAmZombieBlocks.HEROBRINE_WALL_HEAD.get(),
                    Direction.DOWN,
                    properties),
            () -> new Item.Properties()
                    .equippable(EquipmentSlot.HEAD)
                    .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
    );

    public static final DeferredItem<Item> DISGUISE_MASK = ITEMS.registerSimpleItem(
            "disguise_mask",
            properties -> properties
                    .durability(15)
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                            .setAsset(ResourceKey.create(
                                    EquipmentAssets.ROOT_ID,
                                    ModIds.id("disguise_mask")))
                            .build())
    );

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IAmZombieMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> IAMZOMBIEQ_TAB = CREATIVE_TABS.register(
            "iamzombieq_tab",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.iamzombieq.iamzombieq_tab"))
                    .icon(() -> new ItemStack(SUPER_ROTTEN_FLESH.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SUPER_ROTTEN_FLESH.get());
                        output.accept(DISGUISE_MASK.get());
                        output.accept(HEROBRINE_HEAD.get());
                        output.accept(COFFIN.get());
                    })
                    .build()
    );

    private IAmZombieItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(IAmZombieItems::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> tab = event.getTabKey();
        if (tab == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(COFFIN.get());
        } else if (tab == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(SUPER_ROTTEN_FLESH.get());
        } else if (tab.identifier().equals(OP_BLOCKS_TAB) && event.hasPermissions()) {
            event.accept(new ItemStack(HEROBRINE_HEAD.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
