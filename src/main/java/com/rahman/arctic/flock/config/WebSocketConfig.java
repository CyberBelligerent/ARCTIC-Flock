package com.rahman.arctic.flock.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.rahman.arctic.flock.websocket.InjectionChannelInterceptor;
import com.rahman.arctic.flock.websocket.JwtHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
	private final InjectionChannelInterceptor injectionChannelInterceptor;

	public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor,
			InjectionChannelInterceptor injectionChannelInterceptor) {
		this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
		this.injectionChannelInterceptor = injectionChannelInterceptor;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/stomp")
				.setAllowedOrigins("http://localhost:5173", "http://localhost")
				.addInterceptors(jwtHandshakeInterceptor)
				.withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes("/app");
		registry.enableSimpleBroker("/topic");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(injectionChannelInterceptor);
	}

}
