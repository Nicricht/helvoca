package cl.helvoca.ai.realtime;

import cl.helvoca.agent.AiCapability;

import java.util.Set;

public record RealtimeAgentRuntimeConfig(
        String name,
        String language,
        String voice,
        String greeting,
        String instructions,
        boolean active,
        Set<AiCapability> capabilities) {}
