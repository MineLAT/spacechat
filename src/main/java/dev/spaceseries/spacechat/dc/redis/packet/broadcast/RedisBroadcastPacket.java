package dev.spaceseries.spacechat.dc.redis.packet.broadcast;

import dev.spaceseries.spaceapi.lib.adventure.adventure.text.Component;
import dev.spaceseries.spacechat.dc.redis.packet.PacketType;
import dev.spaceseries.spacechat.dc.redis.packet.RedisPacket;

public class RedisBroadcastPacket extends RedisPacket {

    /**
     * The broadcast component
     */
    private Component component;

    /**
     * The identifier of the server that the chat message is from
     */
    private String serverIdentifier;

    /**
     * Construct redis broadcast packet
     *
     * @param serverIdentifier server identifier
     * @param component        component
     */
    public RedisBroadcastPacket(String serverIdentifier, Component component) {
        super(PacketType.BROADCAST);
        this.serverIdentifier = serverIdentifier;
        this.component = component;
    }

    /**
     * Returns component
     *
     * @return component
     */
    public Component getComponent() {
        return component;
    }

    /**
     * Sets component
     *
     * @param component component
     */
    public void setComponent(Component component) {
        this.component = component;
    }

    /**
     * Returns server identifier
     *
     * @return identifier
     */
    public String getServerIdentifier() {
        return serverIdentifier;
    }

    /**
     * Set server identifier
     *
     * @param serverIdentifier identifier
     */
    public void setServerIdentifier(String serverIdentifier) {
        this.serverIdentifier = serverIdentifier;
    }
}
