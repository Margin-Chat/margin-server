package org.margin.server.sfu;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

@Service
public class SfuService {

    @Value("${mediasoup.url:http://localhost:3000}")
    private String mediasoupUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String createOrJoinRoom(String roomId) {
        String url = mediasoupUrl +
                "/rooms/" +
                roomId;

        restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        return mediasoupUrl;
    }
}