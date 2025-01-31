package dev.spaceseries.spacechat.parser.itemchat;

import com.saicone.ezlib.Dependencies;
import com.saicone.ezlib.Dependency;
import com.saicone.ezlib.Repository;
import dev.spaceseries.spacechat.SpaceChatPlugin;
import dev.spaceseries.spacechat.api.config.generic.adapter.ConfigurationAdapter;
import dev.spaceseries.spacechat.config.SpaceChatConfigKeys;
import dev.spaceseries.spacechat.parser.Parser;
import me.pikamug.localelib.LocaleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

@Dependencies({
        @Dependency(value = "me.pikamug.localelib:LocaleLib:@release",
                repository = @Repository(url = "https://repo.codemc.io/repository/maven-public"),
                relocate = {"me.pikamug.localelib", "{package}.lib.localelib"}
        ),
        @Dependency(value = "com.saicone.rtag:rtag-item:1.5.9",
                repository = @Repository(url = "https://jitpack.io"),
                relocate = {"com.saicone.rtag", "{package}.lib.rtag"}
        )
})
public class ItemChatParser extends Parser {

    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    /**
     * Locale manager
     */
    private static final LocaleManager LOCALE_MANAGER = new LocaleManager();
    private static final Map<String, String> FALLBACK_NAMES = new HashMap<>();

    /**
     * Configuration
     */
    private final ConfigurationAdapter configuration;

    /**
     * Item Chat parser
     *
     * @param plugin plugin
     */
    public ItemChatParser(SpaceChatPlugin plugin) {
        super(plugin);
        this.configuration = plugin.getSpaceChatConfig().getAdapter();
    }

    @Override
    public Component parse(Player player, Component message) {
        // if not enabled, return
        if (!SpaceChatConfigKeys.ITEM_CHAT_ENABLED.get(configuration)) {
            return message;
        }

        if (!player.hasPermission(SpaceChatConfigKeys.PERMISSIONS_USE_ITEM_CHAT.get(configuration))) {
            return message;
        }

        boolean containsItemChatAliases = false;
        for (String s : SpaceChatConfigKeys.ITEM_CHAT_REPLACE_ALIASES.get(configuration)) {
            if (componentContains(message, s)) {
                containsItemChatAliases = true;
            }
        }

        if (!containsItemChatAliases) {
            return message;
        }

        final long cooldown = SpaceChatConfigKeys.ITEM_CHAT_COOLDOWN.get(configuration);
        if (cooldown > 0) {
            final long currentTime = System.currentTimeMillis();
            if (COOLDOWN.getOrDefault(player.getUniqueId(), 0L) > currentTime) {
                return message;
            }
            COOLDOWN.put(player.getUniqueId(), currentTime + cooldown);
        }

        // get item in hand
        ItemStack itemStack = player.getItemInHand();

        // if null or air, return
        if (itemStack.getType().equals(Material.AIR) || itemStack.getAmount() <= 0) {
            return message;
        }

        // get item key
        String itemKey = LOCALE_MANAGER.queryMaterial(itemStack.getType(), itemStack.getDurability(), itemStack.getItemMeta());
        String fallback = FALLBACK_NAMES.get(itemKey);
        if (fallback == null) {
            final String[] split = itemStack.getType().name().split("_");
            for (int i = 0; i < split.length; i++) {
                final String s = split[i];
                split[i] = s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
            }
            fallback = String.join(" ", split);
            FALLBACK_NAMES.put(itemKey, fallback);
        }

        // get display name
        Component name = itemStack.hasItemMeta() ?
                Objects.requireNonNull(itemStack.getItemMeta()).hasDisplayName() ? LegacyComponentSerializer.legacySection().deserialize(itemStack.getItemMeta().getDisplayName()) : Component.translatable(itemKey, fallback) :
                Component.translatable(itemKey, fallback);

        // replacement config for %item% and %amount%
        TextReplacementConfig nameReplacementConfig = TextReplacementConfig.builder()
                .matchLiteral("%name%").replacement(name)
                .build();

        TextReplacementConfig amountReplacementConfig = TextReplacementConfig.builder()
                .matchLiteral("%amount%")
                .replacement(Integer.toString(itemStack.getAmount()))
                .build();

        // convert lore (if exists)
        TextComponent.Builder loreBuilder = null;

        // if using custom lore, use that instead
        if (SpaceChatConfigKeys.ITEM_CHAT_WITH_LORE_USE_CUSTOM.get(configuration)) {
            List<String> lore = SpaceChatConfigKeys.ITEM_CHAT_WITH_LORE_CUSTOM.get(configuration);
            loreBuilder = Component.text();

            for (Iterator<String> it = lore.iterator(); it.hasNext(); ) {
                loreBuilder.append(LegacyComponentSerializer.legacyAmpersand().deserialize(it.next())
                        .replaceText(nameReplacementConfig)
                        .replaceText(amountReplacementConfig));
                if (it.hasNext()) {
                    loreBuilder.append(Component.newline());
                }
            }
        }

        // create a new component for the ACTUAL item message replacement (e.g. [Enchanted Sword x1]
        Component itemMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(SpaceChatConfigKeys.ITEM_CHAT_WITH_CHAT.get(configuration))
                .replaceText(nameReplacementConfig)
                .replaceText(amountReplacementConfig)
                // remove all decoration from parent components above
                .decoration(TextDecoration.BOLD, TextDecoration.State.NOT_SET)
                .decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET)
                .decoration(TextDecoration.OBFUSCATED, TextDecoration.State.NOT_SET)
                .decoration(TextDecoration.STRIKETHROUGH, TextDecoration.State.NOT_SET)
                .decoration(TextDecoration.UNDERLINED, TextDecoration.State.NOT_SET);

        // create hover event
        HoverEvent<?> hoverEvent;

        if (loreBuilder != null) {
            hoverEvent = HoverEvent.showText(loreBuilder.build());
        } else {
            // show item
            hoverEvent = itemStack.asHoverEvent();
        }

        itemMessage = itemMessage.hoverEvent(hoverEvent);

        Component finalItemMessage = itemMessage;

        // replace [item] (and other aliases) with the item message

        // keep track of the count
        for (String s : SpaceChatConfigKeys.ITEM_CHAT_REPLACE_ALIASES.get(configuration)) {
            message = message.replaceText(b -> {
                if (SpaceChatConfigKeys.ITEM_CHAT_MAX_PER_MESSAGE.get(configuration) != -1) {
                    b.times(SpaceChatConfigKeys.ITEM_CHAT_MAX_PER_MESSAGE.get(configuration)).matchLiteral(s).replacement(finalItemMessage);
                } else {
                    b.matchLiteral(s).replacement(finalItemMessage);
                }
            });
        }

        return message;
    }

    /**
     * Check if component contains text
     *
     * @param component Component
     * @param s text
     * @return true if contains
     */
    public boolean componentContains(Component component, String s) {
        if (component instanceof TextComponent && ((TextComponent) component).content().contains(s)) {
            return true;
        }
        for (Component child : component.children()) {
            if (componentContains(child, s)) {
                return true;
            }
        }
        return false;
    }
}