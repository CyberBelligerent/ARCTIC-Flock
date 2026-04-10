package com.rahman.arctic.flock.websocket;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.rahman.arctic.orca.utils.ArcticUserDetails;
import com.rahman.arctic.orca.utils.ArcticUserService;
import com.rahman.arctic.orca.utils.JwtTokenUtil;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

	private final JwtTokenUtil tokenUtil;
	private final ArcticUserService userService;

	public JwtHandshakeInterceptor(JwtTokenUtil tokenUtil, ArcticUserService userService) {
		this.tokenUtil = tokenUtil;
		this.userService = userService;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) {

		String jwt = null;

		if (request instanceof ServletServerHttpRequest servletRequest) {
			HttpServletRequest httpRequest = servletRequest.getServletRequest();

			// Check Authorization header first
			String authHeader = httpRequest.getHeader("Authorization");
			if (authHeader != null && authHeader.startsWith("Bearer ")) {
				jwt = authHeader.substring(7);
			}

			// Fall back to token cookie
			if (jwt == null) {
				Cookie[] cookies = httpRequest.getCookies();
				if (cookies != null) {
					for (Cookie cookie : cookies) {
						if ("token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
							jwt = cookie.getValue();
							break;
						}
					}
				}
			}
		}

		if (jwt == null) return false;

		try {
			String username = tokenUtil.getUsernameFromToken(jwt);
			if (username == null) return false;

			ArcticUserDetails user = (ArcticUserDetails) userService.loadUserByUsername(username);
			if (user == null || !tokenUtil.validateToken(jwt, user)) return false;

			attributes.put("ARCTIC_USER", user);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {
	}

}
