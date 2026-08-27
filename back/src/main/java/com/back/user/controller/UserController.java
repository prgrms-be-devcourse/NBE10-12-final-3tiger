package com.back.user.controller;

import com.back.global.api.ApiResponse;
import com.back.global.auth.CurrentUserId;
import com.back.user.dto.MyPageUpdateRequest;
import com.back.user.dto.MyPageResponse;
import com.back.user.dto.ProfileImageResponse;
import com.back.user.dto.SignupRequest;
import com.back.user.dto.SignupResponse;
import com.back.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok("회원가입이 완료되었습니다.", userService.signup(request));
    }

    @GetMapping("/check-email")
    public ApiResponse<Void> checkEmail(
            @RequestParam @NotBlank @Email String email
    ) {
        userService.checkEmailAvailability(email);
        return ApiResponse.ok("사용 가능한 이메일입니다.", null);
    }

    @GetMapping("/me")
    public ApiResponse<MyPageResponse> getMyPage(@CurrentUserId Long userId) {
        return ApiResponse.ok("마이페이지 조회 성공", userService.getMyPage(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<Void> updateMyPage(
            @CurrentUserId Long userId,
            @Valid @RequestBody MyPageUpdateRequest request
    ) {
        userService.updateMyPage(userId, request);
        return ApiResponse.ok("마이페이지 수정 성공", null);
    }

    @PostMapping(value = "/me/profile-image", consumes = "multipart/form-data")
    public ApiResponse<ProfileImageResponse> updateProfileImage(
            @CurrentUserId Long userId,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ApiResponse.ok(
                "프로필 사진이 변경되었습니다.",
                userService.updateProfileImage(userId, file)
        );
    }

    @PatchMapping("/withdraw")
    public ApiResponse<Void> withdraw(@CurrentUserId Long userId) {
        userService.withdraw(userId);
        return ApiResponse.ok("회원 탈퇴가 정상적으로 완료되었습니다.", null);
    }
}
