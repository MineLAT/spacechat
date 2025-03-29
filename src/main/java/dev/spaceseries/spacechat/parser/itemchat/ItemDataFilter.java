/*
 * This file is part of LightItems, licensed under the MIT License
 *
 * Copyright (c) 2025 Rubenicos
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.spaceseries.spacechat.parser.itemchat;

import com.saicone.rtag.item.ItemObject;
import com.saicone.rtag.tag.TagBase;
import com.saicone.rtag.tag.TagCompound;
import com.saicone.rtag.tag.TagList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ItemDataFilter {

    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;

    @NotNull
    public static ItemStack filterItem(@NotNull ItemStack item, @NotNull DataPath comparator) {
        Object tag = ItemObject.getCustomDataTag(ItemObject.asNMSCopy(item));
        if (tag == null) {
            return item;
        }
        if (!filter(tag, comparator)) {
            return new ItemStack(item.getType(), item.getAmount());
        }
        final Object result = ItemObject.asNMSCopy(new ItemStack(item.getType(), item.getAmount()));
        ItemObject.setCustomDataTag(result, tag);
        return ItemObject.asCraftMirror(result);
    }

    @NotNull
    public static Object filterCompound(@NotNull Object compound, @NotNull DataPath comparator) {
        final Object tag = TagCompound.get(compound, "tag");
        if (tag != null) {
            filter(tag, comparator);
        }
        return compound;
    }

    public static boolean filter(@NotNull Object tag, @NotNull DataPath comparator) {
        if (comparator == DataPath.EMPTY) {
            return true;
        } else if (comparator == DataPath.RESET) {
            if (TagBase.getTypeId(tag) == TAG_LIST && TagList.getType(tag) == TAG_COMPOUND) {
                for (Object compound : TagList.getValue(tag)) {
                    filterCompound(compound, comparator);
                }
            }
        } else if (TagBase.getTypeId(tag) == TAG_COMPOUND) {
            final Map<String, Object> map = TagCompound.getValue(tag);
            map.entrySet().removeIf(entry -> {
                final DataPath children = comparator.get(entry.getKey());
                if (children == null) {
                    return true;
                }
                return !filter(entry.getValue(), children);
            });
            return !map.isEmpty();
        }
        return true;
    }
}
