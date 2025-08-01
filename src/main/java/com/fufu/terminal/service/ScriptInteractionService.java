package com.fufu.terminal.service;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.ScriptExecutionSession;
import com.fufu.terminal.entity.ScriptInteraction;
import com.fufu.terminal.entity.enums.InteractionType;
import com.fufu.terminal.entity.interaction.InteractionRequest;
import com.fufu.terminal.entity.interaction.InteractionResponse;
import com.fufu.terminal.repository.ScriptInteractionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing script interactions.
 */
@Service
@AllArgsConstructor
public class ScriptInteractionService {

    private final ScriptInteractionRepository interactionRepository;
    private final ScriptExecutionSessionRepository sessionRepository;

    /**
     * Creates and persists a new interaction request.
     *
     * @param session       The current execution session.
     * @param atomicScript  The script requesting interaction.
     * @param request       The interaction request details.
     * @return The persisted ScriptInteraction entity.
     */
    @Transactional
    public ScriptInteraction createInteraction(ScriptExecutionSession session, AtomicScript atomicScript, InteractionRequest request) {
        ScriptInteraction interaction = new ScriptInteraction();
        interaction.setSessionId(session.getId());
        interaction.setAtomicScriptId(atomicScript.getId());
        interaction.setInteractionType(InteractionType.valueOf(request.getType().toUpperCase())); // Ensure type matches ENUM
        interaction.setPromptMessage(request.getPrompt());
        interaction.setStatus("PENDING");
        interaction.setCreatedAt(LocalDateTime.now());
        return interactionRepository.save(interaction);
    }

    /**
     * Completes an interaction with a user's response.
     *
     * @param interactionId The ID of the interaction to complete.
     * @param response      The user's response.
     * @return The updated ScriptInteraction entity.
     */
    @Transactional
    public Optional<ScriptInteraction> completeInteraction(Long interactionId, InteractionResponse response) {
        Optional<ScriptInteraction> optionalInteraction = interactionRepository.findById(interactionId);
        if (optionalInteraction.isPresent()) {
            ScriptInteraction interaction = optionalInteraction.get();
            interaction.setUserResponse(response.getResponseDataAsJson()); // Assuming response data is JSON
            interaction.setStatus("COMPLETED");
            interaction.setRespondedAt(LocalDateTime.now());
            return Optional.of(interactionRepository.save(interaction));
        }
        return Optional.empty();
    }
}
