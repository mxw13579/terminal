package com.fufu.terminal.util;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PasswordHashGenerator implements CommandLineRunner {
    
    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && "hash".equals(args[0])) {
            // Generate admin123 hash
            String adminHash = BCrypt.withDefaults().hashToString(12, "admin123".toCharArray());
            System.out.println("Admin password hash for 'admin123': " + adminHash);
            
            // Generate user123 hash  
            String userHash = BCrypt.withDefaults().hashToString(12, "user123".toCharArray());
            System.out.println("User password hash for 'user123': " + userHash);
            
            // Test verification
            boolean adminTest = BCrypt.verifyer().verify("admin123".toCharArray(), adminHash).verified;
            boolean userTest = BCrypt.verifyer().verify("user123".toCharArray(), userHash).verified;
            
            System.out.println("Admin hash verification: " + adminTest);
            System.out.println("User hash verification: " + userTest);
            
            // Test with existing hash from database
            String existingAdminHash = "$2a$12$EXRkfkdTkHjwhXeQsX5vhOxVpBnbatMWpICbFNJvpeJbFo.B5ssLO";
            boolean existingTest = BCrypt.verifyer().verify("admin123".toCharArray(), existingAdminHash).verified;
            System.out.println("Existing admin hash verification: " + existingTest);
        }
    }
}