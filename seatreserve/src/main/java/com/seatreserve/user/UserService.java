package com.seatreserve.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.seatreserve.user.UserDtos.UserResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(String email, String rawPassword, User.Role role) {
        if (userRepository.findByEmail(email).isPresent()) {
            log.info("User registration rejected: email already registered ({})", email);
            throw new IllegalArgumentException("Email is already registered: " + email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered successfully: userId={}, email={}, role={}",
                saved.getId(), saved.getEmail(), saved.getRole());
        return UserResponse.from(saved);
    }
}
