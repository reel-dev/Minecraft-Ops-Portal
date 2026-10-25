package de.elbracht.smp.agent;

public interface DockerControl {
    void verifyTarget() throws Exception;
    void start() throws Exception;
    void stop() throws Exception;
}
