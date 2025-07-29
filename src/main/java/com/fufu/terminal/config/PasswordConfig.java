package com.fufu.terminal.config;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    public static class BCryptPasswordEncoder implements PasswordEncoder {
        
        @Override
        public String encode(CharSequence rawPassword) {
            return BCrypt.withDefaults().hashToString(12, rawPassword.toString().toCharArray());
        }
        
        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            BCrypt.Result result = BCrypt.verifyer().verify(rawPassword.toString().toCharArray(), encodedPassword);
            return result.verified;
        }
    }
    
    public interface PasswordEncoder {
        String encode(CharSequence rawPassword);
        boolean matches(CharSequence rawPassword, String encodedPassword);
    }
}