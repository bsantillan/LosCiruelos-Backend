package com.LosCiruelos.padel_club_api;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.LosCiruelos.padel_club_api.common.Fechas;

@SpringBootApplication
@EnableScheduling 
public class PadelClubApiApplication {

	public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(Fechas.ZONA));

		SpringApplication.run(PadelClubApiApplication.class, args);
	}

}
