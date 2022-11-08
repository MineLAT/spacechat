package dev.spaceseries.spacechat.sync.redis.stream.packet.message;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.lang.reflect.Type;

public class RedisMessageSerializer implements JsonSerializer<RedisMessagePacket> {


    @Override
    public JsonElement serialize(RedisMessagePacket src, Type typeOfSrc, JsonSerializationContext context) {
        // create json element
        JsonObject element = new JsonObject();

        // add properties
        element.addProperty("senderUUID", src.getSender().toString());
        element.addProperty("senderName", src.getSenderName());
        element.addProperty("receiverName", src.getReceiverName());
        element.addProperty("channel", src.getChannel() == null ? null : src.getChannel().getHandle());
        element.addProperty("serverIdentifier", src.getServerIdentifier());
        element.addProperty("serverDisplayName", src.getServerDisplayName());
        element.addProperty("component", GsonComponentSerializer.gson().serialize(src.getComponent()));

        return element;
    }
}
