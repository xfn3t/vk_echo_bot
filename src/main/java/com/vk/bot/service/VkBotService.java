package com.vk.bot.service;

import com.vk.bot.model.Message;
import com.vk.bot.repository.RepliedMessageRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Getter
public class VkBotService {

    private final RepliedMessageRepository repliedMessageRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String VK_API_URL = "https://api.vk.com/method/";

    @Value("${vk.api.token}")
    private String accessToken;
    @Value("${vk.api.group.id}")
    private int groupId;

    @Value("${vk.api.version}")
    private String vkApiVersion;

    private int ts;

    private String longPollServer;
    private String longPollKey;

    @Autowired
    public VkBotService(RepliedMessageRepository repliedMessageRepository,
                        @Value("${vk.api.token}") String accessToken,
                        @Value("${vk.api.group.id}") int groupId,
                        @Value("${vk.api.version}") String vkApiVersion) {
        this.repliedMessageRepository = repliedMessageRepository;
        this.accessToken = accessToken;
        this.groupId = groupId;
        this.vkApiVersion = vkApiVersion;
        initializeLongPollServer();
    }


    /**
     * Initializes the Long Polling server by calling the VK API.
     * Retrieves server, key, and timestamp needed for subsequent Long Poll requests.
     */
    public void initializeLongPollServer() {

        String url = VK_API_URL + "groups.getLongPollServer?group_id=" + groupId +
                "&access_token=" + accessToken + "&v=" + vkApiVersion;

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("response")) {
                Map<String, Object> serverData = (Map<String, Object>) response.get("response");
                this.longPollServer = (String) serverData.get("server");
                this.longPollKey = (String) serverData.get("key");
                this.ts = Integer.parseInt(serverData.get("ts").toString());

                log.info("Long Poll init");
            } else {
                throw new RuntimeException("LongPoll exception: Bad API.");
            }
        } catch (Exception e) {
            log.error("LongPoll exception: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Bad init Long Poll", e);
        }
    }

    /**
     * Scheduled task that polls the VK Long Polling server for new events.
     * If new messages are received, processes them and sends replies if needed.
     */
    @Scheduled(fixedDelay = 1000)
    public void pollVkServer() {

        if (longPollServer == null || longPollServer.isEmpty()) {
            log.error("Long Poll not init.");
            initializeLongPollServer();
            return;
        }

        String url = longPollServer + "?act=a_check&key=" + longPollKey + "&ts=" + ts + "&wait=25&mode=2&version=3";
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        if (response != null && response.containsKey("updates")) {
            List<Map<String, Object>> updates = (List<Map<String, Object>>) response.get("updates");

            for (Map<String, Object> update : updates) {
                if ("message_new".equals(update.get("type"))) {
                    Map<String, Object> objectData = (Map<String, Object>) update.get("object");

                    if (objectData != null && objectData.containsKey("message")) {
                        Map<String, Object> messageObject = (Map<String, Object>) objectData.get("message");

                        if (messageObject != null && messageObject.containsKey("from_id") && messageObject.containsKey("id")) {
                            Message vkMessage = new Message();

                            vkMessage.setUserId(((Number) messageObject.get("from_id")).longValue());
                            vkMessage.setMessageId(((Number) messageObject.get("id")).longValue());
                            vkMessage.setText("You say: " + messageObject.get("text"));

                            log.info("Message: " + vkMessage.getText());

                            if (shouldReply(vkMessage)) {
                                replyToMessage(vkMessage, System.currentTimeMillis());
                            }
                        } else {
                            log.error("Bad data in message: " + messageObject);
                        }
                    } else {
                        log.error("Not full data in object: " + objectData);
                    }
                }
            }

            this.ts = Integer.parseInt(response.get("ts").toString());
        } else {
            log.error("Bad update from Long Poll. Reinit...");
            initializeLongPollServer();
        }
    }


    /**
     * Checks whether a reply should be sent for the given message.
     * @param message the incoming message to check
     * @return true if no reply has been sent previously, false otherwise
     */
    public boolean shouldReply(Message message) {
        return !repliedMessageRepository.isReplied(message.getMessageId());
    }

    /**
     * Sends a reply to the user with the given message content.
     * Adds the message ID to the repository to track replied messages.
     * @param message the message to reply to
     * @param randomId a unique identifier for the message (usually a timestamp)
     */
    public void replyToMessage(Message message, long randomId) {

        String replyUrl = VK_API_URL + "messages.send?user_id=" + message.getUserId() +
                "&message=" + message.getText() +
                "&random_id=" + randomId +
                "&access_token=" + accessToken +
                "&v=" + vkApiVersion;

        restTemplate.getForObject(replyUrl, Void.class);
        repliedMessageRepository.addRepliedMessage(message.getMessageId());
    }
}
