package com.LosCiruelos.padel_club_api.DTOs.Responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GoogleUser {

    private String email;
    private String firstName;
    private String lastName;
    
}
