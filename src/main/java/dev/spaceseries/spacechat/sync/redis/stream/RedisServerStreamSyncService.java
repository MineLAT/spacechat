package dev.spaceseries.spacechat.sync.redis.stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.spaceseries.spacechat.SpaceChatPlugin;
import dev.spaceseries.spacechat.chat.ChatManager;
import dev.spaceseries.spacechat.sync.ServerStreamSyncService;
import dev.spaceseries.spacechat.sync.ServerSyncServiceManager;
import dev.spaceseries.spacechat.sync.packet.ReceiveStreamDataPacket;
import dev.spaceseries.spacechat.sync.packet.SendStreamDataPacket;
import dev.spaceseries.spacechat.sync.packet.StreamDataPacket;
import dev.spaceseries.spacechat.sync.provider.redis.RedisProvider;
import dev.spaceseries.spacechat.sync.redis.stream.packet.RedisPublishDataPacket;
import dev.spaceseries.spacechat.sync.redis.stream.packet.RedisStringReceiveDataPacket;
import dev.spaceseries.spacechat.sync.redis.stream.packet.broadcast.RedisBroadcastPacket;
import dev.spaceseries.spacechat.sync.redis.stream.packet.broadcast.RedisBroadcastPacketDeserializer;
import dev.spaceseries.spacechat.sync.redis.stream.packet.broadcast.RedisBroadcastPacketSerializer;
import dev.spaceseries.spacechat.sync.redis.stream.packet.chat.RedisChatPacket;
import dev.spaceseries.spacechat.sync.redis.stream.packet.chat.RedisChatPacketDeserializer;
import dev.spaceseries.spacechat.sync.redis.stream.packet.chat.RedisChatPacketSerializer;
import dev.spaceseries.spacechat.sync.redis.stream.packet.message.RedisMessageDeserializer;
import dev.spaceseries.spacechat.sync.redis.stream.packet.message.RedisMessagePacket;
import dev.spaceseries.spacechat.sync.redis.stream.packet.message.RedisMessageSerializer;
import org.bukkit.Bukkit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import static dev.spaceseries.spacechat.config.SpaceChatConfigKeys.*;

public class RedisServerStreamSyncService extends ServerStreamSyncService {

    /**
     * Redis provider
     */
    private RedisProvider provider;

    /**
     * Redis Messenger
     * <p>
     * This class does all of the actual work and connection management
     */
    private Messenger messenger;

    private boolean messengerEnabled = true;

    /**
     * Gson
     * <p>
     * Responsible for serializing and deserializing messages
     */
    private Gson gson;

    /**
     * Chat manager
     */
    private final ChatManager chatManager;

    /**
     * Construct server sync service
     *
     * @param plugin         plugin
     * @param serviceManager service manager
     */
    public RedisServerStreamSyncService(SpaceChatPlugin plugin, ServerSyncServiceManager serviceManager) {
        super(plugin, serviceManager);
        this.chatManager = plugin.getChatManager();
    }

    /**
     * Publishes a chat message across the server
     *
     * @param packet packet
     */
    @Override
    public void publishChat(SendStreamDataPacket<?> packet) {
        RedisChatPacket redisChatPacket = (RedisChatPacket) packet;

        // gson-ify the redis message
        String json = gson.toJson(redisChatPacket, RedisChatPacket.class);

        // publish to redis
        messenger.publish(new RedisPublishDataPacket(REDIS_CHAT_CHANNEL.get(plugin.getSpaceChatConfig().getAdapter()), json));
    }

    /**
     * Publishes a player message across the server
     *
     * @param packet packet
     */
    @Override
    public void publishMessage(SendStreamDataPacket<?> packet) {
        RedisMessagePacket redisMessagePacket = (RedisMessagePacket) packet;

        // gson-ify the redis message
        String json = gson.toJson(redisMessagePacket, RedisMessagePacket.class);

        //publish to redis
        messenger.publish(new RedisPublishDataPacket(REDIS_MESSAGE_CHANNEL.get(plugin.getSpaceChatConfig().getAdapter()), json));
    }

    /**
     * Publishes a broadcast message across the server
     *
     * @param packet packet
     */
    @Override
    public void publishBroadcast(SendStreamDataPacket<?> packet) {
        RedisBroadcastPacket redisBroadcastPacket = (RedisBroadcastPacket) packet;

        // gson-ify the redis message
        String json = gson.toJson(redisBroadcastPacket, RedisBroadcastPacket.class);

        // publish to redis
        messenger.publish(new RedisPublishDataPacket(
                REDIS_BROADCAST_CHANNEL.get(plugin.getSpaceChatConfig().getAdapter()), json));
    }

    /**
     * Published a list of players across the server
     *
     * @param players players
     */
    @Override
    public void publishPlayerList(Set<String> players) {

        // gson-ify the list
        String json = gson.toJson(players);

        // publish to redis
        messenger.publish(new RedisPublishDataPacket(
                REDIS_ONLINE_PLAYERS_CHANNEL.get(plugin.getSpaceChatConfig().getAdapter()), json));
    }


    /**
     * Receives an incoming chat message
     *
     * @param packet packet
     */
    @Override
    public void receiveChat(ReceiveStreamDataPacket<?> packet) {
        RedisStringReceiveDataPacket redisStringReceiveDataPacket = (RedisStringReceiveDataPacket) packet;
        // deserialize
        RedisChatPacket chatPacket = gson.fromJson(redisStringReceiveDataPacket.getData(), RedisChatPacket.class);

        // if the message is from ourselves, then return
        if (chatPacket.getServerIdentifier().equalsIgnoreCase(REDIS_SERVER_IDENTIFIER.get(plugin.getSpaceChatConfig().getAdapter()))) {
            return;
        }

        // if channel exists, send through that instead
        if (chatPacket.getChannel() != null && plugin.getChannelManager().get(chatPacket.getChannel().getHandle()) != null) {
            chatManager.sendComponentChannelMessage(null, chatPacket.getComponent(), chatPacket.getChannel());
            return;
        }

        // send to all players
        chatManager.sendComponentChatMessage(chatPacket.getComponent());
    }

    /**
     * Receives an incoming player message
     *
     * @param packet packet
     */
    @Override
    public void receiveMessage(ReceiveStreamDataPacket<?> packet) {
        RedisStringReceiveDataPacket redisStringReceiveDataPacket = (RedisStringReceiveDataPacket) packet;
        // deserialize
        RedisMessagePacket messagePacket = gson.fromJson(redisStringReceiveDataPacket.getData(), RedisMessagePacket.class);

        // if the message is from ourselves, then return
        if (messagePacket.getServerIdentifier().equalsIgnoreCase(REDIS_SERVER_IDENTIFIER.get(plugin.getSpaceChatConfig().getAdapter()))) {
            return;
        }

        // if channel exists, send through that instead
        if (messagePacket.getChannel() != null && plugin.getChannelManager().get(messagePacket.getChannel().getHandle()) != null) {
            chatManager.sendComponentChannelMessage(null, messagePacket.getComponent(), messagePacket.getChannel());
            return;
        }

        // put replier in map
        plugin.getUserManager().getReplyTargetMap().put(messagePacket.getReceiverName(), messagePacket.getSenderName());

        // send to player
        chatManager.sendComponentChatMessage(messagePacket.getComponent(), Bukkit.getPlayer(messagePacket.getReceiverName()));
    }

    /**
     * Receives an incoming list of players
     *
     * @param packet packet
     */
    @Override
    public void receivePlayerList(ReceiveStreamDataPacket<?> packet) {
        RedisStringReceiveDataPacket redisStringReceiveDataPacket = (RedisStringReceiveDataPacket) packet;

        Set<String> players = gson.fromJson(redisStringReceiveDataPacket.getData(), Set.class);

        //Update the online players
        plugin.getUserManager().setOnlinePlayers(players);
    }

    /**
     * Receives an incoming chat message
     *
     * @param packet packet
     */
    @Override
    public void receiveBroadcast(ReceiveStreamDataPacket<?> packet) {
        RedisStringReceiveDataPacket stringReceiveDataPacket = (RedisStringReceiveDataPacket) packet;

        // deserialize
        RedisBroadcastPacket broadcastPacket = gson.fromJson(stringReceiveDataPacket.getData(), RedisBroadcastPacket.class);

        // if the message is from ourselves, then return
        if (broadcastPacket.getServerIdentifier().equalsIgnoreCase(REDIS_SERVER_IDENTIFIER.get(plugin.getSpaceChatConfig().getAdapter()))) {
            return;
        }

        // send to all players
        chatManager.sendComponentMessage(broadcastPacket.getComponent());
    }

    /**
     * Starts the service in question
     */
    @Override
    public void start() {
        // initialize provider
        this.provider = getServiceManager().getRedisProvider();
        // initialize messenger
        this.messenger = new Messenger();
        // It's alive!
        alive();
        // initialize my super-duper gson adapter
        gson = new GsonBuilder()
                .registerTypeAdapter(RedisChatPacket.class, new RedisChatPacketSerializer())
                .registerTypeAdapter(RedisChatPacket.class, new RedisChatPacketDeserializer(plugin))
                .registerTypeAdapter(RedisBroadcastPacket.class, new RedisBroadcastPacketSerializer())
                .registerTypeAdapter(RedisBroadcastPacket.class, new RedisBroadcastPacketDeserializer())
                .registerTypeAdapter(RedisMessagePacket.class, new RedisMessageDeserializer(plugin))
                .registerTypeAdapter(RedisMessagePacket.class, new RedisMessageSerializer())
                .create();
    }

    private void alive() {
        // subscribing to redis pub/sub is a blocking operation.
        // we need to make a new thread in order to not block the main thread....
        new Thread(() -> {
            boolean reconnected = false;
            while (messengerEnabled && !Thread.interrupted() && provider.provide() != null && !provider.provide().isClosed()) {
                try (Jedis jedis = provider.provide().getResource()) {
                    if (reconnected) {
                        plugin.getLogger().log(Level.INFO, "Redis connection is alive again");
                    }
                    // Lock the thread
                    jedis.subscribe(messenger,
                            REDIS_CHAT_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter()),
                            REDIS_BROADCAST_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter()),
                            REDIS_MESSAGE_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter()),
                            REDIS_ONLINE_PLAYERS_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter())
                    );
                } catch (Throwable t) {
                    // Thread was unlocked
                    if (messengerEnabled) {
                        plugin.getLogger().log(Level.WARNING, "Redis connection dropped, automatic reconnection in 8 seconds...");
                        t.printStackTrace();
                        try {
                            messenger.unsubscribe(
                                    REDIS_CHAT_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter()),
                                    REDIS_BROADCAST_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter()),
                                    REDIS_MESSAGE_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter()),
                                    REDIS_ONLINE_PLAYERS_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter())
                            );
                        } catch (Throwable ignored) { }

                        // Make an instant subscribe if occurs any error on initialization
                        if (!reconnected) {
                            reconnected = true;
                        } else {
                            try {
                                Thread.sleep(8000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    } else {
                        return;
                    }
                }
            }
        }).start();
    }

    /**
     * Ends the service in question
     */
    @Override
    public void end() {
        messengerEnabled = false;
        if (this.provider.provide() != null && this.provider.provide().getResource().getClient() != null) {
            // unsubscribe from chat channel
            messenger.unsubscribe(
                    REDIS_CHAT_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter()),
                    REDIS_BROADCAST_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter()),
                    REDIS_MESSAGE_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter()),
                    REDIS_ONLINE_PLAYERS_CHANNEL.get(this.plugin.getSpaceChatConfig().getAdapter())
            );

            if (!provider.provide().isClosed()) {
                provider.provide().close();
            }
        }
    }

    private class Messenger extends JedisPubSub {

        @Override
        public void onMessage(String channel, String message) {
            // receiving
            // [channel] sent [message]

            // if it's the correct channel
            if (channel.equalsIgnoreCase(REDIS_CHAT_CHANNEL.get(plugin.getSpaceChatConfig().getAdapter()))) {
                receiveChat(new RedisStringReceiveDataPacket(message));
            } else if (channel.equalsIgnoreCase(REDIS_BROADCAST_CHANNEL.get(plugin.getSpaceChatConfig().getAdapter()))) {
                receiveBroadcast(new RedisStringReceiveDataPacket(message));
            } else if(channel.equalsIgnoreCase(REDIS_MESSAGE_CHANNEL.get(plugin.getSpaceChatConfig().getAdapter()))){
                receiveMessage(new RedisStringReceiveDataPacket(message));
            } else if (channel.equalsIgnoreCase(REDIS_ONLINE_PLAYERS_CHANNEL.get(plugin.getSpaceChatConfig().getAdapter()))){
                receivePlayerList(new RedisStringReceiveDataPacket(message));
            }
        }

        @Override
        public void onSubscribe(String channel, int subscribedChannels) {
            // we have subscribed to [channel]. We are currently subscribed to [subscribedChannels] channels.
            plugin.getLogger().log(Level.INFO, "SpaceChat subscribed to the redis channel '" + channel + "'");
        }

        @Override
        public void onUnsubscribe(String channel, int subscribedChannels) {
            // we have unsubscribed from [channel]. We are currently subscribed to another [subscribedChannels] channels.
            plugin.getLogger().log(Level.INFO, "SpaceChat unsubscribed from the redis channel '" + channel + "'");
        }

        /**
         * Publish a message
         *
         * @param dataPacket packet
         */
        public void publish(StreamDataPacket dataPacket) {
            RedisPublishDataPacket redisPublishDataPacket = (RedisPublishDataPacket) dataPacket;

            String channel = redisPublishDataPacket.getChannel();
            String message = redisPublishDataPacket.getMessage();
            // run async
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                provider.consumer(jedis -> jedis.publish(channel, message)).run();
            });
        }
    }
}
