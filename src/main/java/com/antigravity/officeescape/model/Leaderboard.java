package com.antigravity.officeescape.model;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "leaderboard")
@Data
@NoArgsConstructor
public class Leaderboard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String playerName;
    private int score; // Floor count

    private LocalDateTime createdAt = LocalDateTime.now();

    public Leaderboard(String playerName, int score) {
        this.playerName = playerName;
        this.score = score;
    }
}
