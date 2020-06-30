package com.yakovliam.spacechat.builder.extra;

import com.yakovliam.spaceapi.config.impl.Configuration;
import com.yakovliam.spacechat.builder.IBuilder;
import com.yakovliam.spacechat.model.action.ClickAction;
import com.yakovliam.spacechat.model.action.ClickActionType;
import org.bukkit.Bukkit;

import java.util.logging.Level;

public class ClickActionBuilder implements IBuilder<Configuration, ClickAction> {

    /**
     * Builds an V (output) from a K (input)
     *
     * @param input The input
     */
    @Override
    public ClickAction build(Configuration input) {
        // create object
        ClickAction clickAction = new ClickAction();

        // get the action type
        String actionTypeString = input.getString("action");
        ClickActionType clickActionType = null;

        try {
            // get action type from string
            clickActionType = ClickActionType.valueOf(actionTypeString.toUpperCase());
        } catch (Exception ignored) {
            Bukkit.getLogger().log(Level.SEVERE, "Error while parsing Extra " + input.getName() + ": " + actionTypeString + " is not a valid extra action");
        }

        // set action type
        clickAction.setClickActionType(clickActionType);

        // get value
        String value = input.getString("value");

        // set value
        clickAction.setValue(value);

        // return
        return clickAction;
    }
}
