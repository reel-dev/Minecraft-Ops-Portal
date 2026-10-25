package de.elbracht.smp.agent;

public interface MinecraftProbe {
    record Result(String minecraftVersion, Integer playersOnline) {}
    Result query() throws Exception;
}
