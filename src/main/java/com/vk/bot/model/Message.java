package com.vk.bot.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class Message {
    private Long messageId;
    private Long userId;
    private String text;
}
