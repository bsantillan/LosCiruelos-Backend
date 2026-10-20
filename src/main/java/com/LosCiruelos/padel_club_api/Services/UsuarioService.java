package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.UsuarioNotFoundException;
import com.LosCiruelos.padel_club_api.Repository.UsuarioRepository;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.Telefonos;

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
        cambiarTelefono(user, telefono);
        return usuarioRepository.save(user);
    }

    /**
     * Guarda el teléfono normalizado (formato de WhatsApp). Si cambia, deja de estar verificado:
     * hay que volver a confirmarlo con un código antes de mandarle mensajes.
     */
    public void cambiarTelefono(Usuario user, String telefono) {
        String normalizado = Telefonos.normalizar(telefono);
        if (!Objects.equals(normalizado, user.getTelefono())) {
            user.setTelefonoVerificado(false);
        }
        user.setTelefono(normalizado);
    }

    /** ADMIN activos: reciben los avisos de reservas. */
    public List<Usuario> adminsActivos() {
        return usuarioRepository.findByRolAndEnabledTrue(Role.ADMIN);
    }

    public void desactivarUsuario(Usuario usuario) {
        usuario.setEnabled(false);
        usuarioRepository.save(usuario);
    }

    public Usuario crearUsuario(String email, String nombre, String apellido,
            String telefono, String password, Role rol, AuthProvider provider,
            Boolean termsAccepted, Boolean emailVerificado, Boolean enabled) {
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .nombre(capitalizar(nombre))
                .apellido(capitalizar(apellido))
                .telefono(Telefonos.normalizar(telefono))
                .passwordHash(password != null ? passwordEncoder.encode(password) : null)
                .rol(rol)
                .provider(provider)
                .termsAccepted(termsAccepted)
                .termsAcceptedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS))
                .emailVerificado(emailVerificado)
                .enabled(enabled)
                .build());
    }

    public Usuario findByIdOrThrow(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado: " + id));
    }

    public Usuario save(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    public List<Usuario> findAll(Specification<Usuario> spec) {
        return usuarioRepository.findAll(spec);
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty())
            return texto;
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }
}
