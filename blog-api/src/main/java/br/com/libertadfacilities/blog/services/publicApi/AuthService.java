package br.com.libertadfacilities.blog.services.publicApi;


import br.com.libertadfacilities.blog.dto.request.AuthRequest;
import br.com.libertadfacilities.blog.dto.request.VerifyTwoFactorLoginRequest;
import br.com.libertadfacilities.blog.dto.response.LoginResponse;
import br.com.libertadfacilities.blog.entity.User;
import br.com.libertadfacilities.blog.exception.BusinessRuleException;
import br.com.libertadfacilities.blog.exception.ResourceNotFoundException;
import br.com.libertadfacilities.blog.repositories.UserRepository;
import br.com.libertadfacilities.blog.security.BlogUserDetailsService;
import br.com.libertadfacilities.blog.security.JwtService;
import br.com.libertadfacilities.blog.security.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TotpService totpService;
    private final BlogUserDetailsService userDetailsService;

    public LoginResponse login(AuthRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (user.isTwoFactorEnabled()) {
            String temporaryToken = jwtService.generateTemporaryTwoFactorToken(userDetails);

            return new LoginResponse(
                    null,
                    "Bearer",
                    true,
                    temporaryToken
            );
        }

        String token = jwtService.generateToken(userDetails);

        return new LoginResponse(
                token,
                "Bearer",
                false,
                null
        );
    }

    public LoginResponse verifyTwoFactorLogin(VerifyTwoFactorLoginRequest request) {
        if (!jwtService.isTemporaryTwoFactorToken(request.temporaryToken())) {
            throw new BusinessRuleException("Token temporário inválido ou expirado");
        }

        String email = jwtService.extractUsernameFromAnyToken(request.temporaryToken());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (!totpService.verifyCode(user.getTwoFactorSecret(), request.code())) {
            throw new BusinessRuleException("Código 2FA inválido");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        String token = jwtService.generateToken(userDetails);

        return new LoginResponse(
                token,
                "Bearer",
                false,
                null
        );
    }


}
