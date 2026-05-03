package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.Exception.MovieNotFoundException;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final MoviesStore moviesStore;

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        try {
            if (method.equals("GET")) {
                handleGetRequest(ex, path);
            } else if (method.equals("POST")) {
                handlePostRequest(ex);
            } else if (method.equals("DELETE")) {
                handleDeleteRequest(ex, path);
            } else {
                ex.sendResponseHeaders(405, -1);
                ex.close();
            }
        } catch (Exception e) {
            sendErrorResponse(ex, 500, "Внутренняя ошибка сервера");
        }
    }


    private void handleGetRequest(HttpExchange ex, String path) throws IOException {
        // Проверяем, есть ли ID в пути
        if (path.matches("/movies/.+")) {
            // GET /movies/{id}

            try {
                int id = extractIdFromPath(path);
                Movie movie = moviesStore.searchMovie(id);
                String jsonResponse = gson.toJson(movie);
                sendJson(ex, 200, jsonResponse);
            } catch (IllegalArgumentException e) {
                List<String> details = List.of(e.getMessage());
                ErrorResponse errorResponse = new ErrorResponse("Ошибка поиска фильма", details);
                sendErrorResponse(ex, 400, e.getMessage());
            } catch (MovieNotFoundException e) {
                List<String> details = List.of(e.getMessage());
                ErrorResponse errorResponse = new ErrorResponse("Ошибка id", details);
                sendErrorResponse(ex, 404, e.getMessage());
            }

        } else if (path.equals("/movies")) {
            // GET /movies или GET /movies?year=YYYY
            String query = ex.getRequestURI().getQuery();

            if (query != null && query.startsWith("year=")) {
                // Фильтрация по году
                try {
                    String yearStr = query.substring(5);
                    int year = Integer.parseInt(yearStr);
                    if (year < 1888 || year > java.time.LocalDate.now().getYear() + 1) {
                        sendErrorResponse(ex, 400, "Некорректный параметр запроса — 'year'");
                        return;
                    }
                    List<Movie> movies = moviesStore.getSortedByYear(year);
                    String jsonResponse = gson.toJson(movies);
                    sendJson(ex, 200, jsonResponse);
                } catch (NumberFormatException e) {
                    sendErrorResponse(ex, 400, "Некорректный параметр запроса — 'year'");
                }
            } else if (query == null) {
                // Получение всех фильмов
                List<Movie> movies = moviesStore.getAllMovies();
                String jsonResponse = gson.toJson(movies);
                sendJson(ex, 200, jsonResponse);
            } else {
                sendErrorResponse(ex, 400, "Некорректный параметр запроса");
            }
        } else {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        }
    }

    private void handlePostRequest(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendErrorResponse(ex, 415, "Unsupported Media Type");
            return;
        }

        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Movie newMovie = gson.fromJson(body, Movie.class); // Десериализация в Movie

            // Добавление фильма
            Movie createdMovie = moviesStore.addNewMovie(newMovie.getTitle(), newMovie.getYear());
            sendJson(ex, 201, gson.toJson(createdMovie));
        } catch (IllegalArgumentException e) {
            // Формируем детализированный ответ об ошибке валидации
            List<String> details = List.of(e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse("Ошибка валидации", details);
            sendJson(ex, 422, gson.toJson(errorResponse));
        } catch (Exception e) {
            sendErrorResponse(ex, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleDeleteRequest(HttpExchange ex, String path) throws IOException {
        try {
            int id = extractIdFromPath(path);
            boolean deleted = moviesStore.deleteMovie(id);
            if (deleted) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
            } else {
                sendErrorResponse(ex, 404, "Фильм не найден");
            }
        } catch (IllegalArgumentException e) {
            sendErrorResponse(ex, 400, e.getMessage());
        }
    }

    private int extractIdFromPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Путь не может быть пустым или null");
        }

        String[] parts = path.split("/");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Некорректный формат пути");
        }

        String idStr = parts[parts.length - 1];
        if (idStr.isEmpty()) {
            throw new IllegalArgumentException("ID не может быть пустой строкой");
        }

        // Проверка, что строка состоит только из цифр
        for (int i = 0; i < idStr.length(); i++) {
            if (!Character.isDigit(idStr.charAt(i))) {
                throw new IllegalArgumentException("Некорректный ID: '" + idStr + "' должен состоять только из цифр");
            }
        }

        try {
            int id = Integer.parseInt(idStr);
            if (id <= 0) {
                throw new IllegalArgumentException("ID должен быть положительным целым числом");
            }
            return id;
        } catch (NumberFormatException e) {
            // Этот блок теперь не должен достигаться, но оставлен для безопасности
            throw new IllegalArgumentException("Некорректный ID: '" + idStr + "' не является числом");
        }
    }
}