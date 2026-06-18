package com.LosCiruelos.padel_club_api.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.LosCiruelos.padel_club_api.Entities.Cancha;

public interface CanchaRepository extends JpaRepository<Cancha, Integer>{
    
}
