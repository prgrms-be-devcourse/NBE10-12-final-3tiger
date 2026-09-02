package com.back.user.service;

import com.back.auth.service.AuthService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.user.domain.Provider;
import com.back.user.domain.User;
import com.back.user.domain.UserMemo;
import com.back.user.dto.MyPageUpdateRequest;
import com.back.user.dto.MyPageResponse;
import com.back.user.dto.ProfileImageResponse;
import com.back.user.dto.SignupRequest;
import com.back.user.dto.SignupResponse;
import com.back.user.repository.UserRepository;
import com.back.user.repository.UserMemoRepository;
import com.back.user.dto.UserMemoRequest;
import com.back.user.dto.UserMemoResponse;
import com.back.global.error.ApiException;
import com.back.user.storage.ProfileImageStorage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;

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
    private final UserMemoRepository userMemoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ProfileImageStorage profileImageStorage;

    public UserService(
            UserRepository userRepository,
            UserMemoRepository userMemoRepository,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            ProfileImageStorage profileImageStorage
    ) {
        this.userRepository = userRepository;
        this.userMemoRepository = userMemoRepository;
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

    public UserMemoResponse getPersonalMemo(Long ownerUserId, Long targetUserId) {
        validateMemoTarget(ownerUserId, targetUserId);
        return userMemoRepository.findByOwner_IdAndTarget_Id(ownerUserId, targetUserId)
                .map(memo -> toUserMemoResponse(targetUserId, memo))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "저장된 사용자 메모가 없습니다."));
    }

    @Transactional
    public UserMemoResponse savePersonalMemo(Long ownerUserId, Long targetUserId, UserMemoRequest request) {
        User target = validateMemoTarget(ownerUserId, targetUserId);
        List<String> tags = request.tags().stream().map(String::trim).distinct().toList();
        String memoText = request.memo() == null ? null : request.memo().trim();
        UserMemo userMemo = userMemoRepository.findByOwner_IdAndTarget_Id(ownerUserId, targetUserId)
                .orElseGet(() -> new UserMemo(getUser(ownerUserId), target, tags, memoText));
        userMemo.update(tags, memoText);
        return toUserMemoResponse(targetUserId, userMemoRepository.save(userMemo));
    }

    @Transactional
    public void deletePersonalMemo(Long ownerUserId, Long targetUserId) {
        validateMemoTarget(ownerUserId, targetUserId);
        if (userMemoRepository.deleteByOwner_IdAndTarget_Id(ownerUserId, targetUserId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "저장된 사용자 메모가 없습니다.");
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

    private User validateMemoTarget(Long ownerUserId, Long targetUserId) {
        if (ownerUserId.equals(targetUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "본인에게는 개인 메모를 저장할 수 없습니다.");
        }
        return getUser(targetUserId);
    }

    private User getUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private UserMemoResponse toUserMemoResponse(Long targetUserId, UserMemo memo) {
        return new UserMemoResponse(targetUserId, memo.getTags(), memo.getMemo(), memo.getUpdatedAt());
    }

    private void validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()
                || file.getSize() > MAX_PROFILE_IMAGE_SIZE
                || !SUPPORTED_PROFILE_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
