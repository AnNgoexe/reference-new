package com.example.bidMarket.controller;

import com.example.bidMarket.Enum.Role;
import com.example.bidMarket.MQTemplate.EmailProvider;
import com.example.bidMarket.SearchService.PaginatedResponse;
import com.example.bidMarket.dto.*;
import com.example.bidMarket.dto.Request.EmailRequest;
import com.example.bidMarket.dto.Request.LoginRequest;
import com.example.bidMarket.dto.Request.RefreshTokenRequest;
import com.example.bidMarket.dto.Request.RegisterRequest;
import com.example.bidMarket.dto.Response.AccountInfo;
import com.example.bidMarket.dto.Response.JwtAuthenticationResponse;
import com.example.bidMarket.dto.Response.OrderResponse;
import com.example.bidMarket.dto.Response.RegisterResponse;
import com.example.bidMarket.exception.AppException;
import com.example.bidMarket.exception.ErrorCode;
import com.example.bidMarket.mapper.UserMapper;
import com.example.bidMarket.model.User;
import com.example.bidMarket.repository.UserRepository;
import com.example.bidMarket.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import com.example.bidMarket.service.VerifyEmailService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final VerifyEmailService verifyEmailService;
    private final EmailProvider emailProvider;
    private final UserMapper userMapper;

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Value("${cookieConfig.sameSite}")
    private String cookieSameSite;
    @Value("${cookieConfig.secure}")
    private String cookieSecure;

    public UserController(UserService userService, UserRepository userRepository, VerifyEmailService verifyEmailService, EmailProvider emailProvider, UserMapper userMapper) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.verifyEmailService = verifyEmailService;
        this.emailProvider = emailProvider;
        this.userMapper = userMapper;
    }

    @PostMapping(value = "/signup")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest registerRequest) throws Exception {
        logger.info("Start sign up for user: {}", registerRequest.getEmail());
        RegisterResponse registerResponse = userService.createUser(registerRequest);
        emailProvider.sendEmailOTPRequest(EmailRequest.builder().email(registerRequest.getEmail()).build());
        return ResponseEntity.ok(registerResponse);
    }

    @PostMapping("/signin")
    public ResponseEntity<JwtAuthenticationResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse cookieResponse) {
        logger.debug("Received signin request for user: {}", loginRequest.getEmail());
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.isBanned()){
            logger.warn("Account " + loginRequest.getEmail() + " is banned");
            throw new AppException(ErrorCode.USER_IS_BANNED);
        }

        if (!user.isVerified()){
            logger.warn("Email " + loginRequest.getEmail() + " is not verified");
            throw new AppException(ErrorCode.USER_IS_NOT_VERIFIED);
        }
        try {
            JwtAuthenticationResponse response = userService.authenticateUser(loginRequest);
            createAuthCookies(cookieResponse, response.getRefreshToken());
            logger.debug("Authen successful: {}", loginRequest.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Authen failed: {}", loginRequest.getEmail());
            throw e;
        }
    }

//    private void createAuthCookies(HttpServletResponse response, String refreshToken) {
//        Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken);
//        refreshTokenCookie.setHttpOnly(true);
//        refreshTokenCookie.setSecure(false); // chưa có https
//        refreshTokenCookie.setPath("/api/users/refresh-token");
//        refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60);
////        refreshTokenCookie.setSameSite("None");
//        response.addCookie(refreshTokenCookie);
//    }

//    private void createAuthCookies(HttpServletResponse response, String refreshToken) {
//        response.setHeader("Set-Cookie",
//                "refreshToken=" + refreshToken +
//                        "; HttpOnly; Path=/api/users/refresh-token; Max-Age=" + (7 * 24 * 60 * 60) +
//                        "; SameSite=None");
//    }

    private void createAuthCookies(HttpServletResponse response, String refreshToken) {
        Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setPath("/api/users/refresh-token");
        refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60);
        String sameSiteAttribute = "; SameSite=" + cookieSameSite;
        if (Objects.equals(cookieSecure, "True")) {
            refreshTokenCookie.setSecure(true);
        }

        response.setHeader("Set-Cookie",
                "refreshToken=" + refreshToken +
                        "; HttpOnly" +
                        (refreshTokenCookie.getSecure() ? "; Secure" : "") +
                        "; Path=" + refreshTokenCookie.getPath() +
                        "; Max-Age=" + refreshTokenCookie.getMaxAge() +
                        sameSiteAttribute);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<JwtAuthenticationResponse> refreshToken(@CookieValue("refreshToken") String refreshToken) {
        logger.info("Refresh token");
        JwtAuthenticationResponse response = userService.refreshToken(new RefreshTokenRequest(refreshToken));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        // orther logic -> later
        Cookie refreshTokenCookie = new Cookie("refreshToken", null);
        refreshTokenCookie.setMaxAge(0);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);
        refreshTokenCookie.setPath("/api/users/refresh-token");
        response.addCookie(refreshTokenCookie);
        return ResponseEntity.ok("Logged out successfully.");
    }

    @GetMapping("/{id}")
//    @PreAuthorize("hasRole('ROLE_ADMIN') or @userSecurity.hasUserId(authentication, #id)")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID id) {
        logger.debug("Fetching user with id: {}", id);
        UserDto userDto = userService.getUserById(id);
        return ResponseEntity.ok(userDto);
    }

    @GetMapping("/search")
//    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public PaginatedResponse<UserDto> searchUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean isBanned,
            @RequestParam(required = false) Boolean isVerified,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        Page<User> userPage = userService.searchUsers(email, role, isBanned, isVerified, page, size, sortBy, sortDirection);
        List<UserDto> content = userPage.stream()
                .map(userMapper::userToUserDto)
                .toList();
        return new PaginatedResponse<UserDto>(
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isLast(),
                userPage.isFirst(),
                content
        );
    }


    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or @userSecurity.hasUserId(authentication, #id)")
    public ResponseEntity<UserDto> updateUser(@PathVariable UUID id, @Valid @RequestBody UserUpdateDto userUpdateDto) {
        UserDto userDto = userService.updateUser(id, userUpdateDto);
        return ResponseEntity.ok(userDto);
    }

    @GetMapping("/{id}/accountInfo")
    public ResponseEntity<AccountInfo> getUserAccountInfo(@PathVariable UUID id) {
        logger.info("Get account info of user id " + id);
        return ResponseEntity.ok(userService.getAccountInfoByUserId(id));
    }

    // update avatar
    @PutMapping("/avatar/{userId}")
    public ResponseEntity<String> updateAvatar(@PathVariable UUID userId, @RequestParam String imageUrl){
        userService.updateAvatar(userId, imageUrl);
        return ResponseEntity.ok("Update avatar successfully");
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<ProfileDto> updateUserProfile(@PathVariable UUID id,@RequestBody ProfileDto profileDto) {
        ProfileDto updatedProfile = userService.updateProfile(id, profileDto);
        return ResponseEntity.ok(updatedProfile);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/changePassword/{email}")
    public ResponseEntity<String> changePassword(
            @PathVariable String email,
            @RequestParam String currentPassword,
            @RequestParam String newPassword
            ) {
        userService.changePassword(email, currentPassword, newPassword);
        return ResponseEntity.ok("Change Password successfully");
    }

    @PostMapping("/forgotPassword/{email}")
    public ResponseEntity<String> forgotPasswordHandler(@PathVariable String email) {
        emailProvider.sendEmailOTPRequest(EmailRequest.builder().email(email).build());
        return ResponseEntity.ok("Email sent successfully");
    }

    @PutMapping("/banUser/{userId}")
    public void banUser(@PathVariable UUID userId) {
        userService.banUser(userId);
    }

    @PutMapping("/unBanUser/{userId}")
    public void unBanUser(@PathVariable UUID userId) {
        userService.unBanUser(userId);
    }

}
