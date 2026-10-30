package com.LosCiruelos.padel_club_api.usuarios.service;

import jakarta.persistence.criteria.Predicate;
import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.ClienteBusquedaResponse;
import com.LosCiruelos.padel_club_api.common.Busquedas;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import java.util.Collections;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.mapper.UsuarioMapper;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.CrearUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.EditarUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.FiltroUsuarioRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.UsuarioAdminResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.ClienteProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.AuthProvider;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.exception.EmailEnUsoException;
import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Usuarios desde el panel: el ADMIN los crea, edita, lista, desactiva y reactiva; el personal
 * (ADMIN y EMPLEADO) busca clientes para filtrar reservas o reservar a su nombre.
 */
public class UsuarioAdminService {

    public static final int MINIMO_CARACTERES_BUSQUEDA = 2;
    public static final int MAXIMO_RESULTADOS_BUSQUEDA = 20;

    private final ClienteProfileService clienteProfileService;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenService refreshTokenService;
    private final VerificationService verificationService;
    private final UsuarioMapper usuarioMapper;

    @Transactional
    public UsuarioAdminResponse crearUsuario(CrearUsuarioAdminRequest request) {
        Usuario existente = usuarioService.findByEmail(request.getEmail());
        if (existente != null) {
            throw new EmailEnUsoException();
        }

        // Validación: si es CLIENTE, categoria y posicion son obligatorios
        if (request.getRol() == Role.CLIENTE) {
            if (request.getCategoria() == null || request.getPosicion() == null) {
                throw new IllegalArgumentException("La categoría y posición son obligatorias para clientes");
            }
        }

        String passwordTemporal = generarPasswordTemporal();

        Usuario usuario = usuarioService.crearUsuario(
                request.getEmail(), request.getNombre(), request.getApellido(),
                request.getTelefono(), passwordTemporal,
                request.getRol(), AuthProvider.LOCAL, true, false, true); // habilitado, email sin verificar

        if (request.getRol() == Role.CLIENTE) {
            clienteProfileService.cargarDatosDeJugador(usuario, request.getCategoria(), request.getPosicion());
        }

        try {
            verificationService.enviarBienvenida(usuario.getEmail(), passwordTemporal);
        } catch (Exception e) {
            log.error("No se pudo enviar email a {}: {}", usuario.getEmail(), e.getMessage());
        }

        return buildUsuarioAdminResponse(usuario);
    }

    @Transactional
    public UsuarioAdminResponse editarUsuario(Long id, EditarUsuarioAdminRequest request) {

        // Todas las validaciones antes de modificar
        if ((request.getCategoria() != null || request.getPosicion() != null) && request.getRol() != Role.CLIENTE) {
            throw new IllegalArgumentException("El rol debe ser CLIENTE para asignar categoría o posición");
        }
        if (request.getRol() == Role.CLIENTE && (request.getCategoria() == null || request.getPosicion() == null)) {
            throw new IllegalArgumentException("La categoría y posición son obligatorias para clientes");
        }

        Usuario usuario = usuarioService.findByIdOrThrow(id);
        if (usuario.getRol() == Role.ADMIN || request.getRol() == Role.ADMIN) {
            throw new IllegalArgumentException("Los administradores no se editan desde este endpoint");
        }
        usuario = usuarioService.updateUsuario(
                usuario, request.getNombre(), request.getApellido(), request.getTelefono());

        boolean teniaPerfilDeCliente = usuario.getClienteProfile() != null;
        usuario.asignarRol(request.getRol()); // deja solo el perfil del rol nuevo (cascada con la cuenta)
        ClienteProfile perfil = usuario.getClienteProfile();
        if (perfil != null && teniaPerfilDeCliente) {
            // el personal corrige la categoría sin el límite de cambios que tiene el cliente
            perfil.setCategoria(request.getCategoria());
            perfil.setPosicion(request.getPosicion());
        } else if (perfil != null) {
            clienteProfileService.cargarDatosDeJugador(usuario, request.getCategoria(), request.getPosicion());
        }
        usuarioRepository.save(usuario);

        return usuarioMapper.aAdminResponse(usuario, perfil);
    }

    public List<UsuarioAdminResponse> listarUsuarios(FiltroUsuarioRequest filtros) {

        if ((filtros.getCategoria() != null || filtros.getPosicion() != null) && filtros.getRol() != Role.CLIENTE) {
            throw new IllegalArgumentException("El rol debe ser CLIENTE para asignar categoría o posición");
        }

        return usuarioRepository.findAll(UsuarioSpecification.conFiltros(filtros))
                .stream()
                .map(this::buildUsuarioAdminResponse)
                .collect(Collectors.toList());
    }

    public UsuarioAdminResponse getUsuario(Long id) {
        Usuario usuario = usuarioService.findByIdOrThrow(id);
        return buildUsuarioAdminResponse(usuario);
    }

    @Transactional
    public UsuarioAdminResponse reactivarUsuario(Long id) {
        Usuario usuario = usuarioService.findByIdOrThrow(id);
        // mientras estuvo desactivada, otra cuenta pudo verificar el mismo celular: se queda con él
        if (Boolean.TRUE.equals(usuario.getTelefonoVerificado())
                && usuarioService.telefonoVerificadoEnOtraCuenta(usuario.getTelefono(), usuario.getId())) {
            usuario.setTelefonoVerificado(false);
        }
        usuario.setEnabled(true);
        usuarioRepository.save(usuario);
        return buildUsuarioAdminResponse(usuario);
    }

    @Transactional
    public UsuarioAdminResponse desactivarUsuario(Long id) {
        Usuario usuario = usuarioService.findByIdOrThrow(id);
        if (usuario.getRol() == Role.ADMIN) {
            throw new IllegalArgumentException("No se puede desactivar a un administrador");
        }
        usuario.setEnabled(false);
        refreshTokenService.borrarTodos(usuario);
        usuarioRepository.save(usuario);
        return buildUsuarioAdminResponse(usuario);
    }

    // ── Búsqueda de clientes (ADMIN y EMPLEADO) ──────────────────────────

    /** Solo usuarios CLIENTE, por nombre, apellido o email (mínimo 2 caracteres, hasta 20 resultados). */
    @Transactional(readOnly = true)
    public List<ClienteBusquedaResponse> buscarClientes(String buscar, boolean soloActivos) {
        String texto = Busquedas.normalizar(buscar);
        if (texto == null || texto.length() < MINIMO_CARACTERES_BUSQUEDA) {
            throw new ReservaException("Escribí al menos " + MINIMO_CARACTERES_BUSQUEDA + " caracteres para buscar.");
        }
        Specification<Usuario> spec = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(root.get("rol"), Role.CLIENTE));
            if (soloActivos) {
                condiciones.add(cb.isTrue(root.get("enabled")));
            }
            condiciones.add(Busquedas.nombreApellidoOEmail(cb, root, texto));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        PageRequest primeros = PageRequest.of(0, MAXIMO_RESULTADOS_BUSQUEDA,
                Sort.by("apellido", "nombre", "id"));
        return usuarioRepository.findAll(spec, primeros).stream().map(usuarioMapper::aBusquedaResponse).toList();
    }

    /** La respuesta con el perfil de jugador, si es CLIENTE. */
    private UsuarioAdminResponse buildUsuarioAdminResponse(Usuario usuario) {
        ClienteProfile perfil = usuario.getRol() == Role.CLIENTE ? clienteProfileService.findByUsuario(usuario) : null;
        return usuarioMapper.aAdminResponse(usuario, perfil);
    }

    private String generarPasswordTemporal() {
        String minusculas = "abcdefghijklmnopqrstuvwxyz";
        String mayusculas = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String numeros = "0123456789";
        String especiales = "!@#$%^&*";

        Random random = new SecureRandom();

        // Garantizás al menos uno de cada tipo
        StringBuilder password = new StringBuilder();
        password.append(minusculas.charAt(random.nextInt(minusculas.length())));
        password.append(mayusculas.charAt(random.nextInt(mayusculas.length())));
        password.append(numeros.charAt(random.nextInt(numeros.length())));
        password.append(especiales.charAt(random.nextInt(especiales.length())));

        // Completás hasta 12 caracteres con caracteres aleatorios
        String todos = minusculas + mayusculas + numeros + especiales;
        for (int i = 4; i < 12; i++) {
            password.append(todos.charAt(random.nextInt(todos.length())));
        }

        // Mezclás para que no siempre empiece igual
        List<Character> chars = new ArrayList<>();
        for (char c : password.toString().toCharArray()) {
            chars.add(c);
        }
        Collections.shuffle(chars, random);

        StringBuilder result = new StringBuilder();
        for (char c : chars) {
            result.append(c);
        }
        return result.toString();
    }
}