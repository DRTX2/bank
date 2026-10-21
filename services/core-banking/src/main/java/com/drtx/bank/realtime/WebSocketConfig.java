package com.drtx.bank.realtime;

import com.drtx.bank.shared.security.SecurityProperties;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket. Destinations:
 * <ul>
 *   <li>{@code /user/queue/notifications} — per-user payment notifications</li>
 *   <li>{@code /topic/tenant.{tenantId}.ops} — live operations feed (requires ops:monitor, same tenant)</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor auth;
    private final SecurityProperties props;

    WebSocketConfig(StompAuthInterceptor auth, SecurityProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(props.corsOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // shortcut: in-memory broker per replica; Kafka fan-out (every replica consumes every event) keeps it correct.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }
}
