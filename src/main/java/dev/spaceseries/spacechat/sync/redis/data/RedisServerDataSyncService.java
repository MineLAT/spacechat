package dev.spaceseries.spacechat.sync.redis.data;

import dev.spaceseries.spacechat.SpaceChatPlugin;
import dev.spaceseries.spacechat.model.Channel;
import dev.spaceseries.spacechat.sync.ServerDataSyncService;
import dev.spaceseries.spacechat.sync.ServerSyncServiceManager;
import dev.spaceseries.spacechat.sync.provider.redis.RedisProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.spaceseries.spacechat.config.SpaceChatConfigKeys.*;

public class RedisServerDataSyncService extends ServerDataSyncService {

    /**
     * Provider
     */
    private final RedisProvider provider;

    /**
     * Construct server sync service
     *
     * @param serviceManager service manager
     */
    public RedisServerDataSyncService(SpaceChatPlugin plugin, ServerSyncServiceManager serviceManager) {
        super(plugin, serviceManager);

        // initialize pool
        this.provider = this.getServiceManager().getRedisProvider();
    }

    /**
     * Subscribes a player to a channel
     *
     * @param uuid    uuid
     * @param channel channel
     */
    @Override
    public void subscribeToChannel(UUID uuid, Channel channel) {
        provider.consumer((jedis) -> {
            // update

            // lpush new channel
            jedis.lpush(REDIS_PLAYER_SUBSCRIBED_CHANNELS_LIST_KEY.get(plugin.getSpaceChatConfig().getAdapter())
                    .replace("%uuid%", uuid.toString()), channel.getHandle());

            // also lpush to master channels list that contains a list of uuids for every player subscribed to that given channel
            jedis.lpush(REDIS_CHANNELS_SUBSCRIBED_UUIDS_LIST_KEY.get(plugin.getSpaceChatConfig().getAdapter())
                    .replace("%channel%", channel.getHandle()), uuid.toString());
        }).run();
    }

    /**
     * Unsubscribes a player from a channel
     *
     * @param uuid              uuid
     * @param subscribedChannel subscribed channel
     */
    @Override
    public void unsubscribeFromChannel(UUID uuid, Channel subscribedChannel) {
        provider.consumer((jedis) -> {
            // update

            // lrem channel
            jedis.lrem(REDIS_PLAYER_SUBSCRIBED_CHANNELS_LIST_KEY.get(plugin.getSpaceChatConfig().getAdapter())
                    .replace("%uuid%", uuid.toString()), 0, subscribedChannel.getHandle());

            // also lrem from master channels list that contains a list of uuids for every player subscribed to that given channel
            jedis.lrem(REDIS_CHANNELS_SUBSCRIBED_UUIDS_LIST_KEY.get(plugin.getSpaceChatConfig().getAdapter())
                    .replace("%channel%", subscribedChannel.getHandle()), 0, uuid.toString());
        }).run();
    }

    /**
     * Updates the current channel that a player is talking in
     *
     * @param uuid    uuid
     * @param channel channel
     */
    @Override
    public void updateCurrentChannel(UUID uuid, Channel channel) {
        provider.consumer((jedis) -> {
            // update key
            if (channel != null)
                jedis.set(REDIS_PLAYER_CURRENT_CHANNEL_KEY.get(plugin.getSpaceChatConfig().getAdapter())
                        .replace("%uuid%", uuid.toString()), channel.getHandle());
            else {
                // get current
                Channel current = getCurrentChannel(uuid);
                if (current != null)
                    jedis.del(REDIS_PLAYER_CURRENT_CHANNEL_KEY.get(plugin.getSpaceChatConfig().getAdapter())
                            .replace("%uuid%", uuid.toString()), current.getHandle());
            }
        }).run();
    }

    /**
     * Gets a list of subscribed channels
     *
     * @param uuid uuid
     * @return channels
     */
    @Override
    public List<Channel> getSubscribedChannels(UUID uuid) {
        return provider.function((jedis) -> {
            List<Channel> channels = jedis.lrange(REDIS_PLAYER_SUBSCRIBED_CHANNELS_LIST_KEY.get(plugin.getSpaceChatConfig().getAdapter())
                            .replace("%uuid%", uuid.toString()), 0, -1).stream()
                    .map(s -> plugin.getChannelManager().get(s, null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            jedis.close();
            return channels;
        }).or(new ArrayList<>()).get();
    }

    /**
     * Gets the current channel that a player is talking in
     *
     * @param uuid uuid
     * @return channel
     */
    @Override
    public Channel getCurrentChannel(UUID uuid) {
        return provider.function((jedis) -> {
            Channel channel = plugin.getChannelManager().get(jedis.get(REDIS_PLAYER_CURRENT_CHANNEL_KEY.get(plugin.getSpaceChatConfig().getAdapter())
                    .replace("%uuid%", uuid.toString())), null);
            jedis.close();
            return channel;
        }).get();
    }

    /**
     * Gets a list of all of the uuids of players who are currently subscribed to a given channel
     * <p>
     * This only includes the uuids of players who are currently online
     *
     * @param channel channel
     * @return uuids
     */
    @Override
    public List<UUID> getSubscribedUUIDs(Channel channel) {
        return provider.function((jedis) -> {
            // get list of uuids of players who've subscribed to a given channel
            List<String> uuids = jedis.lrange(REDIS_CHANNELS_SUBSCRIBED_UUIDS_LIST_KEY.get(plugin.getSpaceChatConfig().getAdapter())
                    .replace("%channel%", channel.getHandle()), 0, -1);
            jedis.close();
            // map and return
            return uuids.stream()
                    .map(UUID::fromString)
                    .filter(u -> {
                        Player p = Bukkit.getPlayer(u);
                        return p != null && p.isOnline();
                    })
                    .collect(Collectors.toList());
        }).get();
    }
}
