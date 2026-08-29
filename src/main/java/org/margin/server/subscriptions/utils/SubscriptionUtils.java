package org.margin.server.subscriptions.utils;

import org.margin.server.subscriptions.entities.Subscription;

import java.time.LocalDate;
import java.time.ZoneOffset;

public class SubscriptionUtils {
    public static LocalDate getNextSubscriptionDate(Subscription subscription) {
        return subscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
