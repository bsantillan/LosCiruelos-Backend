package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public Usuario findByEmail(String email) {
        return usuarioRepository.findByEmail(email).orElse(null);
    }

    public Usuario findByEmailOrThrow(String email, RuntimeException ex) {
        return usuarioRepository.findByEmail(email).orElseThrow(() -> ex);
    }

    public Usuario updateUsuario(Usuario user, String nombre, String apellido, String telefono) {
        user.setNombre(capitalizar(nombre));
        user.setApellido(capitalizar(apellido));
        user.setTelefono(telefono);
        return usuarioRepository.save(user);
    }

    public void desactivarUsuario(Usuario usuario) {
        usuario.setEnabled(false);
        usuarioRepository.save(usuario);
    }

    public Usuario crearUsuario(String email, String nombre, String apellido,
            String telefono, String password, Role rol, AuthProvider provider, Boolean termsAccepted) {
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .nombre(capitalizar(nombre))
                .apellido(capitalizar(apellido))
                .telefono(telefono)
                .passwordHash(password != null ? passwordEncoder.encode(password) : null)
                .rol(rol)
                .provider(provider)
                .termsAccepted(termsAccepted)
                .termsAcceptedAt(LocalDateTime.now())
                .emailVerificado(provider == AuthProvider.GOOGLE)
                .enabled(provider == AuthProvider.GOOGLE)
                .build());
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty())
            return texto;
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }
}
