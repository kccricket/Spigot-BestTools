package net.kccricket.bestesttool;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class Blacklist {

    List<Material> mats;

    Blacklist(List<String> strings) {
        mats = new ArrayList<>();
        for(String s : strings) {
            Material mat = Material.getMaterial(s);
            if(mat!=null) mats.add(mat);
        }
    }

    Blacklist() {
        mats = new ArrayList<>();
    }

    void add(String string) {
        Material mat = Material.getMaterial(string);
        if(mat!=null) mats.add(mat);
    }

    void add(Material mat) {
        mats.add(mat);
    }

    boolean contains(Material mat) {
        return mats.contains(mat);
    }

    void remove(Material mat) {
        if(mats.contains(mat)) mats.remove(mat);
    }

    List<String> toStringList() {
        ArrayList<String> list = new ArrayList<>();

        for(Material mat : mats) {
            list.add(mat.name());
        }
        return list;
    }

    void print(Player p,Main main) {

        if(mats.size()==0) {
            Messages.sendMessage(p,main.messages.BL_EMPTY);
            return;
        }

        p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(main.getConfig().getString("blacklist-title")));

        /*p.sendMessage("This list will be nicer in the next version :P");
        p.sendMessage("Blacklist: ");
        StringBuilder slist = new StringBuilder();
        */
        for(Material mat : mats) {
            Component link = createLink("[X] ","/besttools blacklist remove "+mat.name());
            Component name = Component.text(mat.name(), NamedTextColor.GRAY);
            p.sendMessage(link.append(name));
        }
    }

    private Component createLink(String text, String link) {
        // TODO: Make color configurable
        return Component.text(text, NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(link));
    }

}
