package com.insurance.notificationservice.service;

import com.insurance.notificationservice.model.ChannelPolicy;

import java.util.Map;

public record NotificationDefinition(
        String title,
        String message,
        ChannelPolicy channelPolicy,
        String emailSubject,
        String templateName,
        Map<String, Object> templateModel
) {}
