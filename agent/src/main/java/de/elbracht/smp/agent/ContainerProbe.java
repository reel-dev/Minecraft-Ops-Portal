package de.elbracht.smp.agent;

public interface ContainerProbe {
    record State(String status, boolean running, boolean paused, boolean restarting, boolean dead,
                 boolean targetMatches, boolean portMatches) {}
    State inspect() throws Exception;
}
