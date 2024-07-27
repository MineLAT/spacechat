package dev.spaceseries.spacechat.builder.live;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import dev.spaceseries.spacechat.SpaceChatPlugin;
import dev.spaceseries.spacechat.model.formatting.Extra;
import dev.spaceseries.spacechat.model.formatting.Format;
import dev.spaceseries.spacechat.parser.MessageParser;
import dev.spaceseries.spacechat.replacer.AmpersandReplacer;
import dev.spaceseries.spacechat.replacer.SectionReplacer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import static dev.spaceseries.spacechat.config.SpaceChatConfigKeys.PERMISSIONS_USE_CHAT_COLORS;

public class RelationalLiveChatFormatBuilder extends LiveChatFormatBuilder {

    /**
     * Ampersand replacer
     */
    private static final AmpersandReplacer AMPERSAND_REPLACER = new AmpersandReplacer();

    /**
     * Section replacer
     */
    private static final SectionReplacer SECTION_REPLACER = new SectionReplacer();

    public RelationalLiveChatFormatBuilder(SpaceChatPlugin plugin) {
        super(plugin);
    }

    /**
     * Builds an array of baseComponents from a message, player, and format
     *
     * @param fromPlayer    the player that send the message
     * @param toPlayer      the player that will receive the message
     * @param messageString the message as string
     * @param format        the format to parse the provided player and message
     * @return              a text component using provided arguments
     */
    public TextComponent build(Player fromPlayer, Player toPlayer, String messageString, Format format) {
        // create component builder for message
        ComponentBuilder<TextComponent, TextComponent.Builder> componentBuilder = Component.text();

        // loop through format parts
        format.getFormatParts().forEach(formatPart -> {
            // create component builder
            ComponentBuilder<TextComponent, TextComponent.Builder> partComponentBuilder = Component.text();
            // if the part has "line", it is a SINGLE MiniMessage...in that case, just parse & return (continues to next part if exists, which it shouldn't)
            if (formatPart.getLine() != null) {
                // replace placeholders
                String mmWithPlaceholdersReplaced = SECTION_REPLACER.apply(PlaceholderAPI.setPlaceholders(fromPlayer, AMPERSAND_REPLACER.apply(formatPart.getLine(), fromPlayer)), fromPlayer);
                // replace relational and apply section replacer
                mmWithPlaceholdersReplaced = SECTION_REPLACER.apply(PlaceholderAPI.setRelationalPlaceholders(fromPlayer, toPlayer, mmWithPlaceholdersReplaced), fromPlayer);

                // get chat message (formatted)
                String chatMessage = LegacyComponentSerializer
                        .legacySection()
                        .serialize(fromPlayer.hasPermission(PERMISSIONS_USE_CHAT_COLORS.get(plugin.getSpaceChatConfig().getAdapter())) ? // if player has permission to use chat colors
                                LegacyComponentSerializer // yes, the player has permission to use chat colors, so color message
                                        .legacyAmpersand()
                                        .deserialize(messageString) :
                                Component.text(messageString)); // no, the player doesn't have permission to use chat colors, so just return the message (not colored)

                // parse message
                Component message = new MessageParser(plugin).parse(fromPlayer, Component.text(chatMessage));

                // parse miniMessage
                Component parsedMiniMessage = MiniMessage.miniMessage().deserialize(mmWithPlaceholdersReplaced);

                // replace chat message
                parsedMiniMessage = parsedMiniMessage.replaceText((text) -> text.match("<chat_message>").replacement(message));

                // parse MiniMessage into builder
                partComponentBuilder.append(parsedMiniMessage);
                // append partComponentBuilder to main builder
                componentBuilder.append(partComponentBuilder.build());
                return;
            }

            String text = formatPart.getText();

            // basically what I am doing here is converting & -> section, then replacing placeholders, then section -> &
            // this just bypasses PAPI's hacky way of coloring text which shouldn't even be implemented...
            text = SECTION_REPLACER.apply(PlaceholderAPI.setPlaceholders(fromPlayer, AMPERSAND_REPLACER.apply(text, fromPlayer)), fromPlayer);
            // set relational placeholders
            text = SECTION_REPLACER.apply(PlaceholderAPI.setRelationalPlaceholders(fromPlayer, toPlayer, text), fromPlayer);


            // build text from legacy (and replace <chat_message> with the actual message)
            // and check permissions for chat colors
            Component parsedText = fromPlayer.hasPermission(PERMISSIONS_USE_CHAT_COLORS.get(plugin.getSpaceChatConfig().getAdapter())) ? LegacyComponentSerializer.legacyAmpersand().deserialize(
                    text.replace("<chat_message>", messageString)) :
                    LegacyComponentSerializer.legacyAmpersand().deserialize(text).replaceText((b) -> b.match("<chat_message>")
                            .replacement(messageString));

            // parse message
            parsedText = new MessageParser(plugin).parse(fromPlayer, parsedText);

            /* Retaining events for MULTIPLE components */

            // parse extra (if applicable)
            if (formatPart.getExtra() != null) {
                Extra extra = formatPart.getExtra();

                // if contains click action
                if (extra.getClickAction() != null) {
                    // apply
                    parsedText = parsedText.clickEvent(extra.getClickAction().toClickEventRelational(fromPlayer, toPlayer));
                }

                // if contains hover action
                if (extra.getHoverAction() != null) {
                    // apply
                    parsedText = parsedText.hoverEvent(extra.getHoverAction().toHoverEventRelational(fromPlayer, toPlayer));
                }
            }

            partComponentBuilder.append(parsedText);

            // append build partComponentBuilder to main componentBuilder
            componentBuilder.append(partComponentBuilder.build());
        });

        // return built component builder
        return componentBuilder.build();
    }
}
