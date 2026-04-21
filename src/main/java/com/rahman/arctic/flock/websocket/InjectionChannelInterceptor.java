package com.rahman.arctic.flock.websocket;

import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import com.rahman.arctic.orca.utils.ArcticUserDetails;

@Component
public class InjectionChannelInterceptor implements ChannelInterceptor {

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
			Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
			if (sessionAttributes != null) {
				ArcticUserDetails user = (ArcticUserDetails) sessionAttributes.get("ARCTIC_USER");
				if (user != null) {
					UsernamePasswordAuthenticationToken auth =
							new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
					accessor.setUser(auth);
				} else {
					AgentPrincipal agent = (AgentPrincipal) sessionAttributes.get(AgentHandshakeInterceptor.AGENT_PRINCIPAL_ATTR);
					if (agent != null) {
						UsernamePasswordAuthenticationToken auth =
								new UsernamePasswordAuthenticationToken(agent, null, java.util.List.of());
						accessor.setUser(auth);
					}
				}
			}
		}

		return message;
	}

}
