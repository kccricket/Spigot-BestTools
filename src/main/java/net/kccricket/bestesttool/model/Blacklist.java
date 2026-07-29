package net.kccricket.bestesttool.model;

import net.kccricket.bestesttool.text.MessageUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class Blacklist {

    public List<Material> mats;

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

    public void add(String string) {
        Material mat = Material.getMaterial(string);
        if(mat!=null) mats.add(mat);
    }

    public void add(Material mat) {
        mats.add(mat);
    }

    public boolean contains(Material mat) {
        return mats.contains(mat);
    }

    public void remove(Material mat) {
        if(mats.contains(mat)) mats.remove(mat);
    }

    public List<String> toStringList() {
        ArrayList<String> list = new ArrayList<>();

        for(Material mat : mats) {
            list.add(mat.name());
        }
        return list;
    }

    public void print(Player p) {

        if(mats.size()==0) {
            MessageUtil.send(p, "blacklistEmpty");
            return;
        }

        p.sendMessage(MessageUtil.get(p, "blacklistTitle"));

        for(Material mat : mats) {
            Component link = createLink("[X] ","/bestesttool blacklist remove "+mat.name());
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
