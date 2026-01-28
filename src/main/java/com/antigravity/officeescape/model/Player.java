package com.antigravity.officeescape.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Player {
    private String sessionId;
    private String name;
    private double x;
    private double y;
    private double vx;
    private double vy;
    private int hp;
    private int floor;
    private boolean isReady;
    private boolean isDead;
    private boolean movingLeft;
    private boolean movingRight;

    private double width;
    private double height;
    private long effectEndTime;

    public Player(String sessionId, String name) {
        this.sessionId = sessionId;
        this.name = name;
        this.hp = 10; // Default HP
        this.floor = 0;
        this.isReady = false;
        this.isDead = false;
        this.x = 200; // Default spawn
        this.y = 100;
        this.width = 30;
        this.height = 30;
    }
}
