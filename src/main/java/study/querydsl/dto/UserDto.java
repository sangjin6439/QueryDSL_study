package study.querydsl.dto;

import lombok.Data;

@Data
public class UserDto {

    private String name;
    private int age;

    public UserDto() {
    }

    public UserDto(final String name, final int age) {
        this.name = name;
        this.age = age;
    }
}
