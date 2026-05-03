package ru.practicum.moviehub.http;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {

    private static MoviesServer server;
    private static HttpClient client;
    private static final String BASE = "http://localhost:8080";
    private MoviesStore moviesStore = new MoviesStore();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @BeforeEach
    void cleanStore() {
        // Очищаем хранилище перед каждым тестом
        moviesStore.getAllMovies().forEach(movie -> moviesStore.deleteMovie(movie.getId()));
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        // Создаём и запускаем сервер
        server = new MoviesServer(new MoviesStore(), 8080);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        System.out.println("Сервер запущен");

    }


    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
            System.out.println("Сервер остановлен");
        }
    }


    // ==================== GET /movies ====================

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""),
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"), "Ожидается JSON-массив");


        assertTrue(moviesStore.getMapMovie().isEmpty(), "Ожидается пустой список фильмов");
    }

    @Test
    void getMovies_whenMoviesExist_returnsListOfMovies() throws Exception {
        // Добавляем тестовые фильмы
        Movie movie1 = moviesStore.addNewMovie("Movie 1", 2010);
        Movie movie2 = moviesStore.addNewMovie("Movie 2", 2014);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());


        assertEquals(2, moviesStore.getMapMovie().size(), "Должно быть 2 фильма");
        assertEquals("Movie 1", moviesStore.getMapMovie().get(1).getTitle());
        assertEquals(2010, moviesStore.getMapMovie().get(1).getYear());
        assertEquals("Movie 2", moviesStore.getMapMovie().get(2).getTitle());
        assertEquals(2014, moviesStore.getMapMovie().get(2).getYear());
    }

    // ==================== POST /movies ====================

    @Test
    void postMovie_withValidData_createsMovie() throws Exception {
        Movie newMovie = new Movie(0, "Movie 1", 1999);
        String jsonBody = gson.toJson(newMovie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode(), "При успешном создании должен вернуться 201");

        Movie createdMovie = gson.fromJson(resp.body(), Movie.class);
        assertNotNull(createdMovie.getId(), "ID не должен быть null");
        assertTrue(createdMovie.getId() > 0, "ID должен быть положительным");
        assertEquals("Movie 1", createdMovie.getTitle());
        assertEquals(1999, createdMovie.getYear());
    }

    @Test
    void postMovie_withEmptyTitle_returnsError() throws Exception {
        String jsonBody = "{\"id\":0,\"title\":\"\",\"releaseYear\":2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "При пустом названии должен вернуться 422");
        assertTrue(resp.body().contains("Название не может быть пустым") ||
                resp.body().contains("Ошибка валидации"));
    }

    @Test
    void postMovie_withTitleTooLong_returnsError() throws Exception {
        String longTitle = "A".repeat(101);
        String jsonBody = "{\"id\":0,\"title\":\"" + longTitle + "\",\"releaseYear\":2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "При слишком длинном названии должен вернуться 422");
    }

    @Test
    void postMovie_withYearLessThan1888_returnsError() throws Exception {
        String jsonBody = "{\"id\":0,\"title\":\"Old Movie\",\"releaseYear\":1887}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "При годе меньше 1888 должен вернуться 422");
        assertTrue(resp.body().contains("Некорректный год") ||
                resp.body().contains("Ошибка валидации"));
    }

    @Test
    void postMovie_withYearGreaterThanCurrentPlusOne_returnsError() throws Exception {
        int futureYear = LocalDate.now().getYear() + 2;
        String jsonBody = "{\"id\":0,\"title\":\"Old Movie\",\"releaseYear\":" + futureYear + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "При годе больше текущего+1 должен вернуться 422");
    }

    @Test
    void postMovie_withWrongContentType_returnsError() throws Exception {
        Movie newMovie = new Movie(0, "Test Movie", 2020);
        String jsonBody = gson.toJson(newMovie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "text/plain")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "При неправильном Content-Type должен вернуться 415");
    }

    @Test
    void postMovie_withInvalidJson_returnsError() throws Exception {
        String invalidJson = "{\"title\":\"Test\", \"year\":"; // Некорректный JSON

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Должна вернуться ошибка 400 или 500
        assertTrue(resp.statusCode() == 400 || resp.statusCode() == 500,
                "При некорректном JSON должна вернуться ошибка");
    }

    // ==================== GET /movies/{id} ====================

    @Test
    void getMovieById_withExistingId_returnsMovie() throws Exception {
        Movie addedMovie = moviesStore.addNewMovie("Pulp Fiction", 1994);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + addedMovie.getId()))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        Movie retrievedMovie = gson.fromJson(resp.body(), Movie.class);
        assertEquals(addedMovie.getId(), retrievedMovie.getId());
        assertEquals("Pulp Fiction", retrievedMovie.getTitle());
        assertEquals(1994, retrievedMovie.getYear());
    }

    @Test
    void getMovieById_withNonExistentId_returnsError() throws Exception {
        int nonExistentId = 99999;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + nonExistentId))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode(), "При несуществующем ID должен вернуться 404");
    }

    @Test
    void getMovieById_withInvalidId_returnsError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "При некорректном ID должен вернуться 400");
    }

    // ==================== DELETE /movies/{id} ====================

    @Test
    void deleteMovie_withExistingId_deletesMovie() throws Exception {
        Movie addedMovie = moviesStore.addNewMovie("Fight Club", 1999);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + addedMovie.getId()))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, resp.statusCode(), "При успешном удалении должен вернуться 204");

        // Проверяем, что фильм действительно удален
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + addedMovie.getId()))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(404, getResp.statusCode(), "После удаления фильм не должен находиться");
    }

    @Test
    void deleteMovie_withNonExistentId_returnsError() throws Exception {
        int nonExistentId = 99999;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + nonExistentId))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode(), "При удалении несуществующего фильма должен вернуться 404");
    }

    @Test
    void deleteMovie_withInvalidId_returnsError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/xyz"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "При некорректном ID должен вернуться 400");
    }

    // ==================== GET /movies?year=YYYY ====================

    @Test
    void getMoviesByYear_withExistingYear_returnsMovies() throws Exception {
        moviesStore.addNewMovie("Movie 2010", 2010);
        moviesStore.addNewMovie("Movie 2015", 2015);
        moviesStore.addNewMovie("Another 2010", 2010);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2010"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        Movie[] moviesArray = gson.fromJson(resp.body(), Movie[].class);
        List<Movie> movies = Arrays.asList(moviesArray);

        assertEquals(2, movies.size(), "Должно быть 2 фильма 2010 года");
        for (Movie movie : movies) {
            assertEquals(2010, movie.getYear());
        }
    }

    @Test
    void getMoviesByYear_withNonExistentYear_returnsEmptyList() throws Exception {
        moviesStore.addNewMovie("Movie 2020", 2020);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=1990"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        Movie[] moviesArray = gson.fromJson(resp.body(), Movie[].class);
        List<Movie> movies = Arrays.asList(moviesArray);

        assertTrue(movies.isEmpty(), "Должен вернуться пустой список");
    }

    @Test
    void getMoviesByYear_withInvalidYearParameter_returnsError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=notANumber"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "При некорректном параметре year должен вернуться 400");
    }

    @Test
    void getMoviesByYear_withYearLessThan1888_returnsError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=1800"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "При годе меньше 1888 должен вернуться 400");
    }

    @Test
    void getMoviesByYear_withYearGreaterThanCurrentPlusOne_returnsError() throws Exception {
        int futureYear = LocalDate.now().getYear() + 2;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + futureYear))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "При годе больше текущего+1 должен вернуться 400");
    }
}