package ru.practicum.moviehub.model;

import java.time.LocalDate;
import java.util.Objects;

public class Movie {
    private int id;
    private String title;
    private int year;

    // Конструктор с ID — для создания фильма с присвоенным ID
    public Movie(int id, String title, int year) {
        this.id = id;
        setTitle(title);
        setYear(year);
    }


    public void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Название не может быть пустым");
        }
        if (title.length() >= 100) {
            throw new IllegalArgumentException("Название не может быть более 100 символов");
        }

        this.title = title;
    }

    public void setYear(int year) {
        if (year < 1888 || year > LocalDate.now().getYear()) {
            throw new IllegalArgumentException("Некорректный год выпуска");
        }
        this.year = year;
    }

    public String getTitle() {
        return title;
    }

    public int getYear() {
        return year;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Movie movie = (Movie) o;
        return id == movie.id && year == movie.year && Objects.equals(title, movie.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, year);
    }

    @Override
    public String toString() {
        return "Movie{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", year=" + year +
                '}';
    }
}