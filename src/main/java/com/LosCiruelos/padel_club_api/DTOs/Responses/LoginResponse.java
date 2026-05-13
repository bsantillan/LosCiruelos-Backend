package com.LosCiruelos.padel_club_api.DTOs.Responses;

import lombok.Data;

@Data
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
}
