package com.back.user.service;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.user.domain.Provider;
import com.back.user.domain.User;
import com.back.user.dto.MyPageResponse;
import com.back.user.dto.SignupRequest;
import com.back.user.dto.SignupResponse;
import com.back.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.createLocal(request.email(), passwordHash, request.nickname());
        User savedUser = userRepository.save(user);

        return SignupResponse.from(savedUser);
    }

    public void checkEmailAvailability(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    public MyPageResponse getMyPage(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String primaryPersona = user.getPrimaryPersona() == null
                ? null
                : user.getPrimaryPersona().name();
        List<String> personaTags = user.getPersonaTags().stream()
                .map(Enum::name)
                .toList();

        return new MyPageResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getProvider() == Provider.LOCAL ? "NORMAL" : user.getProvider().name(),
                user.getProfileImageUrl(),
                primaryPersona,
                personaTags
        );
    }
}
