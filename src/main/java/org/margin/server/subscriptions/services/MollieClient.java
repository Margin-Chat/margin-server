package org.margin.server.subscriptions.services;

import com.mollie.mollie.Client;
import com.mollie.mollie.models.components.*;
import com.mollie.mollie.models.operations.*;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.subscriptions.config.MollieProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class MollieClient {
    private final MollieProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Client mollieClient;
    private final String apiKey;

    public MollieClient(MollieProperties properties) {
        this.properties = properties;
        this.apiKey = properties.apiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Mollie API Key is not configured in application.properties");
        }

        this.mollieClient = Client.builder()
                .security(Security.builder()
                        .apiKey(apiKey)
                        .build())
                .build();
    }

    public CustomerResponse createCustomer(String name, String email) {
        CreateCustomerResponse res = mollieClient.customers().create()
                .entityCustomer(EntityCustomer.builder()
                        .name(name)
                        .email(email)
                        .locale(LocaleResponse.EN_US)
                        .build())
                .call();

        if (res.customerResponse().isPresent()) {
            log.info("Customer {} has been successfully created", res.customerResponse().get().id());
        } else {
            throw new IllegalStateException("Could not create customer");
        }

        return res.customerResponse().get();
    }

    public PaymentResponse createFirstPayment(String customerId,
                                              String amount,
                                              String currency,
                                              String description,
                                              String redirectUrl,
                                              String webhookUrl,
                                              String metadata) {
        CreatePaymentResponse response = mollieClient.payments().create()
                .paymentRequest(PaymentRequest.builder()
                        .customerId(customerId)
                        .amount(Amount.builder()
                                .value(amount)
                                .currency(currency)
                                .build())
                        .description(description)
                        .redirectUrl(redirectUrl)
                        .webhookUrl(webhookUrl)
                        .metadata(Metadata.of(metadata))
                        .sequenceType(SequenceType.FIRST)
                        .build())
                .call();

        if (response.paymentResponse().isPresent()) {
            log.info("Created payment with response status: {}", response.paymentResponse().get().status());
        } else {
            throw new IllegalStateException("Subscription response is null");
        }
        return response.paymentResponse().get();
    }

    public SubscriptionResponse createSubscription(String customerId,
                                                   String amount,
                                                   String currency,
                                                   String interval,
                                                   String description,
                                                   String webhookUrl,
                                                   String startDate) {
        CreateSubscriptionResponse res = mollieClient.subscriptions().create()
                .customerId(customerId)
                .subscriptionRequest(SubscriptionRequest.builder()
                        .amount(Amount.builder()
                                .currency(currency)
                                .value(amount)
                                .build())
                        .interval(interval)
                        .description(description)
                        .webhookUrl(webhookUrl)
                        .startDate(startDate)
                        .build())
                .call();

        if (res.subscriptionResponse().isPresent()) {
            log.info("Create subscription response: {}", res.subscriptionResponse().get());
        } else {
            throw new IllegalStateException("Subscription response is null");
        }
        return res.subscriptionResponse().get();
    }

    public PaymentResponse getPayment(String paymentId) {
        GetPaymentRequest request = GetPaymentRequest.builder()
                .paymentId(paymentId)
                .build();

        GetPaymentResponse response = mollieClient.payments().get()
                .request(request)
                .call();

        if (response.paymentResponse().isPresent()) {
            log.info("Get payment response status: {}", response.paymentResponse().get().status());
        } else {
            throw new IllegalStateException("Subscription response is null");
        }

        return response.paymentResponse().get();
    }

    public SubscriptionResponse getSubscription(String customerId, String subscriptionId) {
        GetSubscriptionResponse response = mollieClient.subscriptions().get()
                .customerId(customerId)
                .subscriptionId(subscriptionId)
                .call();

        if (response.subscriptionResponse().isPresent()) {
            log.info("Get subscription response: {}", response.subscriptionResponse().get().status());
        } else {
            throw new IllegalStateException("Subscription response is null");
        }

        return response.subscriptionResponse().get();
    }

    public void cancelSubscription(String customerId, String subscriptionId) {
        CancelSubscriptionResponse response = mollieClient.subscriptions().cancel()
                .customerId(customerId)
                .subscriptionId(subscriptionId)
                .requestBody(CancelSubscriptionRequestBody.builder()
                        .testmode(Boolean.parseBoolean(properties.testmode()))
                        .build())
                .call();

        if (response.subscriptionResponse().isPresent()) {
            log.info("Cancel subscription response: {}", response.subscriptionResponse().get().status());
        } else {
            throw new IllegalStateException("Subscription response is null");
        }
    }

    public GetCustomerResponseBody getCustomer(String customerId) {
        GetCustomerResponse response = mollieClient.customers().get()
                .customerId("cst_5B8cwPMGnU")
                .include("events")
                .idempotencyKey("123e4567-e89b-12d3-a456-426")
                .call();

        if (response.object().isPresent()) {
            log.info("Get customer response: {}", response.object().get());
        } else {
            throw new IllegalStateException("Customer response is null");
        }

        return response.object().get();
    }
}