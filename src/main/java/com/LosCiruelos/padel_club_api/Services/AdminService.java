package com.LosCiruelos.padel_club_api.Services;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import java.util.Collections;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.DTOs.Requests.CrearUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.EditarUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.FiltroUsuarioRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.UsuarioAdminResponse;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.EmailEnUsoException;
import com.LosCiruelos.padel_club_api.Security.JWTUtil;
import com.LosCiruelos.padel_club_api.Specifications.UsuarioSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ClienteProfileService clienteProfileService;
    private final UsuarioService usuarioService;
    private final JWTUtil jwtUtil;
    private final VerificationService verificationService;

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
                request.getRol(), AuthProvider.LOCAL, true, false, false);

        if (request.getRol() == Role.CLIENTE) {
            clienteProfileService.crearClienteProfile(
                    usuario, request.getCategoria(), request.getPosicion());
        }

        try {
            verificationService.enviarBienvenida(usuario.getEmail(), passwordTemporal);
        } catch (Exception e) {
            log.error("No se pudo enviar email a {}: {}", usuario.getEmail(), e.getMessage());
        }

        return buildUsuarioAdminResponse(usuario);
    }

    public UsuarioAdminResponse editarUsuario(Long id, EditarUsuarioAdminRequest request) {

        if ((request.getCategoria() != null || request.getPosicion() != null) && request.getRol() != Role.CLIENTE) {
            throw new IllegalArgumentException("El rol debe ser CLIENTE para asignar categoría o posición");
        }

        Usuario usuario = usuarioService.findByIdOrThrow(id);
        Role rolAnterior = usuario.getRol();

        usuario = usuarioService.updateUsuario(
                usuario, request.getNombre(), request.getApellido(), request.getTelefono());

        usuario.setRol(request.getRol());
        usuarioService.save(usuario);

        ClienteProfile perfil = null;

        if (rolAnterior == Role.CLIENTE && request.getRol() != Role.CLIENTE) {
            usuario.setClienteProfile(null);
            usuarioService.save(usuario);
        } else if (request.getRol() == Role.CLIENTE) {
            perfil = clienteProfileService.findByUsuario(usuario);

            if (request.getCategoria() == null || request.getPosicion() == null) {
                throw new IllegalArgumentException("La categoría y posición son obligatorias para clientes");
            }

            if (perfil == null) {
                perfil = clienteProfileService.crearClienteProfile(
                        usuario, request.getCategoria(), request.getPosicion());
            } else {
                perfil = clienteProfileService.updateClienteProfile(
                        perfil, request.getCategoria(), request.getPosicion(), perfil.getCategoriaActualizadaAt());
            }
        }

        return buildUsuarioAdminResponse(usuario, perfil);
    }

    public List<UsuarioAdminResponse> listarUsuarios(FiltroUsuarioRequest filtros) {

        if ((filtros.getCategoria() != null || filtros.getPosicion() != null) && filtros.getRol() != Role.CLIENTE) {
            throw new IllegalArgumentException("El rol debe ser CLIENTE para asignar categoría o posición");
        }

        return usuarioService.findAll(UsuarioSpecification.conFiltros(filtros))
                .stream()
                .map(this::buildUsuarioAdminResponse)
                .collect(Collectors.toList());
    }

    public UsuarioAdminResponse getUsuario(Long id) {
        Usuario usuario = usuarioService.findByIdOrThrow(id);
        return buildUsuarioAdminResponse(usuario);
    }

    public UsuarioAdminResponse reactivarUsuario(Long id) {
        Usuario usuario = usuarioService.findByIdOrThrow(id);
        usuario.setEnabled(true);
        usuarioService.save(usuario);
        return buildUsuarioAdminResponse(usuario);
    }

    public UsuarioAdminResponse desactivarUsuario(Long id) {
        Usuario usuario = usuarioService.findByIdOrThrow(id);
        usuario.setEnabled(false);
        jwtUtil.deleteAllRefreshTokens(usuario);
        usuarioService.save(usuario);
        return buildUsuarioAdminResponse(usuario);
    }

    private UsuarioAdminResponse buildUsuarioAdminResponse(Usuario usuario, ClienteProfile perfil) {
        UsuarioAdminResponse response = new UsuarioAdminResponse();
        response.setId(usuario.getId());
        response.setNombre(usuario.getNombre());
        response.setApellido(usuario.getApellido());
        response.setEmail(usuario.getEmail());
        response.setTelefono(usuario.getTelefono());
        response.setRol(usuario.getRol());
        response.setEnabled(usuario.getEnabled());
        response.setEmailVerificado(usuario.getEmailVerificado());
        response.setProvider(usuario.getProvider());

        if (perfil != null) {
            response.setCategoria(perfil.getCategoria());
            response.setPosicion(perfil.getPosicion());
        }

        return response;
    }

    private UsuarioAdminResponse buildUsuarioAdminResponse(Usuario usuario) {
        ClienteProfile perfil = null;
        if (usuario.getRol() == Role.CLIENTE) {
            perfil = clienteProfileService.findByUsuario(usuario);
        }
        return buildUsuarioAdminResponse(usuario, perfil);
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