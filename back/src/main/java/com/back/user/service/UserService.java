package com.back.user.service;

import com.back.auth.service.AuthService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.user.domain.Provider;
import com.back.user.domain.User;
import com.back.user.dto.MyPageUpdateRequest;
import com.back.user.dto.MyPageResponse;
import com.back.user.dto.ProfileImageResponse;
import com.back.user.dto.SignupRequest;
import com.back.user.dto.SignupResponse;
import com.back.user.repository.UserRepository;
import com.back.user.storage.ProfileImageStorage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final long MAX_PROFILE_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> SUPPORTED_PROFILE_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ProfileImageStorage profileImageStorage;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            ProfileImageStorage profileImageStorage
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.profileImageStorage = profileImageStorage;
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

    @Transactional
    public void updateMyPage(Long userId, MyPageUpdateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateProfile(
                request.nickname(),
                request.primaryPersona(),
                request.personaTags()
        );
    }

    @Transactional
    public ProfileImageResponse updateProfileImage(Long userId, MultipartFile file) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        validateProfileImage(file);
        String profileImageUrl = profileImageStorage.upload(userId, file);
        user.changeProfileImage(profileImageUrl);

        return new ProfileImageResponse(profileImageUrl);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.withdraw();
        authService.revokeAllRefreshTokens(userId);
    }

    private void validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()
                || file.getSize() > MAX_PROFILE_IMAGE_SIZE
                || !SUPPORTED_PROFILE_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
