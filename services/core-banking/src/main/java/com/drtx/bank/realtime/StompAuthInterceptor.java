package com.drtx.bank.realtime;

import java.util.List;

import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Authenticates STOMP CONNECT with the bearer token and authorises every SUBSCRIBE; clients cannot SEND. */
@Component
class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    StompAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorize(accessor);
            case SEND -> throw new MessageDeliveryException("Clients may not send messages");
            default -> {
            }
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            List<String> perms = jwt.getClaimAsStringList("perms");
            return new JwtAuthenticationToken(jwt, perms == null ? List.of()
                    : perms.stream().map(SimpleGrantedAuthority::new).toList());
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid token");
        }
    }

    private static void authorize(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof JwtAuthenticationToken token)) {
            throw new MessageDeliveryException("Not authenticated");
        }
        CurrentUser user = CurrentUser.from(token.getToken());
        String destination = String.valueOf(accessor.getDestination());
        if (destination.equals("/user/queue/notifications")) {
            return;
        }
        if (destination.equals("/topic/tenant." + user.tenantId() + ".ops") && user.has(Permission.OPS_MONITOR)) {
            return;
        }
        throw new MessageDeliveryException("Subscription to " + destination + " denied");
    }
}
