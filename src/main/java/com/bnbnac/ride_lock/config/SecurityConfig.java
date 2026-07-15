package com.bnbnac.ride_lock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	// 이 프로젝트는 순수 JSON API라 CSRF 토큰을 내려줄 HTML 폼이 없다 - 기본 세션 기반 CSRF 방어는
	// 브라우저 폼 제출을 전제로 해서 REST 클라이언트(k6 등)엔 애초에 적용할 수 없는 보호다.
	// 나머지 기본 동작(모든 요청 인증 필요, Basic/Form 로그인)은 Spring Boot 기본값 그대로 유지 -
	// JWT 인증을 실제로 붙일 때 이 설정 자체가 통째로 교체된다.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.formLogin(Customizer.withDefaults());
		return http.build();
	}

}
