package com.vk.bot.repository;

import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;

@Repository
public class RepliedMessageRepository {
    private final Set<Long> repliedMessages = new HashSet<>();

    public boolean isReplied(Long messageId) {
        return repliedMessages.contains(messageId);
    }

    public void addRepliedMessage(Long messageId) {
        repliedMessages.add(messageId);
    }
}
