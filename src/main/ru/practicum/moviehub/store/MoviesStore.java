package ru.practicum.moviehub.store;

import ru.practicum.moviehub.Exception.MovieNotFoundException;
import ru.practicum.moviehub.model.Movie;


import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class MoviesStore {


    private static final HashMap<Integer, Movie> mapMovie = new HashMap<>();
    private static int nextId = 1;


    //Добавление Фильма
    public static Movie addNewMovie(String title, int year) {

        Movie newMovie = new Movie(findNextAvailableId(), title, year);
        mapMovie.put(findNextAvailableId(), newMovie);
        System.out.println("Фильм " + title + " добавлен");
        return newMovie;

    }

    private static int findNextAvailableId() {
        return IntStream.rangeClosed(1, nextId - 1)
                .filter(id -> !mapMovie.containsKey(id))
                .findFirst()
                .orElse(nextId++);
    }


    public static Movie searchMovie(int id) {
        if (mapMovie.isEmpty()) {
            System.out.println("Список пуст");
            throw new MovieNotFoundException("Список фильмов пустой");
        }
        if (!mapMovie.containsKey(id)) {
            System.out.println("Фильм с " + id + " не найден");
            throw new MovieNotFoundException("По " + id + " фильма не существует");
        }


        System.out.println("Фильм " + mapMovie.get(id) + " найден");
        return mapMovie.get(id);
    }

    public boolean deleteMovie(int id) {
        System.out.println("Фильм " + mapMovie.get(id) + " удален");
        return mapMovie.remove(id) != null;
    }

    public static List<Movie> getSortedByYear(int year) {

        List<Movie> filMap = mapMovie.values().stream()
                .filter(movie -> movie.getYear() == year)
                .collect(Collectors.toList());
        System.out.println("Фильмы отсортированы");
        System.out.println("Количество фильмов в отсортированном списке: " + filMap.size());
        return filMap;
    }

    public static List<Movie> getAllMovies() {
        return mapMovie.values().stream()
                .collect(Collectors.toList());
    }

    public static HashMap<Integer, Movie> getMapMovie() {
        return mapMovie;
    }
}