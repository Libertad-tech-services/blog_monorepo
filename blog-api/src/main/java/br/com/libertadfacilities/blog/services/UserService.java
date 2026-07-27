package br.com.libertadfacilities.blog.services;

import br.com.libertadfacilities.blog.entity.User;
import br.com.libertadfacilities.blog.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getUserById(Long id){
        return userRepository.findById(id)
                .orElseThrow(()-> new UsernameNotFoundException("Usuário não encontrado."));
    }
}
