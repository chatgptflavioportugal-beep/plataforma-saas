package com.saas.subscription.dto.request;

import java.util.List;

public record ConfirmSubscriptionRequest(
    List<ModuleSubscriptionItem> modules
) {}
