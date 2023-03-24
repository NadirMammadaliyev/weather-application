package com.nadir.weather.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadir.weather.dto.WeatherDto;
import com.nadir.weather.dto.WeatherResponse;
import com.nadir.weather.exception.ClientException;
import com.nadir.weather.model.WeatherEntity;
import com.nadir.weather.repository.WeatherRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static com.nadir.weather.constants.Constants.*;

@Service
@CacheConfig(cacheNames = {"weather"})
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);

    private final WeatherRepository weatherRepository;
    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherService(WeatherRepository weatherRepository, RestTemplate restTemplate) {
        this.weatherRepository = weatherRepository;
        this.restTemplate = restTemplate;
    }


    @Cacheable(key = "#city")
    public WeatherDto getWeatherByCityName(String city) {
        logger.info("getWeatherByCityName.start Requested city: " + city);
        Optional<WeatherEntity> weatherEntityOptional = weatherRepository.findFirstByRequestedCityNameOrderByUpdateTimeDesc(city);

        return weatherEntityOptional.map(weather -> {
            if (weather.getUpdateTime().isBefore(LocalDateTime.now().minusMinutes(30))) {
               return WeatherDto.convert(getWeatherFromWeatherStack(city));
            }
            logger.info(String.format("getWeatherByCityName Getting weather from database for %s due to it is already up-to-date", city));
            return WeatherDto.convert(weather);
        }).orElseGet(() -> WeatherDto.convert(getWeatherFromWeatherStack(city)));
    }

    @CacheEvict(allEntries = true)
    @PostConstruct
    @Scheduled(fixedRateString = "10000")
    public void clearCache(){
        logger.info("clearCache.start Caches are cleared");
    }
    private WeatherEntity getWeatherFromWeatherStack(String city) {
        try {
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(getWeatherStackUrl(city), String.class);
            WeatherResponse weatherResponse = objectMapper.readValue(responseEntity.getBody(), WeatherResponse.class);
            logger.info("getWeatherFromWeatherStack weatherResponse: " + weatherResponse);
            return saveWeatherEntity(city, weatherResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }catch (ClientException e){
            throw new ClientException();
        }
    }

    private WeatherEntity saveWeatherEntity(String city, WeatherResponse weatherResponse) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        WeatherEntity weatherEntity = new WeatherEntity(city,
                weatherResponse.location().name(),
                weatherResponse.location().country(),
                weatherResponse.current().temperature(),
                LocalDateTime.now(),
                LocalDateTime.parse(weatherResponse.location().localTime(), dateTimeFormatter));

        return weatherRepository.save(weatherEntity);
    }

    private String getWeatherStackUrl(String city) {
        return API_URL + ACCESS_KEY_PARAM + API_KEY + QUERY_KEY_PARAM + city;
    }
}
