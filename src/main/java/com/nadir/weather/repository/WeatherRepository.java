package com.nadir.weather.repository;

import com.nadir.weather.model.WeatherEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeatherRepository extends JpaRepository<WeatherEntity, String > {
    Optional<WeatherEntity> findFirstByRequestedCityNameOrderByUpdateTimeDesc(String city);
}
